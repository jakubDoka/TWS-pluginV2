package theWorst.database;

import arc.Events;
import arc.graphics.Color;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Timer;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import mindustry.entities.traits.BuilderTrait;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.net.Administration;
import mindustry.type.ItemStack;
import mindustry.world.blocks.storage.CoreBlock;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.Document;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import theWorst.Bot;
import theWorst.Global;
import theWorst.helpers.gameChangers.Pet;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import static mindustry.Vars.*;
import static theWorst.Tools.Commands.logInfo;
import static theWorst.Tools.Formatting.*;
import static theWorst.Tools.General.enumContains;
import static theWorst.Tools.General.getCore;
import static theWorst.Tools.Json.*;
import static theWorst.Tools.Players.*;

public class Database {
    public static final String playerCollection = "playerD";
    public static final String AFK = "[gray]<AFK>[]";

    static final String petFile = Global.configDir + "pets.json";
    static final String subnetFile = Global.saveDir + "subnetBuns.json";
    public static MongoClient client = MongoClients.create();
    static MongoDatabase database = client.getDatabase(Global.config.dbName);
    static MongoCollection<Document> rawData = database.getCollection(playerCollection);
    public static HashMap<String,PD> online = new HashMap<>();
    public static HashMap<String, Pet> pets=new HashMap<>();
    static HashSet<String> subnet = new HashSet<>();
    public static DataHandler data = new DataHandler(rawData);

    public Database(){
        loadSubnet();
        Events.on(EventType.ServerLoadEvent.class, e->{
            loadPets();
        });

        new AfkMaker();
        //todo test
        Events.on(EventType.PlayerConnect.class,e->{
            //remove fake ranks
            logPlayer(e.player, true);

        });

        Events.on(EventType.PlayerLeave.class,e->{
            PD pd = online.remove(e.player.uuid);
            if(pd == null) return;

            sendMessage("player-disconnected",e.player.name,String.valueOf(pd.id));
            Bot.sendToLinkedChat(String.format("**%s** (ID:**%d**) hes disconnected.", cleanColors(e.player.name), pd.id));
            data.free(pd);
        });

        //games played and games won counter
        Events.on(EventType.GameOverEvent.class, e ->{
            for(Player p:playerGroup){
                if(p.getTeam()==e.winner) {
                    data.incOne(p, Stat.gamesWon);
                }
                data.incOne(p, Stat.gamesPlayed);
            }
        });

        //build and destruct permissions handling
        Events.on(EventType.BuildSelectEvent.class, e-> {
            if (!(e.builder instanceof Player)) return;
            Player player = (Player) e.builder;
            PD pd = online.get(player.uuid);
            CoreBlock.CoreEntity core = getCore();
            if (core == null) return;
            BuilderTrait.BuildRequest request = player.buildRequest();
            if (request == null) return;
            if (pd.hasThisPerm(Perm.destruct) && request.breaking) {
                for (ItemStack s : request.block.requirements) {
                    core.items.add(s.item, s.amount / 2);
                }
                Call.onDeconstructFinish(request.tile(), request.block, ((Player) e.builder).id);
            } else if (pd.hasThisPerm(Perm.build) && !request.breaking && request.block.buildCost > 30) {
                if (core.items.has(request.block.requirements)) {
                    for (ItemStack s : request.block.requirements) {
                        core.items.remove(s);
                    }
                    Call.onConstructFinish(e.tile, request.block, ((Player) e.builder).id,
                            (byte) request.rotation, player.getTeam(), false);
                    e.tile.configureAny(request.config);
                }
            } else return;
            //necessary because instant build or break do not trigger event
            Events.fire(new EventType.BlockBuildEndEvent(e.tile, player, e.team, e.breaking));
        });

        //count units killed and deaths
        Events.on(EventType.UnitDestroyEvent.class, e->{
            if(e.unit instanceof Player){
                data.incOne((Player) e.unit, Stat.deaths);
            }else if(e.unit.getTeam() != Team.sharded){
                for(Player p:playerGroup){
                    data.incOne(p, Stat.enemiesKilled);
                }
            }
        });

        //handle hammer event
        Events.on(EventType.PlayerIpBanEvent.class,e->{
           data.setRank(e.ip, Ranks.griefer, RankType.rank);
           subnet.add(getSubnet(e.ip));
           netServer.admins.unbanPlayerIP(e.ip);
        });

        //count buildings and destroys
        Events.on(EventType.BlockBuildEndEvent.class, e->{
            if(e.player == null) return;
            if(!e.breaking && e.tile.block().buildCost/60<1) return;
            if(e.breaking){
                data.incOne(e.player, Stat.buildingsBroken);
            }else {
                data.incOne(e.player, Stat.buildingsBuilt);
            }
        });

    }

