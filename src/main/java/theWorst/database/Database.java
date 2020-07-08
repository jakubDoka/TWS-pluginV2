package theWorst.database;

import arc.Events;
import arc.graphics.Color;
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
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import theWorst.Bot;
import theWorst.Global;
import theWorst.helpers.gameChangers.Pet;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static mindustry.Vars.netServer;
import static mindustry.Vars.playerGroup;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static theWorst.Tools.Commands.logInfo;
import static theWorst.Tools.Formatting.*;
import static theWorst.Tools.General.*;
import static theWorst.Tools.Json.loadJson;
import static theWorst.Tools.Json.saveJson;
import static theWorst.Tools.Players.sendErrMessage;
import static theWorst.Tools.Players.sendMessage;

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
        Events.on(EventType.PlayerConnect.class,e->{
            //remove fake ranks
            String originalName = e.player.name;
            e.player.name = cleanEmotes(e.player.name);
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
            if(subnet.contains(getSubnet(pd)) && getRank(pd) != Rank.griefer){
                setRank(pd, Rank.griefer, e.player);
                sendMessage("griefer-subnet",e.player.name);
            }
            //resolving special rank
            SpecialRank sr = getSpecialRank(pd);
            if(sr != null && !sr.isPermanent()){
                pd.specialRank = null;
                sr = null;
            }

            for(SpecialRank rank : ranks.values()){
                if(rank.condition(pd) && (sr==null || sr.value < rank.value)){
                   pd.specialRank = rank.name;
                   sr = rank;
                }
            }
            addPets(pd, sr);
            //modify name based of rank
            updateName(e.player,pd);
            sendMessage("player-connected",e.player.name,String.valueOf(pd.serverId));
            Bot.sendToLinkedChat(String.format("**%s** (ID:**%d**) hes connected.", cleanColors(e.player.name), pd.serverId));
            if (Bot.api == null || Bot.config.serverId == null || pd.rank.equals(Rank.griefer.name())) return;
            if (Bot.pendingLinks.containsKey(pd.serverId)){
                sendMessage(e.player,"discord-pending-link",Bot.pendingLinks.get(pd.serverId).name);
            }
            if (pd.discordLink == null) return;
            CompletableFuture<User> optionalUser = Bot.api.getUserById(pd.discordLink);
            Timer.schedule(new Timer.Task() {
                @Override
                public void run() {
                    if (!optionalUser.isDone()) return;
                    this.cancel();
                    try {
                        User user = optionalUser.get();
                        if (user == null) return;
                        Optional<Server> server = Bot.api.getServerById(Bot.config.serverId);
                        if (!server.isPresent()) return;
                        Rank finalR = null;
                        SpecialRank finalDl = null;
                        for (Role r : user.getRoles(server.get())) {
                            String roleName = r.getName();
                            if (enumContains(Rank.values(), roleName)) {
                                Rank rank = Rank.valueOf(roleName);
                                if (Rank.valueOf(pd.rank).getValue() < rank.getValue()) {
                                    finalR = rank;
                                }
                            }else if (ranks.containsKey(roleName)) {
                                SpecialRank dl = ranks.get(pd.donationLevel);
                                SpecialRank ndl = ranks.get(roleName);
                                if (pd.donationLevel == null || dl == null || dl.value < ndl.value) {
                                    pd.donationLevel = roleName;
                                    finalDl = ndl;
                                }
                            }
                        }
                        if (pd.donationLevel != null) {
                            updateName(e.player, pd);
                            addPets(pd, finalDl);
                        }
                        if (finalR == null) return;
                        setRank(pd, finalR, e.player);
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

    void addPets(PlayerD pd, SpecialRank sr){
        if(sr != null && sr.pets != null){
            for(String pet : sr.pets){
                Pet found = pets.get(pet);
                if(found == null){
                    Log.info("missing pet :" + pet);
                } else {
                    pd.pets.add(new Pet(found));
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
        JSONObject data = new JSONObject();
        JSONArray array = new JSONArray();
        for(String s : subnet){
            array.add(s);
        }
        data.put("subnet",array);
        saveJson(subnetFile,data.toJSONString());
    }

    public static void loadSubnet(){
        subnet.clear();
        loadJson(subnetFile,(data)->{
            for(Object o : (JSONArray) data.get("subnet")){
                subnet.add((String) o);
            }
        },Database::saveSubnet);
    }

    public static void loadRanks() {
        ranks.clear();
        loadJson(rankFile,data -> {
            ObjectMapper mapper = new ObjectMapper();
            SpecialRank[] srs = mapper.readValue(((JSONArray)data.get("ranks")).toJSONString(),SpecialRank[].class);
            for(SpecialRank sr : srs) ranks.put(sr.name,sr);
            boolean invalid = false;
            for(SpecialRank r : ranks.values()){
                if(r.quests != null) {
                    for(String s: r.quests.keySet()){
                        if(!enumContains(Stat.values(),s)){
                            logInfo("special-error-invalid-stat", r.name, s);
                            invalid = true;
                        }
                        for(String l : r.quests.get(s).keySet()){
                            if(!enumContains(SpecialRank.Mod.values(),l)){
                                logInfo("special-error-invalid-stat-property", r.name, s, l);
                                invalid = true;
                            }
                        }
                    }
                }
                if(r.linked != null){
                    for(String l : r.linked){
                        if(!ranks.containsKey(l)){
                            logInfo("special-rank-error-missing-rank" , l, r.name);
                            invalid = true;
                        }

                    }
                }
            }
            if(invalid){
                ranks.clear();
                logInfo("special-rank-file-invalid");
            }
        },Database::defaultRanks);
    }

    public static void defaultRanks(){
        HashMap<String, ArrayList<SpecialRank>> ranks = new HashMap<>();
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
        ranks.put("ranks", arr);
        try {
            saveJson(rankFile, new ObjectMapper().writeValueAsString(ranks));
            logInfo("files-default-config-created","special ranks", rankFile);
            loadRanks();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            logInfo("files-default-config-failed","special ranks", rankFile);
        }
    }

    public static void loadPets(){
        pets.clear();
        loadJson(petFile,data -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                Pet[] pets = mapper.readValue(((JSONArray)data.get("pets")).toJSONString(),Pet[].class);
                for(Pet p : pets)
                    Database.pets.put(p.name,p);
            } catch (IOException ex){
                ex.printStackTrace();
            }
        },Database::defaultPets);
    }

    public static void defaultPets(){
        try {
            String data = new ObjectMapper().writeValueAsString(new HashMap<String, ArrayList<Pet>>(){{
                put("pets",new ArrayList<Pet>(){{ add(new Pet()); }});
            }});
            saveJson(petFile, data);
            logInfo("files-default-config-created","pets", petFile);
            loadPets();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            logInfo("files-default-config-failed","pets", petFile);
        }
    }

    public static SpecialRank getSpecialRank(PlayerD pd) {
        if (pd.specialRank == null) return null;
        SpecialRank sr = ranks.get(pd.specialRank);
        if (sr == null) pd.specialRank = null;
        return sr;
    }

    public static SpecialRank getDonationLevel(PlayerD pd) {
        if (pd.donationLevel == null) return null;
        SpecialRank sr = ranks.get(pd.donationLevel);
        if (sr == null) pd.donationLevel = null;
        return sr;
    }

    public static void updateName(Player player,PlayerD pd){
        player.name=pd.originalName;
        if (pd.afk){
            player.name += AFK;
        } else if(pd.donationLevel != null) {
            SpecialRank rank = getDonationLevel(pd);
            if(rank == null){
                updateName(player,pd);
                return;
            }
            player.name += rank.getSuffix();
        } else if(pd.specialRank != null) {
            SpecialRank rank = getSpecialRank(pd);
            if(rank == null){
                updateName(player,pd);
                return;
            }
            player.name += rank.getSuffix();
        } else {
            player.name += getRank(pd).getSuffix();
        }
        //name changes for some reason removes admin tag so this is necessary
        player.isAdmin = getRank(pd).isAdmin;
    }

    static void updateMeta(PlayerD pd){
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
        PlayerD pd = getData(player);
        if(!pd.settings.contains(Setting.ability.name())) return false;
        SpecialRank sr = getSpecialRank(pd);
        SpecialRank dl = getDonationLevel(pd);
        boolean srh = false, dlh = false;
        if(sr != null) srh = sr.permissions.contains(perm.name());
        if(dl != null) dlh = dl.permissions.contains(perm.name());
        return  srh || dlh;
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
                        sendMessage("afk-is-not",pd.originalName,AFK);
                        return;
                    }
                    if (!pd.afk && Time.timeSinceMillis(pd.lastAction)>requiredTime){
                        pd.afk = true;
                        updateName(p,pd);
                        sendMessage("afk-is",pd.originalName,AFK);
                    }
                }
            },0,updateRate);
        }
    }
}
