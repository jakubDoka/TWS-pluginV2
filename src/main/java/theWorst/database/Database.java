package theWorst.database;

import arc.Events;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Timer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.bson.Document;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import theWorst.Bot;
import theWorst.Config;
import theWorst.Tools;

import java.awt.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static mindustry.Vars.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static theWorst.Tools.logInfo;

public class Database {
    public static final String AFK = "[gray]<AFK>[]";
    static final String rankFile = Config.configDir + "specialRanks.json";
    static final String petFile = Config.configDir + "pets.json";
    static final String subnetFile = Config.saveDir + "subnetBuns.json";
    public static MongoClient client = MongoClients.create();
    static MongoDatabase database = client.getDatabase(Config.dbName);
    static MongoCollection<Document> rawData = database.getCollection("playerD");
    static MongoOperations data = new MongoTemplate(client, Config.dbName);
    static HashMap<String,PlayerD> online = new HashMap<>();
    public static HashMap<String,SpecialRank> ranks=new HashMap<>();
    public static HashMap<String,Pet> pets=new HashMap<>();
    static HashSet<String> subnet = new HashSet<>();

    private static final PlayerD defaultPD = new PlayerD(){{
            oldMeta=new PlayerD();
            uuid = "default";
    }};
    private static final SpecialRank error = new SpecialRank(){{
            name="error";
            color="red";
    }};