    public static void logPlayer(Player player, boolean firstTime) {
        String originalName = player.name;
        player.name = cleanName(player.name);
        if(!originalName.equals(player.name)){
            //name cannot be blank
            if(player.name.replace(" ","").isEmpty()){
                player.name = player.id +"&#@";
            }
            //let player know
            if(firstTime) sendErrMessage(player,"name-modified");
        }
        PD pd = data.LoadData(player);
        DataHandler.Doc doc = data.getDoc(player);
        online.put(player.uuid, pd);
        //marked subnet so mark player aromatically
        if(subnet.contains(getSubnet(player.con.address)) && pd.hasPermLevel(Perm.normal)){
            Bot.onRankChange(pd.name, pd.id, pd.rank.name , Ranks.griefer.name, "Server", "Subnet ban.");
            setRank(pd.id, Ranks.griefer);
        }
        //resolving special rank
        new Thread(()->{
            Rank sr = null;
            for(Rank rank : Ranks.special.values()){
                if(rank.condition(doc,pd)){
                    if (sr == null || sr.value < rank.value) {
                        synchronized (pd) {
                            pd.sRank = rank;
                        }
                        sr = rank;
                        pd.addPerms(rank);
                    }
                    addPets(pd, rank);
                }
            }
        });

        pd.updateName();

        Runnable conMess = () ->{ if (firstTime) sendMessage("player-connected",player.name,String.valueOf(pd.id)); };

        Bot.sendToLinkedChat(String.format("**%s** (ID:**%d**) hes connected.", player.name, pd.id));
        if (Bot.api == null || Bot.config.serverId == null || pd.rank == Ranks.griefer) return;
        if (Bot.pendingLinks.containsKey(pd.id)){
            sendMessage(player,"discord-pending-link",Bot.pendingLinks.get(pd.id).name);
        }
        String link = doc.getLink();
        if (link == null) {
            conMess.run();
            return;
        }
        CompletableFuture<User> optionalUser = Bot.api.getUserById(link);
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                if (!optionalUser.isDone()) return;
                this.cancel();
                try {
                    User user = optionalUser.get();
                    if (user == null) {
                        conMess.run();
                        return;
                    }
                    Optional<Server> server = Bot.api.getServerById(Bot.config.serverId);
                    if (!server.isPresent()) {
                        conMess.run();
                        return;
                    }
                    Rank current = pd.rank;
                    Rank crDl = null;
                    for (Role r : user.getRoles(server.get())) {
                        String roleName = r.getName();
                        Rank dl = Ranks.donation.get(roleName);
                        Rank rk = Ranks.buildIn.get(roleName);
                        if (rk != null) {
                            if (pd.rank.value < rk.value) {
                                current = rk;
                            }
                            synchronized (pd) {
                                pd.obtained.add(rk);
                            }
                            pd.addPerms(rk);
                            addPets(pd, rk);
                        }else if (dl != null ) {
                            if (crDl == null || dl.value > crDl.value){
                                crDl = dl;
                            }
                            synchronized (pd) {
                                pd.obtained.add(dl);
                            }
                            pd.addPerms(dl);
                            addPets(pd, dl);
                        }
                    }
                    synchronized (pd){
                        setRank(pd.id, current);
                        pd.dRank = crDl;
                        pd.updateName();
                    }
                    conMess.run();
                } catch (InterruptedException | ExecutionException interruptedException) {
                    interruptedException.printStackTrace();
                }
            }
        }, 0, .1f);
    }

    public static boolean hasDisabled(Player player, Perm p) {
        return data.contains(player, "settings", p.name());
    }

    static void fixName(Player player) {

    }


    static void addPets(PD pd, Rank sr){
        if(!hasEnabled(pd.player, Setting.pets)) return;
        if(sr != null && sr.pets != null){
            for(String pet : sr.pets){
                Pet found = pets.get(pet);
                if(found == null){
                    Log.info("missing pet :" + pet);
                } else {
                    synchronized (pd.pets) {
                        pd.pets.add(new Pet(found));
                    }
                }
            }
        }
    }

    public static PD getData(Player player) {
        return online.get(player.uuid);
    }

    public static boolean hasEnabled(Player player, Setting setting) {
        return data.contains(player, "settings", setting.name());
    }

    public static boolean hasMuted(Player player, Player other){
        return data.contains(player, "mutes", other.uuid);
    }

    //just for testing purposes
    public static void clean(){
        rawData.drop();
        rawData = MongoClients.create().getDatabase(Global.config.dbName).getCollection(playerCollection);
        data = new DataHandler(rawData);
    }

    public static DataHandler.Doc findData(String target) {
        DataHandler.Doc res = null;
        if(Strings.canParsePostiveInt(target)) {
            res = data.getDoc(Long.parseLong(target));
        }
        if (res == null) {
            for(PD pd : online.values()){
                if(pd.name.equals(target)){
                    return data.getDoc(pd.player);
                }
            }
        }
        return res;

    }



    public static void setRank(long id, Rank rank){
        DataHandler.Doc doc = data.getDoc(id);
        Rank current = doc.getRank(RankType.rank);
        boolean wosGrifer= current == Ranks.griefer;
        data.setRank(id, rank, RankType.rank);
        String uuid = doc.getUuid();
        Administration.PlayerInfo inf=netServer.admins.getInfo(uuid);
        if(rank.isAdmin){
            netServer.admins.adminPlayer(inf.id,inf.adminUsid);
        } else {
            netServer.admins.unAdminPlayer(inf.id);
        }
        if (rank == Ranks.griefer || wosGrifer) {
            String sub = getSubnet(doc.getIp());
            if(wosGrifer) {
                subnet.remove(sub);
            } else {
                subnet.add(sub);
            }
            saveSubnet();
        }
        PD pd = online.get(doc.getUuid());
        if(pd != null) {
            pd.rank = rank;
            pd.updateName();
            pd.player.isAdmin = rank.isAdmin;
        }
    }

    private static void saveSubnet() {
        saveSimple(subnetFile, subnet, null);
    }

    public static void loadSubnet(){
        String[] subnet = loadSimpleCollection(subnetFile, Database::saveSubnet);
        if(subnet == null) return;
        Database.subnet.addAll(Arrays.asList(subnet));
    }

    public static void loadPets(){
        pets.clear();
        HashMap<String, Pet[]> pets = loadSimpleHashmap(petFile, Pet[].class, Database::defaultPets);
        if(pets == null) return;
        Pet[] pts = pets.get("pets");
        if(pts == null) return;
        for(Pet p : pts) {
            if(p.trail == null) {
                logInfo("pet-invalid-trail", p.name);
                continue;
            }
            Database.pets.put(p.name,p);
        }

    }

    public static void defaultPets(){
        saveSimple(petFile, new HashMap<String, ArrayList<Pet>>(){{
            put("pets",new ArrayList<Pet>(){{ add(new Pet()); }});
        }},"pets");

    }

    public static int getDatabaseSize(){
        return (int) database.runCommand(new Document("collStats", playerCollection)).get("count");
    }



    public static ArrayList<String> search(String[] args, int limit, PD pd){
        ArrayList<String> result = new ArrayList<>();
        FindIterable<Document> res;
        if(args.length > 1) {
            switch (args[0]){
                case "sort":
                    if(!enumContains(Stat.values(), args[1])){
                        result.add(format(getTranslation(pd, "search-invalid-mode"), Arrays.toString(Stat.values())));
                        return result;
                    }
                    res = rawData.find().sort(new BsonDocument(args[1], new BsonInt32(args.length == 3 ? -1 : 1))).limit(limit);
                    break;
                case "rank":

                    if(!Ranks.buildIn.containsKey(args[1])) {
                        result.add(getTranslation(pd, "search-non-existent-rank"));
                        return result;
                    }
                    res = rawData.find(Filters.eq(RankType.rank.name(), args[1])).limit(limit);
                    break;
                case "specialrank":

                    if(!Ranks.special.containsKey(args[1])) {
                        result.add(getTranslation(pd, "search-non-existent-special-rank"));
                        return result;
                    }
                    res = rawData.find(Filters.eq(RankType.specialRank.name(), args[1])).limit(limit);
                    if (res.first() == null){
                        res = rawData.find(Filters.eq(RankType.specialRank.name(), args[1])).limit(limit);
                    }
                    break;
                default:
                    result.add(getTranslation(pd, "invalid-mode"));
                    return result;
            }
        } else {
            Pattern pattern = Pattern.compile("^"+Pattern.quote(args[0]), Pattern.CASE_INSENSITIVE);
            res = rawData.find(Filters.regex("originalName", pattern)).limit(limit);
        }

        for(Document d : res) {
            result.add(docToString(d));
        }
        return result;
    }

    //todo this is incorrect function fix it
    static String docToString(Document doc) {
        DataHandler.Doc d = DataHandler.Doc.getNew(doc);
        return "[gray][yellow]" + d.getId() + "[] | " + d.getName() + " | []" + d.getRank(RankType.rank).Suffix() ;

    }

    private static class AfkMaker {
        private static final int updateRate = 60;
        private static final int requiredTime = 1000*60*5;
        private AfkMaker(){
            Timer.schedule(()->{
                try {
                    for (Player p : playerGroup) {
                        PD pd = online.get(p.uuid);
                        synchronized (pd) {
                            if (pd.afk && Time.timeSinceMillis(pd.lastAction) < requiredTime) {
                                pd.afk = false;
                                pd.updateName();
                                sendMessage("afk-is-not", pd.name, AFK);
                                return;
                            }
                            if (!pd.afk && Time.timeSinceMillis(pd.lastAction) > requiredTime) {
                                pd.afk = true;
                                pd.updateName();
                                sendMessage("afk-is", pd.name, AFK);
                            }
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }, 0, updateRate);
        }
    }
}
