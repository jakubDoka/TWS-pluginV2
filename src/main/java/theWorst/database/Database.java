package theWorst.database;

import arc.Events;
import arc.util.Strings;

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
import theWorst.Bot;
import theWorst.Global;
import theWorst.tools.Bundle;
import theWorst.tools.Millis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Pattern;

import static mindustry.Vars.*;
import static theWorst.tools.Formatting.*;
import static theWorst.tools.General.enumContains;
import static theWorst.tools.General.getCore;
import static theWorst.tools.Json.loadSimpleCollection;
import static theWorst.tools.Json.saveSimple;
import static theWorst.tools.Players.*;

public class Database {
    public static final String playerCollection = "PlayerData";
    public static final String AFK = "[gray]<AFK>[]";

    static final String subnetFile = Global.saveDir + "subnetBuns.json";

    public static MongoClient client = MongoClients.create(Global.config.dbAddress);
    static MongoDatabase database = client.getDatabase(Global.config.dbName);
    static MongoCollection<Document> rawData = database.getCollection(playerCollection);
    public static DataHandler data = new DataHandler(rawData);

    public static HashMap<String,PD> online = new HashMap<>();

    static HashSet<String> subnet = new HashSet<>();


    public static void init(){
        loadSubnet();
        new AfkMaker();

        Events.on(EventType.PlayerConnect.class,e-> {
            //remove fake ranks and colors
            Player player = e.player;
            String originalName = player.name;
            player.name = cleanName(player.name);
            if (!originalName.equals(player.name)) {
                //name cannot be blank
                if (player.name.replace(" ", "").isEmpty()) {
                    player.name = player.id + "&#@";
                }
                //let player know
                sendErrMessage(player, "name-modified");
            }
            PD pd = data.LoadData(player);
            Doc doc = data.getDoc(pd.id);
            online.put(player.uuid, pd);
            //marked subnet so mark player aromatically
            if (subnet.contains(getSubnet(player.con.address)) && pd.hasPermLevel(Perm.normal)) {
                Bot.onRankChange(pd.name, pd.id, pd.rank.name, Ranks.griefer.name, "Server", "Subnet ban.");
                setRank(pd.id, Ranks.griefer);
            }
            //resolving special rank
            pd.updateName();
            checkAchievements(pd, doc);
            Bot.connectUser(pd, doc);
            Bundle.findBundleAndCountry(pd);
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
                long id = getData(p).id;
                if(p.getTeam()==e.winner) {
                    data.incOne(id, Stat.gamesWon);
                }
                data.incOne(id, Stat.gamesPlayed);
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
                data.incOne(getData((Player) e.unit).id, Stat.deaths);
            }else if(e.unit.getTeam() != Team.sharded){
                for(Player p:playerGroup){
                    data.incOne(getData(p).id, Stat.enemiesKilled);
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
            long id = getData(e.player).id;
            if(e.breaking){
                data.incOne(id, Stat.buildingsBroken);
            }else {
                data.incOne(id, Stat.buildingsBuilt);
            }
        });

    }



    public static void checkAchievements(PD pd, Doc doc) {
        if(pd.isGriefer()) return;
        for(Rank r : Ranks.special.values()) {
            pd.removeRank(r);
            pd.sRank = null;
        }
        new Thread(()->{
            for(Rank rank : Ranks.special.values()){
                if(rank.condition(doc,pd)){
                    if (pd.sRank == null || pd.sRank.value < rank.value) {
                        synchronized (pd) {
                            pd.sRank = rank;
                        }
                    }
                    pd.addRank(rank);
                }
            }
            synchronized (pd){
                pd.updateName();
            }
        }).start();
    }



    public static boolean hasDisabled(Player player, Perm perm) {
        return data.contains(getData(player).id, "settings", perm.name());
    }

    public static PD getData(Player player) {
        return online.get(player.uuid);
    }

    public static boolean hasEnabled(Player player, Setting setting) {
        return data.contains(getData(player).id, "settings", setting.name());
    }

    public static boolean hasMuted(Player player, Player other){
        return data.contains(getData(player).id, "mutes", other.uuid);
    }

    //just for testing purposes
    public static void clear(){
        rawData.drop();
        reconnect();
    }

    public static void reconnect() {
        client = MongoClients.create(Global.config.dbAddress);
        database = client.getDatabase(Global.config.dbName);
        rawData = database.getCollection(playerCollection);
        data = new DataHandler(rawData);
    }

    public static Doc findData(String target) {
        Doc res = null;
        if(Strings.canParsePostiveInt(target)) {
            res = data.getDoc(Long.parseLong(target));
        }
        if (res == null) {
            for(PD pd : online.values()){
                if(pd.name.equals(target)){
                    return data.getDoc(pd.id);
                }
            }
        }
        return res;

    }



    public static void setRank(long id, Rank rank){
        Doc doc = data.getDoc(id);
        Rank current = doc.getRank(RankType.rank);
        boolean wosGrifer= current == Ranks.griefer;
        data.setRank(id, rank, RankType.rank);
        String uuid = doc.getUuid();
        if(uuid == null) return;
        Administration.PlayerInfo inf = netServer.admins.getInfo(uuid);
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
        PD pd = online.get(uuid);
        if(pd != null) {
            pd.removeRank(pd.rank);
            pd.rank = rank;
            pd.addRank(current);
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

    public static void disconnectAccount(PD pd){
        if(pd.paralyzed) return;
        if(!pd.getDoc().isProtected()) {
            Database.data.delete(pd.id);
        } else {
            Database.data.setUuid(pd.id, "why cant i just die");
            Database.data.setIp(pd.id, "because you are too week");
        }
    }

    static String docToString(Document doc) {
        Doc d = Doc.getNew(doc);
        return "[gray][yellow]" + d.getId() + "[] | " + d.getName() + " | []" + d.getRank(RankType.rank).getSuffix() ;

    }

    public static void reLogPlayer(Player player, long id) {
        player.name = getData(player).name;
        data.bind(player, id);
        online.put(player.uuid, data.LoadData(player));
        sendMessage(player, "database-re-logged");
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
                            if (pd.afk && Millis.since(pd.lastAction) < requiredTime) {
                                pd.afk = false;
                                pd.updateName();
                                sendMessage("afk-is-not", pd.name, AFK);
                                return;
                            }
                            if (!pd.afk && Millis.since(pd.lastAction) > requiredTime) {
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
