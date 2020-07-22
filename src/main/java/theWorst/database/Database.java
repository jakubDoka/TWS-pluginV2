package theWorst.database;

import arc.Events;
import arc.graphics.Color;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Timer;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
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
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import theWorst.Bot;
import theWorst.Global;
import theWorst.helpers.gameChangers.Pet;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import static mindustry.Vars.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static theWorst.Tools.Commands.logInfo;
import static theWorst.Tools.Formatting.*;
import static theWorst.Tools.General.*;
import static theWorst.Tools.Json.*;
import static theWorst.Tools.Players.*;

public class Database {
    public static final String playerCollection = "playerD";
    public static final String AFK = "[gray]<AFK>[]";
    static final String rankFile = Global.configDir + "specialRanks.json";
    static final String petFile = Global.configDir + "pets.json";
    static final String subnetFile = Global.saveDir + "subnetBuns.json";
    public static MongoClient client = MongoClients.create();
    static MongoDatabase database = client.getDatabase(Global.config.dbName);
    static MongoCollection<Document> rawData = database.getCollection(playerCollection);
    static MongoOperations data = new MongoTemplate(client, Global.config.dbName);
    static HashMap<String,PlayerD> online = new HashMap<>();
    public static HashMap<String,SpecialRank> ranks=new HashMap<>();
    public static HashMap<String, Pet> pets=new HashMap<>();
    static HashSet<String> subnet = new HashSet<>();

    private static final PlayerD defaultPD = new PlayerD(){{
            oldMeta = new PlayerD();
            uuid = "default";
    }};

