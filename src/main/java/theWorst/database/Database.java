package theWorst.database;

import arc.Core;
import arc.Events;
import arc.math.geom.Vec2;
import arc.util.Log;
import arc.util.Strings;

import arc.util.Timer;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import mindustry.entities.traits.BuilderTrait;
import mindustry.entities.type.Player;
import mindustry.entities.type.TileEntity;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.net.Administration;
import mindustry.type.ItemStack;
import mindustry.ui.fragments.Fragment;
import mindustry.world.Tile;
import mindustry.world.blocks.BuildBlock;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.BlockFlag;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.Document;
import theWorst.Bot;
import theWorst.Global;
import theWorst.tools.Bundle;
import theWorst.tools.Millis;
import theWorst.tools.VPNDetection;

import java.awt.*;
import java.util.*;
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
    public static final String counter = "counter";
    static final String subnetFile = Global.saveDir + "subnetBuns.json";
    static final String cpnFile = Global.saveDir + "detectedVpn.json";

    public static MongoClient client = MongoClients.create(Global.config.dbAddress);
    public static MongoDatabase database = client.getDatabase(Global.config.dbName);
    static MongoCollection<Document> rawData = database.getCollection(playerCollection);
    public static DataHandler data = new DataHandler(rawData, database.getCollection(counter));

    public static HashMap<String,PD> online = new HashMap<>();

    public static HashSet<String> subnet = new HashSet<>(), vpn = new HashSet<>();

    static PD defaultPd = new PD(player);

    static final HashMap<TileEntity, Long> placedTurrets = new HashMap<>();

    static final KillCountResolver killCounter = new KillCountResolver();

    static final HashSet<Long> buildBoostIds = new HashSet<>();

    public static void init(){
        loadSubnet(subnet);
        loadSubnet(vpn);
        new AfkMaker();

        Events.on(EventType.PlayerJoin.class,e-> {
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
            if(!pd.hasPermLevel(Perm.high) && !pd.paralyzed){
                if (subnet.contains(getSubnet(player.con.address))) {
                    Bot.onRankChange(pd.name, pd.id, pd.rank.name, Ranks.griefer.name, "Server", "Subnet ban.");
                    setRank(pd.id, Ranks.griefer);
                }


            }

            //resolving special rank
            pd.updateName();
            checkAchievements(pd, doc);
            Bot.connectUser(pd, doc);
            Bundle.findBundleAndCountry(pd);
        });

        Events.on(EventType.PlayerLeave.class,e->{
            PD pd = online.get(e.player.uuid);
            if(pd == null) return;

            sendMessage("player-disconnected",e.player.name,String.valueOf(pd.id));
            Bot.sendToLinkedChat(String.format("**%s** (ID:**%d**) has disconnected.", cleanColors(e.player.name), pd.id));
            online.remove(e.player.uuid);
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

        Events.on(EventType.WorldLoadEvent.class, e ->{
            synchronized (placedTurrets){
                placedTurrets.clear();
            }
        });

        //build and destruct permissions handling
        Events.on(EventType.BuildSelectEvent.class, e-> {
            try {
                if (!(e.builder instanceof Player)) return;
                Player player = (Player) e.builder;
                PD pd = getData(player);
                if(!buildBoostIds.contains(pd.id)){
                    buildBoostIds.add(pd.id);
                    return;
                }
                CoreBlock.CoreEntity core = getCore();
                if (core == null) return;
                BuilderTrait.BuildRequest request = player.buildRequest();
                if (request == null) return;
                if (pd.hasThisPerm(Perm.destruct) && request.breaking) {
                    for (ItemStack s : request.block.requirements) {
                        core.items.add(s.item, s.amount / 2);
                    }
                    Call.onDeconstructFinish(request.tile(), request.block, ((Player) e.builder).id);

                } else if (pd.hasThisPerm(Perm.build) && !request.breaking) {
                    if (core.items.has(multiplyReq(request.block.requirements, Global.limits.builderMinMaterialReq)) && request.progress > Global.limits.builderBoost) {
                        for (ItemStack s : request.block.requirements) {
                            core.items.remove(s);
                        }
                        BuildBlock.constructed(e.tile, request.block, ((Player) e.builder).id, (byte) request.rotation, player.getTeam(), request.hasConfig);
                        if (request.hasConfig) Call.onTileConfig(null, e.tile, request.config);
                    }
                } else return;

                buildBoostIds.remove(pd.id);
                //necessary because instant build or break do not trigger event
                Events.fire(new EventType.BlockBuildEndEvent(e.tile, player, e.team, e.breaking));
            } catch (Exception ex){
                ex.printStackTrace();
            }
        });

        //count units killed and deaths
        Events.on(EventType.UnitDestroyEvent.class, e->{
            if(e.unit instanceof Player){
                data.incOne(getData((Player) e.unit).id, Stat.deaths);
            }else if(e.unit.getTeam() != Team.sharded){
                killCounter.queue.add(()->{
                    HashSet<Long> seen = new HashSet<>();
                    for(TileEntity t : new HashSet<>(placedTurrets.keySet())){
                        Turret tur = (Turret)t.block;
                        if(tur == null ) continue;
                        Turret.TurretEntity ent = (Turret.TurretEntity)t;
                        if(new Vec2(t.x, t.y).sub(new Vec2(e.unit.x, e.unit.y)).len() < tur.range) continue;
                        if(ent.totalAmmo == 0 || (ent.power != null && ent.power.status == 0)) continue;
                        seen.add(placedTurrets.get(t));

                    }
                    for( Long id : seen) {
                        data.incOne(id, Stat.enemiesKilled);
                    }
                });
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
            long id = getData(e.player).id;
            if(e.breaking){
                removeTurret(e.tile);
            } else if(e.tile.ent() != null && e.tile.block().flags.contains(BlockFlag.turret)) {
                synchronized (placedTurrets) {
                    placedTurrets.put(e.tile.ent(), id);
                }
            }
            if(!e.breaking && e.tile.block().buildCost/60<1) return;

            if(e.breaking){
                data.incOne(id, Stat.buildingsBroken);

            }else {
                data.incOne(id, Stat.buildingsBuilt);

            }
        });

        Events.on(EventType.BlockDestroyEvent.class, e-> removeTurret(e.tile));

        Events.on(EventType.WithdrawEvent.class, e-> data.inc(getData(e.player).id, Stat.itemsTransported, e.amount));

        Events.on(EventType.DepositEvent.class, e-> data.inc(getData(e.player).id, Stat.itemsTransported, e.amount));

    }

    private static ItemStack[] multiplyReq(ItemStack[] requirements, int multiplier) {
        ItemStack[] res = new ItemStack[requirements.length];
        for(int i = 0; i < res.length; i++){
            res[i] = new ItemStack(requirements[i].item, requirements[i].amount * multiplier);
        }
        return res;
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

    public static void removeTurret(Tile tile) {
        if(!tile.block().flags.contains(BlockFlag.turret)) return;
        synchronized (placedTurrets) {
            if (tile.ent() == null) {
                for (TileEntity t : placedTurrets.keySet()) {
                    if (t.tile.pos() == tile.pos()) {
                        placedTurrets.remove(t);
                    }
                }
                return;
            }
            placedTurrets.remove(tile.ent());
        }
    }



    public static boolean hasDisabled(Player player, Perm perm) {
        return data.contains(getData(player).id, "settings", perm.name());
    }

    public static PD getData(Player player) {
        if(!online.containsKey(player.uuid)){
            //throw new RuntimeException("Error missing data of player " + player.name + " with uuid " + player.uuid);
            Log.info("ono");
        }
        return online.getOrDefault(player.uuid, defaultPd);
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
        data = new DataHandler(rawData, database.getCollection(counter));
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
        boolean wosGrifer = current == Ranks.griefer;
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
            if (pd.isGriefer()) {
                pd.perms.clear();
            }
            pd.addRank(rank);
            pd.updateName();
            pd.player.isAdmin = rank.isAdmin;
        } else {
            Player player = playerGroup.find(p -> p.uuid.equals(uuid));
            if (player != null) {
                reLogPlayer(player, id);
            }
        }
    }

    static void saveSubnet() {
        saveSimple(subnetFile, subnet, null);
    }

    static void saveVpn() {
        saveSimple(cpnFile, vpn, null);
    }

    public static void loadSubnet(HashSet<String> dest){

        String[] subnet = loadSimpleCollection(subnetFile, Database::saveSubnet);
        if(subnet == null) return;
        dest.clear();
        dest.addAll(Arrays.asList(subnet));
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
            if( args[0].equals("online")) {
                for( Player p : playerGroup) {
                    result.add(docToString(getData(p).getDoc().data));
                }
                return result;
            }
            Pattern pattern = Pattern.compile("^"+Pattern.quote(args[0]), Pattern.CASE_INSENSITIVE);
            res = rawData.find(Filters.regex("name", pattern)).limit(limit);
        }

        for(Document d : res) {
            result.add(docToString(d) + (args[0].equals("sort") ? " = " + d.get(args[1]) : ""));
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
                        PD pd = getData(p);
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

    private static class KillCountResolver {
        final ArrayList<Runnable> queue = new ArrayList<>();
        Timer.Task thread;

        KillCountResolver() {
            thread = Timer.schedule(()->{
                while (true){
                    synchronized (queue){
                        if(queue.isEmpty()) return;
                        queue.remove(0).run();
                    }
                }
            }, .1f, .1f);
        }
    }
}