    public Database(){
        autocorrect();
        loadSubnet();
        Events.on(EventType.ServerLoadEvent.class, e->{
            loadPets();
            loadRanks();
        });

        new AfkMaker();
        Events.on(EventType.PlayerConnect.class,e->{
            //remove fake ranks
            String name = e.player.name;
            e.player.name = Tools.cleanEmotes(e.player.name);
            if(!name.equals(e.player.name)){
                //name cannot be blank
                if(e.player.name.replace(" ","").isEmpty()){
                    e.player.name = e.player.id +"&#@";
                }
                //let player know
                Tools.sendErrMessage(e.player,"name-modified");
            }
            PlayerD pd = new PlayerD(e.player);
            online.put(e.player.uuid, pd);
            //marked subnet so mark player aromatically
            if(subnet.contains(getSubnet(pd)) && Tools.getRank(pd) != Rank.griefer){
                setRank(pd, Rank.griefer, e.player);
                Tools.sendMessage("griefer-subnet",e.player.name);
            }
            //resolving special rank
            SpecialRank sp = getSpecialRank(pd);
            if(sp != null && !sp.isPermanent()){
                pd.specialRank = null;
                sp = null;
            }
            for(SpecialRank rank : ranks.values()){
                if(rank.condition(pd) && (sp==null || sp.value>rank.value)){
                   pd.specialRank = rank.name;
                   sp = rank;
                }
            }
            if(sp != null && sp.pets != null){
                for(String pet : sp.pets){
                    Pet found = pets.get(pet);
                    if(found == null){
                        Log.info("missing pet :" + pet);
                    } else {
                        pd.pets.add(new Pet(found));
                    }
                }
            }
            //modify name based of rank
            updateName(e.player,pd);
            Tools.sendMessage("player-connected",e.player.name,String.valueOf(pd.serverId));
            if (Bot.api == null || Bot.config.serverId == null || pd.rank.equals(Rank.griefer.name())) return;
            if (Bot.pendingLinks.containsKey(pd.serverId)){
                Tools.sendMessage(e.player,"discord-pending-link",Bot.pendingLinks.get(pd.serverId).name);
            }
            if (pd.discordLink == null) return;
            CompletableFuture<User> optionalUser = Bot.api.getUserById(pd.discordLink);
            Timer.schedule(new Timer.Task() {
                @Override
                public void run() {
                    if (optionalUser.isDone()) {
                        this.cancel();
                        try {
                            User user = optionalUser.get();
                            if (user == null) return;
                            Optional<Server> server = Bot.api.getServerById(Bot.config.serverId);
                            if (!server.isPresent()) return;
                            Rank finalR = null;
                            for (Role r : user.getRoles(server.get())) {
                                if(Tools.enumContains(Rank.values(),r.getName())){
                                    Rank rank = Rank.valueOf(r.getName());
                                    if (Rank.valueOf(pd.rank).getValue() < rank.getValue()) {
                                        finalR = rank;
                                    }
                                }
                            }
                            if(finalR == null) return;
                            setRank(pd, finalR, e.player);
                        } catch (InterruptedException | ExecutionException interruptedException) {
                            interruptedException.printStackTrace();
                        }
                    }
                }
            }, 0, .1f);
        });

        Events.on(EventType.PlayerLeave.class,e->{
            PlayerD pd = online.remove(e.player.uuid);
            if(pd == null) return;
            Tools.sendMessage("player-disconnected",e.player.name,String.valueOf(pd.serverId));
            pd.disconnect();
        });

        //games played and games won counter
        Events.on(EventType.GameOverEvent.class, e ->{
            for(Player p:playerGroup){
                PlayerD pd=getData(p);
                if(p.getTeam()==e.winner) {
                    pd.gamesWon++;
                }
                pd.gamesPlayed++;
            }
        });

        //build and destruct permissions handling
        Events.on(EventType.BuildSelectEvent.class, e-> {
            if (!(e.builder instanceof Player)) return;
            boolean happen = false;
            Player player = (Player) e.builder;
            CoreBlock.CoreEntity core = Tools.getCore();
            if (core == null) return;
            BuilderTrait.BuildRequest request = player.buildRequest();
            if (request == null) return;
            if (hasSpecialPerm(player, Perm.destruct) && request.breaking) {
                happen = true;
                for (ItemStack s : request.block.requirements) {
                    core.items.add(s.item, s.amount / 2);
                }
                Call.onDeconstructFinish(request.tile(), request.block, ((Player) e.builder).id);
            } else if (hasSpecialPerm(player, Perm.build) && !request.breaking) {
                if (core.items.has(request.block.requirements)) {
                    happen = true;
                    for (ItemStack s : request.block.requirements) {
                        core.items.remove(s);
                    }
                    Call.onConstructFinish(e.tile, request.block, ((Player) e.builder).id,
                            (byte) request.rotation, player.getTeam(), false);
                    e.tile.configure(request.config);
                }
            }
            //necessary because instant build or break do not trigger event
            if (happen) Events.fire(new EventType.BlockBuildEndEvent(e.tile, player, e.team, e.breaking));
        });

        //count units killed and deaths
        Events.on(EventType.UnitDestroyEvent.class, e->{
            if(e.unit instanceof Player){
                getData((Player) e.unit).deaths++;
            }else if(e.unit.getTeam() != Team.sharded){
                for(Player p:playerGroup){
                    getData(p).enemiesKilled++;
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
            PlayerD pd=getData(e.player);
            if(e.breaking){
                pd.buildingsBroken++;
            }else {
                pd.buildingsBuilt++;
            }
        });

    }

    public static void reload(){
        database = client.getDatabase(Config.dbName);
        rawData = database.getCollection("playerD");
        data = new MongoTemplate(client, Config.dbName);
    }

    //just for testing purposes
    public static void clean(){
        rawData.drop();
        data = new MongoTemplate(client,Config.dbName);
        rawData = MongoClients.create().getDatabase(Config.dbName).getCollection("players");
    }

    public static String getSubnet(PlayerD pd){
        String address=pd.ip;
        return address.substring(0,address.lastIndexOf("."));
    }

    public static void setRank(PlayerD pd,Rank rank,Player player){
        Rank current = Tools.getRank(pd);
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
        JSONObject data = new JSONObject();
        JSONArray array = new JSONArray();
        for(String s : subnet){
            array.add(s);
        }
        data.put("subnet",array);
        Tools.saveJson(subnetFile,data.toJSONString());
    }

    public static void loadSubnet(){
        subnet.clear();
        Tools.loadJson(subnetFile,(data)->{
            for(Object o : (JSONArray) data.get("subnet")){
                subnet.add((String) o);
            }
        },Database::saveSubnet);
    }

    public static boolean loadRanks() {
        ranks.clear();
        AtomicBoolean res = new AtomicBoolean(true);
        Tools.loadJson(rankFile,data -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                SpecialRank[] srs = mapper.readValue(((JSONArray)data.get("ranks")).toJSONString(),SpecialRank[].class);
                for(SpecialRank sr : srs)
                    ranks.put(sr.name,sr);
            } catch (IOException ex){
                ex.printStackTrace();
            }
        },()->{
            Tools.saveJson(rankFile, "{\n" +
                    "    \"ranks\":[\n" +
                    "        {\n" +
                    "            \"name\":\"kamikaze\",\n" +
                    "            \"color\":\"scarlet\",\n" +
                    "            \"description\":\"missing\",\n" +
                    "            \"value\":1,\n" +
                    "            \"permissions\":[\"suicide\"],\n" +
                    "            \"pets\":[\"fire-pet\"],\n" +
                    "            \"quests\":{\n" +
                    "                \"deaths\":{\n" +
                    "                    \"best\":2\n" +
                    "                }\n" +
                    "            }\n" +
                    "        },\n" +
                    "        {\n" +
                    "            \"name\":\"builder\",\n" +
                    "            \"color\":\"green\",\n" +
                    "            \"description\":\"You have to build a lot o get this rank.\",\n" +
                    "            \"value\":5,\n" +
                    "            \"permissions\":[\"build\"],\n" +
                    "            \"quests\":{\n" +
                    "                \"buildingsBuilt\":{\n" +
                    "                    \"required\":10000,\n" +
                    "                    \"frequency\":300\n" +
                    "                }\n" +
                    "            }\n" +
                    "        }\n" +
                    "    ]\n" +
                    "}");
            Tools.logInfo("files-default-config-created","special ranks", rankFile);
        });
        return res.get();
    }