    public Database(){
        autocorrect();
        loadSubnet();
        Events.on(EventType.ServerLoadEvent.class, e->{
            loadPets();
            loadRanks();
        });

        new AfkMaker();
        //todo test
        Events.on(EventType.PlayerConnect.class,e->{
            //remove fake ranks
            String originalName = e.player.name;
            e.player.name = cleanName(e.player.name);
            if(!originalName.equals(e.player.name)){
                //name cannot be blank
                if(e.player.name.replace(" ","").isEmpty()){
                    e.player.name = e.player.id +"&#@";
                }
                //let player know
                sendErrMessage(e.player,"name-modified");
            }
            PlayerD pd = new PlayerD(e.player);
            online.put(e.player.uuid, pd);
            //marked subnet so mark player aromatically
            Rank r = getRank(pd);
            if(subnet.contains(getSubnet(pd)) && r != Rank.griefer){
                Bot.onRankChange(pd.originalName, pd.serverId,r.name(), Rank.griefer.name(), "Server", "Subnet ban.");
                setRank(pd, Rank.griefer, e.player);
            }
            //resolving special rank
            SpecialRank specialRank = getSpecialRank(pd);
            if(specialRank != null && !specialRank.isPermanent()){
                pd.specialRank = "";
            }

            new Thread(()->{
                SpecialRank sr = getSpecialRank(pd);
                for(SpecialRank rank : ranks.values()){
                    if(rank.condition(pd) ){
                        if (sr==null || sr.value < rank.value) {
                            synchronized (pd) {
                                pd.specialRank = rank.name;
                            }
                            sr = rank;
                        }
                        synchronized (pd.obtainedRanks){
                            pd.obtainedRanks.add(sr);
                        }
                        addPets(pd, rank);
                    }
                }
            });


            //modify name based of rank
            updateName(e.player,pd);

            Runnable conMess = () -> sendMessage("player-connected",e.player.name,String.valueOf(pd.serverId));

            Bot.sendToLinkedChat(String.format("**%s** (ID:**%d**) hes connected.", cleanColors(e.player.name), pd.serverId));
            if (Bot.api == null || Bot.config.serverId == null || pd.rank.equals(Rank.griefer.name())) return;
            if (Bot.pendingLinks.containsKey(pd.serverId)){
                sendMessage(e.player,"discord-pending-link",Bot.pendingLinks.get(pd.serverId).name);
            }
            if (pd.discordLink == null) {
                conMess.run();
                return;
            }
            CompletableFuture<User> optionalUser = Bot.api.getUserById(pd.discordLink);
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
                        Rank finalR = null;
                        pd.donationLevel = "";
                        SpecialRank crDl = getDonationLevel(pd);
                        for (Role r : user.getRoles(server.get())) {
                            String roleName = r.getName();
                            SpecialRank dl = ranks.get(roleName);

                            if (enumContains(Rank.values(), roleName)) {
                                Rank rank = Rank.valueOf(roleName);
                                if (Rank.valueOf(pd.rank).getValue() < rank.getValue()) {
                                    finalR = rank;
                                }
                            }else if (dl != null ) {
                                if (crDl == null || dl.value > crDl.value){
                                    pd.donationLevel = roleName;
                                    crDl = dl;
                                }
                                synchronized (pd.obtainedRanks) {
                                    pd.obtainedRanks.add(dl);
                                }
                                addPets(pd, dl);
                            }
                        }
                        updateName(e.player, pd);

                        if (finalR != null) {
                            setRank(pd, finalR, e.player);
                        }
                        conMess.run();
                    } catch (InterruptedException | ExecutionException interruptedException) {
                        interruptedException.printStackTrace();
                    }
                }
            }, 0, .1f);
        });

        Events.on(EventType.PlayerLeave.class,e->{
            PlayerD pd = online.remove(e.player.uuid);
            if(pd == null) return;
            sendMessage("player-disconnected",e.player.name,String.valueOf(pd.serverId));
            Bot.sendToLinkedChat(String.format("**%s** (ID:**%d**) hes disconnected.", cleanColors(e.player.name), pd.serverId));
            pd.disconnect();
        });

        //games played and games won counter
        Events.on(EventType.GameOverEvent.class, e ->{
            for(Player p:playerGroup){
                if(p.getTeam()==e.winner) {
                    incOne(p, Stat.gamesWon);
                }
                incOne(p, Stat.gamesPlayed);
            }
        });

        //build and destruct permissions handling
        Events.on(EventType.BuildSelectEvent.class, e-> {
            if (!(e.builder instanceof Player)) return;
            Player player = (Player) e.builder;
            CoreBlock.CoreEntity core = getCore();
            if (core == null) return;
            BuilderTrait.BuildRequest request = player.buildRequest();
            if (request == null) return;
            if (hasSpecialPerm(player, Perm.destruct) && request.breaking) {
                for (ItemStack s : request.block.requirements) {
                    core.items.add(s.item, s.amount / 2);
                }
                Call.onDeconstructFinish(request.tile(), request.block, ((Player) e.builder).id);
            } else if (hasSpecialPerm(player, Perm.build) && !request.breaking && request.block.buildCost > 30) {
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
                incOne((Player) e.unit, Stat.deaths);
            }else if(e.unit.getTeam() != Team.sharded){
                for(Player p:playerGroup){
                    incOne(p, Stat.enemiesKilled);
                }
            }
        });

        //handle hammer event
        Events.on(EventType.PlayerIpBanEvent.class,e->{
            netServer.admins.unbanPlayerIP(e.ip);
            for(PlayerD pd : getAllMeta()){
                if(pd.ip.equals(e.ip)){
                    pd.rank=Rank.griefer.name();
                    netServer.admins.getInfo(pd.uuid).lastKicked=Time.millis();
                    subnet.add(getSubnet(pd));
                    break;
                }
            }
        });

        //count buildings and destroys
        Events.on(EventType.BlockBuildEndEvent.class, e->{
            if(e.player == null) return;
            if(!e.breaking && e.tile.block().buildCost/60<1) return;
            if(e.breaking){
                incOne(e.player, Stat.buildingsBroken);
            }else {
                incOne(e.player, Stat.buildingsBuilt);
            }
        });

    }

    void addPets(PlayerD pd, SpecialRank sr){
        if(!pd.settings.contains(Setting.pets.name())) return;
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

    public static void reload(){
        database = client.getDatabase(Global.config.dbName);
        rawData = database.getCollection(playerCollection);
        data = new MongoTemplate(client, Global.config.dbName);
    }

    //just for testing purposes
    public static void clean(){
        rawData.drop();
        data = new MongoTemplate(client,Global.config.dbName);
        rawData = MongoClients.create().getDatabase(Global.config.dbName).getCollection(playerCollection);
    }

    public static String getSubnet(PlayerD pd){
        String address=pd.ip;
        return address.substring(0,address.lastIndexOf("."));
    }

    public static void setRank(PlayerD pd,Rank rank,Player player){
        Rank current = getRank(pd);
        boolean wosGrifer= current == Rank.griefer;
        boolean wosAdmin = current.isAdmin;
        pd.rank=rank.name();
        Administration.PlayerInfo inf=netServer.admins.getInfo(pd.uuid);
        if(rank.isAdmin){
            if(!wosAdmin) netServer.admins.adminPlayer(inf.id,inf.adminUsid);
        }else if(wosAdmin) {
            netServer.admins.unAdminPlayer(inf.id);
        }
        if (rank == Rank.griefer || wosGrifer) {
            String sub = getSubnet(pd);
            if(wosGrifer) {
                subnet.remove(sub);
            } else {
                subnet.add(sub);
            }
            saveSubnet();
        }
        if(player==null){
            player=playerGroup.find(p->p.con.address.equals(pd.ip));
            if(player==null){
                pd.update();
                return;
            }
        }
        updateName(player,pd);
        player.isAdmin=rank.isAdmin;
    }

    private static void saveSubnet() {
        saveSimple(subnetFile, subnet, null);
    }

    public static void loadSubnet(){
        String[] subnet = loadSimpleCollection(subnetFile, Database::saveSubnet);
        if(subnet == null) return;
        Database.subnet.addAll(Arrays.asList(subnet));
    }

    public static void loadRanks() {
        ranks.clear();
        HashMap<String, SpecialRank[]> ranks = loadSimpleHashmap(rankFile, SpecialRank[].class, Database::defaultRanks);
        if (ranks == null) return;
        SpecialRank[] srs = ranks.get("ranks");
        if (srs == null) return;
        boolean invalid = false;
        for (SpecialRank r : srs) {
            if (r.quests != null) {
                for (String s : r.quests.keySet()) {
                    if (!enumContains(Stat.values(), s)) {
                        logInfo("special-error-invalid-stat", r.name, s);
                        invalid = true;
                    }
                    for (String l : r.quests.get(s).keySet()) {
                        if (!enumContains(SpecialRank.Mod.values(), l)) {
                            logInfo("special-error-invalid-stat-property", r.name, s, l);
                            invalid = true;
                        }
                    }
                }
            }
            if (r.linked != null) {
                for (String l : r.linked) {
                    if (!ranks.containsKey(l)) {
                        logInfo("special-rank-error-missing-rank", l, r.name);
                        invalid = true;
                    }

                }
            }
            Database.ranks.put(r.name, r);
        }
        if (invalid) {
            ranks.clear();
            logInfo("special-rank-file-invalid");
        }

    }

    public static void defaultRanks(){

        ArrayList<SpecialRank> arr = new ArrayList<>();
        arr.add(new SpecialRank(){
            {
                name = "kamikaze";
                color = "scarlet";
                description = new HashMap<String, String>(){{
                    put("default","put your description here.");
                    put("en_US","Put translation like this.");
                }};
                value = 1;
                permissions = new HashSet<String>(){{add(Perm.suicide.name());}};
                quests = new HashMap<String, HashMap<String, Integer>>(){{
                    put(Stat.deaths.name(), new HashMap<String, Integer>(){{
                        put(Mod.best.name(), 10);
                        put(Mod.required.name(), 100);
                        put(Mod.frequency.name(), 20);
                    }});
                }};
            }
        });
        arr.add(new SpecialRank(){{
            name = "donor";
            color = "#" + Color.gold.toString();
            description = new HashMap<String, String>(){{
                put("default","For people who support server financially.");
            }};
            permissions = new HashSet<String>(){{
                add(Perm.colorCombo.name());
                add(Perm.suicide.name());
            }};
            pets = new ArrayList<String>(){{
                add("fire-pet");
                add("fire-pet");
            }};
        }});
        HashMap<String, ArrayList<SpecialRank>> ranks = new HashMap<>();
        ranks.put("ranks", arr);
        saveSimple(rankFile, ranks, "special ranks");
        for(SpecialRank sr : arr){
            Database.ranks.put(sr.name, sr);
        }
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

    public static SpecialRank getSpecialRank(PlayerD pd) {
        if (pd.specialRank == null ||pd.specialRank.isEmpty()) return null;
        SpecialRank sr = ranks.get(pd.specialRank);
        if (sr == null) pd.specialRank = "";
        return sr;
    }

    public static SpecialRank getDonationLevel(PlayerD pd) {
        if (pd.donationLevel == null || pd.donationLevel.isEmpty()) return null;
        SpecialRank sr = ranks.get(pd.donationLevel);
        if (sr == null) pd.donationLevel = "";
        return sr;
    }

    public static void updateName(Player player,PlayerD pd){
        player.name=pd.originalName;
        SpecialRank rank = getDonationLevel(pd);
        SpecialRank level = getSpecialRank(pd);
        if (pd.afk){
            player.name += AFK;
        } else if(level != null) {
            player.name += level.getSuffix();
        } else if(rank != null) {
            player.name += rank.getSuffix();
        } else {
            player.name += getRank(pd).getSuffix();
        }
        //name changes for some reason removes admin tag so this is necessary
        player.isAdmin = getRank(pd).isAdmin;
    }

    public static void updateMeta(PlayerD pd){
        Database.data.findAndReplace(new Query(where("_id").is(pd.uuid)),pd);
    }

    public static PlayerD query(String property, Object value){
        return data.findOne(new Query(where(property).is(value)),PlayerD.class);
    }

    public static PlayerD getMeta(String uuid){
        return query("_id", uuid);
    }

    public static PlayerD getMetaById(long id){
        return query("serverId", id);
    }

    public static Document getRawMeta(String uuid) {
        return rawData.find(Filters.eq("_id",uuid)).first();
    }

    public static int getDatabaseSize(){
        return (int) database.runCommand(new Document("collStats", playerCollection)).get("count");
    }

    public static List<PlayerD> getAllMeta(){
        return data.findAll(PlayerD.class);
    }

    public static FindIterable<Document> getAllRawMeta(){
        return rawData.find();
    }

    public static PlayerD getData(Player player){
        return online.getOrDefault(player.uuid,defaultPD);
    }

    public static PlayerD findData(String key){
        PlayerD pd;
        if(Strings.canParsePostiveInt(key)){
            pd = getMetaById(Long.parseLong(key));
        } else {
            pd = getMeta(key);
        }
        if(pd == null){
            for(PlayerD data : online.values()){
                if(cleanName(data.originalName).equals(key)) return data;
            }
            return null;
        }
        //making changes to the instance of data of player that is online has no effect
        if(online.containsKey(pd.uuid)){
            return online.get(pd.uuid);
        }
        return pd;
    }

    public static void inc(Player player, Stat stat, long amount){
        rawData.updateOne(Filters.eq("uuid", player.uuid), Updates.inc(stat.name(), amount));
    }

    public static void incOne(Player player, Stat stat) {
        inc(player, stat, 1);
    }



    public static boolean hasEnabled(Player player, Setting setting){
        return getData(player).settings.contains(setting.name());
    }

    public static boolean hasPerm(Player player,Perm perm){
        for(Perm p : getRank(getData(player)).permissions) {
            if(p.getValue() >= perm.getValue()) return true;
        }
        return false;
    }

    public static boolean hasSpecialPerm(Player player,Perm perm){
        PlayerD pd = getData(player);
        if(pd.settings.contains(perm.name())) return false;
        for ( SpecialRank s : pd.obtainedRanks) {
            if (s.permissions.contains(perm.name())) {
                return true;
            }
        }
        SpecialRank dl = getDonationLevel(pd);
        if(dl != null) return dl.permissions.contains(perm.name());
        return false;
    }

    //when structure of playerD is changed this function tries its bet to make database still compatible
    //mongoDB surly has tools for this kind of actions its just too complex for me to figure out
    private static void autocorrect(){
        //creates document from updated class
        PlayerD pd = new PlayerD();
        for(Field f : PlayerD.class.getDeclaredFields()){
            try {
                Object val = f.get(pd);
                if(val == null && f.getType() == String.class){
                    f.set(pd, "");
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        data.save(pd,"pref");
        //assuming that all documents have same structure, and they should we are taking one o the old ones
        Document oldDoc = rawData.find().first();
        Document currentDoc = data.getCollection("pref").find().first();
        data.getCollection("pref").drop();
        //i don't know how this can happen but i want that database the database that produced it
        if(currentDoc == null || oldDoc == null){
            Log.err(currentDoc == null ? "missing current doc" : "missing old doc");
            return;
        }
        //if there is new field added
        for(String s : currentDoc.keySet()){
            if(!oldDoc.containsKey(s)){
                for(Document d : getAllRawMeta()){
                    if(d.containsKey(s)) continue;
                    d.append(s, currentDoc.get(s));
                    rawData.replaceOne(Filters.eq("_id",d.get("_id")),d);
                }
                logInfo("autocorrect-field-add",s);
            }
        }
        //if field wos removed
        for(String s : oldDoc.keySet()){
            if(!currentDoc.containsKey(s)){
                for(Document d : getAllRawMeta()){
                    if(!d.containsKey(s)) continue;
                    d.remove(s);
                    rawData.replaceOne(Filters.eq("_id",d.get("_id")),d);
                }
                logInfo("autocorrect-field-remove",s);
            }
        }
    }

    public static ArrayList<String> search(String[] args, int limit, PlayerD pd){
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

                    if(!enumContains(Rank.values(), args[1])) {
                        result.add(getTranslation(pd, "search-non-existent-rank"));
                        return result;
                    }
                    res = rawData.find(Filters.eq("rank", args[1])).limit(limit);
                    break;
                case "specialrank":

                    if(!ranks.containsKey(args[1])) {
                        result.add(getTranslation(pd, "search-non-existent-special-rank"));
                        return result;
                    }
                    res = rawData.find(Filters.eq("specialRank", args[1])).limit(limit);
                    if (res.first() == null){
                        res = rawData.find(Filters.eq("donationLevel", args[1])).limit(limit);
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

    static String docToString(Document doc) {
        String r = (String) doc.get("rank");
        return "[gray][yellow]" + doc.get("serverId") + "[] | " + doc.get("originalName") + " | []" + Rank.valueOf(r).getName();

    }

    private static class AfkMaker {
        private static final int updateRate = 60;
        private static final int requiredTime = 1000*60*5;
        private AfkMaker(){
            Timer.schedule(()->{
                try {
                    synchronized (this){
                        for(Player p : playerGroup){
                            PlayerD pd = getData(p);
                            if(pd.afk && Time.timeSinceMillis(pd.lastAction)<requiredTime){
                                pd.afk = false;
                                updateName(p,pd);
                                sendMessage("afk-is-not",pd.originalName,AFK);
                                return;
                            }
                            if (!pd.afk && Time.timeSinceMillis(pd.lastAction)>requiredTime){
                                pd.afk = true;
                                updateName(p,pd);
                                sendMessage("afk-is",pd.originalName,AFK);
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