    public static boolean loadPets(){
        pets.clear();
        AtomicBoolean res = new AtomicBoolean(true);
        Tools.loadJson(petFile,data -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                Pet[] pets = mapper.readValue(((JSONArray)data.get("pets")).toJSONString(),Pet[].class);
                for(Pet p : pets)
                    Database.pets.put(p.name,p);
            } catch (IOException ex){
                ex.printStackTrace();
            }
        },()->{

            try {
                ObjectMapper mapper = new ObjectMapper();

                JSONObject pet = (JSONObject) new JSONParser().parse(mapper.writeValueAsString(new Pet()));
                JSONObject data = new JSONObject();
                JSONArray array = new JSONArray();
                array.add(pet);
                data.put("pets",array);
                Tools.saveJson(petFile, data.toJSONString());
                Tools.logInfo("files-default-config-created","pets", petFile);
            } catch (JsonProcessingException | ParseException e) {
                e.printStackTrace();
            }


        });
        return res.get();}

    public static SpecialRank getSpecialRank(PlayerD pd){
        if(pd.specialRank==null) return null;
        SpecialRank sr=ranks.get(pd.specialRank);
        if(sr==null){
            pd.specialRank=null;
            return error;
        }
        return sr;
    }

    public static void updateName(Player player,PlayerD pd){
        player.name=pd.originalName;
        if (pd.afk){
            player.name += AFK;
        } else if(pd.specialRank!=null) {
            SpecialRank rank = getSpecialRank(pd);
            if(rank == null){
                pd.specialRank = null;
                updateName(player,pd);
                return;
            }
            player.name += rank.getSuffix();
        } else {
            player.name += Tools.getRank(pd).getSuffix();
        }
        //name changes for some reason removes admin tag so this is necessary
        player.isAdmin = Tools.getRank(pd).isAdmin;
    }

    static void updateMeta(PlayerD pd){
        Database.data.findAndReplace(new Query(where("_id").is(pd.uuid)),pd);
    }

    public static PlayerD getMeta(String uuid){
        return data.findOne(new Query(where("_id").is(uuid)),PlayerD.class);
    }

    public static PlayerD getMetaById(long id){
        return data.findOne(new Query(where("serverId").is(id)),PlayerD.class);
    }

    public static Document getRawMeta(String uuid) {
        return rawData.find(Filters.eq("_id",uuid)).first();
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
            Log.info("happened");
            pd = getMetaById(Long.parseLong(key));
        } else {
            pd = getMeta(key);
        }
        if(pd == null){
            for(PlayerD data : online.values()){
                if(data.originalName.equals(key)) return data;
            }
            return null;
        }
        //making changes to the instance of data of player that is online has no effect
        if(online.containsKey(pd.uuid)){
            return online.get(pd.uuid);
        }
        return pd;
    }

    public static boolean hasEnabled(Player player, Setting setting){
        return getData(player).settings.contains(setting.name());
    }

    public static boolean hasPerm(Player player,Perm perm){
        return Rank.valueOf(getData(player).rank).permission.getValue()>=perm.getValue();
    }

    public static boolean hasThisPerm(Player player,Perm perm){
        Rank rank = Rank.valueOf(getData(player).rank);
        return rank.permission==perm;
    }

    public static boolean hasSpecialPerm(Player player,Perm perm){
        SpecialRank sr=getSpecialRank(getData(player));
        if(sr==null) return false;
        return sr.permissions.contains(perm.name());
    }

    //when structure of playerD is changed this function tries its bet to make database still compatible
    //mongoDB surly has tools for this kind of actions its just too complex for me to figure out
    private static void autocorrect(){
        //creates document from updated class
        data.save(new PlayerD(),"pref");
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
            if(!currentDoc.containsKey(s)){
                for(Document d : getAllRawMeta()){
                    if(d.containsKey(s)) continue;
                    d.append(s, oldDoc.get(s));
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

    private static class AfkMaker {
        private static final int updateRate = 60;
        private static final int requiredTime = 1000*60*5;
        private AfkMaker(){
            Timer.schedule(()->{
                for(Player p : playerGroup){
                    PlayerD pd = getData(p);
                    if(pd.afk && Time.timeSinceMillis(pd.lastAction)<requiredTime){
                        pd.afk = false;
                        updateName(p,pd);
                        Tools.sendMessage("afk-is-not",pd.originalName,AFK);
                        return;
                    }
                    if (!pd.afk && Time.timeSinceMillis(pd.lastAction)>requiredTime){
                        pd.afk = true;
                        updateName(p,pd);
                        Tools.sendMessage("afk-is",pd.originalName,AFK);
                    }
                }
            },0,updateRate);
        }
    }
}
