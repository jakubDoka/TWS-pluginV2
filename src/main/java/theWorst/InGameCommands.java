package theWorst;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Array;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.entities.type.BaseUnit;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import mindustry.game.Gamemode;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.maps.Map;
import mindustry.type.Item;
import mindustry.type.ItemType;
import mindustry.type.UnitType;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import theWorst.Tools.Players;
import theWorst.database.*;
import theWorst.helpers.Hud;
import theWorst.helpers.MapD;
import theWorst.helpers.MapManager;
import theWorst.helpers.Tester;
import theWorst.helpers.gameChangers.*;
import theWorst.votes.Vote;
import theWorst.votes.VoteData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import static mindustry.Vars.*;
import static theWorst.Tools.Commands.*;
import static theWorst.Tools.Formatting.*;
import static theWorst.Tools.General.*;
import static theWorst.Tools.Maps.findMap;
import static theWorst.Tools.Maps.getFreeTiles;
import static theWorst.Tools.Players.*;
import static theWorst.database.Database.getData;


public class InGameCommands {
    public static Vote vote = new Vote("vote-y-n");
    public static Vote voteKick = new Vote("vote-/vote-y-/vote-n");
    public static Loadout loadout;
    public static Factory factory;
    public HashMap<String, String> dms = new HashMap<>();

    interface Command {
        VoteData run(String[] args, Player player);
    }


    public InGameCommands(){
        Events.on(EventType.PlayerChatEvent.class,e->{
            getData(e.player).onAction(e.player);
            if(e.message.equalsIgnoreCase("y") || e.message.equalsIgnoreCase("n")) {
                vote.addVote(e.player,e.message.toLowerCase());
            }
        });

        Events.on(EventType.PlayerLeave.class,e->{
            voteKick.revolve();
            vote.revolve();
        });

        Events.on(EventType.ServerLoadEvent.class, e->{
            loadout = new Loadout();
            factory = new Factory(loadout);
        });
    }

    private boolean handleHammer(Vote vote, PlayerD pd,Player player) {
        if(vote.voting && vote.voteData.target instanceof PlayerD && ((PlayerD)vote.voteData.target).uuid == pd.uuid){
            vote.addVote(player, "y");
            return true;
        }
        return false;
    }


    public void register(CommandHandler handler) {
        handler.removeCommand("help");

        handler.<Player>register("help","[page]","Shows all available commands and how to use them.",(args,player)->{
            ArrayList<String> res = new ArrayList<>();
            PlayerD pd = getData(player);
            for(CommandHandler.Command c : handler.getCommandList()){
                String paramKey = c.text + "-params";
                String descriptionKey = c.text + "-description";
                String params = cnaTranslate(pd, paramKey) ?  getTranslation(pd , paramKey) : c.paramText;
                String description = cnaTranslate(pd, descriptionKey) ?  getTranslation(pd , descriptionKey) : c.description;
                res.add(String.format("[orange]/%s[] - [gray]%s[] - %s", c.text, params, description));
            }
            int page = args.length == 1 && Strings.canParsePostiveInt(args[0]) ? Integer.parseInt(args[0]) : 1;
            player.sendMessage(formPage(res, page, "help", 7));
        });

        handler.<Player>register("status","Shows some information about server like fps.",(args, player)-> {
            int totalFreeSpace = getFreeTiles(false);
            int currentFreeSpace = getFreeTiles(true);
            sendInfoPopup(player, "server-status",
                    "" + (Core.graphics.getFramesPerSecond()),
                    "" + playerGroup.size(),
                    "" + Database.getDatabaseSize(),
                    "" + currentFreeSpace,
                    "" + totalFreeSpace,
                    "" + (int)( (float) currentFreeSpace / totalFreeSpace * 100));
        });

        handler.<Player>register("rules","Shows rules of this server.",(args,player)->{
            String rules = null;
            if(Global.config.rules != null){
                String dm = Global.config.rules.get("default");
                rules = Global.config.rules.getOrDefault(getCountryCode(getData(player).bundle.getLocale()),dm);
            }

            if(rules == null){
                sendErrMessage(player, "no-rules");
                return;
            }

            Call.onInfoMessage(player.con,"[orange]==RULES==[]\n\n"+rules);
        });

        Command mkgfCommand = (args,player)-> {
            PlayerD pd = Database.findData(cleanName(args[0]));
            if (pd == null) {
                sendErrMessage(player, "player-not-found");
                return null;
            }
            if (pd.uuid.equals(player.uuid)) {
                sendErrMessage(player, "mkgf-self");
                return null;
            }
            if (getRank(pd).isAdmin) {
                sendErrMessage(player, "mkgf-target-admin");
                return null;
            }

            VoteData voteData = new VoteData() {
                {
                    by = player;
                    target = pd;
                    reason = pd.rank.equals(Rank.griefer.name()) ? "mkgf-remove" : "mkgf-add";
                }

                @Override
                public void run() {
                    if (pd.rank.equals(Rank.griefer.name())) {
                        Database.setRank(pd, Rank.newcomer, null);
                    } else {
                        Database.setRank(pd, Rank.griefer, null);
                    }
                }
            };

            if (player.isAdmin) {
                voteData.run();
                sendMessage("mkgf-admin-marked", getData(player).originalName, pd.originalName);
                return null;
            }

            if(handleHammer(vote, pd, player) || handleHammer(voteKick, pd, player)){
                return null;
            }

            if (playerGroup.size() < 3) {
                sendErrMessage(player, "mkgf-not-enough");
                return null;
            }
            return voteData;
        };

        handler.<Player>register("mkgf","<name/id>","Opens vote for marking player a griefer.",(args,player)->{
            VoteData voteData = mkgfCommand.run(args,player);
            if(voteData==null) return;
            vote.aVote(voteData,5, ((PlayerD)voteData.target).originalName);
        });

        handler.<Player>register("voteKick","<name/id>","Opens vote for marking player a griefer.",(args,player)->{
            VoteData voteData = mkgfCommand.run(args,player);
            if(voteData==null) return;
            voteKick.aVote(voteData,5, ((PlayerD)voteData.target).originalName);
        });

        handler.<Player>register("info","[id]","Displays players profile.",(args, player)->{
            String data;
            PlayerD pd = getData(player);
            if(args.length == 0){
                data = pd.toString(pd);
            } else {
                if(!Strings.canParsePostiveInt(args[0])){
                    sendErrMessage(player,"refuse-not-integer","1");
                    return;
                }
                PlayerD other = Database.findData(args[0]);
                if(other == null){
                    sendErrMessage(player,"player-not-found");
                    return;
                }
                data = other.toString(pd);
            }
            Call.onInfoMessage(player.con,"[orange]==PLAYER PROFILE==[]\n\n"+data);
        });

        handler.<Player>register("set","[setting/help] [on/off/argument]",
                "/set to see setting options. /set help for more info.",(args,player)->{
            PlayerD pd = getData(player);

            if(args.length==0) {
                StringBuilder sb = new StringBuilder();
                for (Setting s : Setting.values()) {
                    sb.append(" ").append(s.name());
                }
                sendMessage(player, "setting");
                player.sendMessage(sb.toString());
                return;
            }
            if(args.length == 1){
                if(!args[0].equals("help")){
                    wrongArgAmount(player,args,2);
                    return;
                }
                sendInfoPopup(player,"set-help");
                return;
            }
            if(args[0].equals("textcolor")) {
                String[] colors = args[1].split("/");
                //this is color combo
                if (colors.length > 1) {
                    //checking if it has correct format
                    boolean invalid = false;
                    for (String s : colors) {
                        if (s.length() != 6) {
                            invalid = true;
                            break;
                        }
                        try {
                            Integer.parseInt(s, 16);
                        } catch (Exception ex) {
                            invalid = true;
                            break;
                        }
                    }
                    if (invalid) {
                        sendErrMessage(player, "set-color-invalid-format", args[1]);
                        return;
                    }
                    //checking if player has permission for this
                    SpecialRank sp = Database.getSpecialRank(pd);
                    if ((sp == null || !sp.permissions.contains(Perm.colorCombo.name()))) {
                        sendErrMessage(player, "set-color-no-perm");
                        return;
                    }
                    String preview = smoothColors("Colors go brrrrr!!", args[1].split("/"));
                    sendMessage(player, "set-color-preview", preview);
                } else {
                    //checking if player is verified
                    if (!Database.hasPerm(player, Perm.high)) {
                        sendErrMessage(player, "at-least-verified", Rank.verified.getName());
                        return;
                    }
                    sendMessage(player, "set-textColor", args[1]);
                }
                pd.textColor = args[1];
                return;
            }
            if(!enumContains(Setting.values(),args[0])){
                sendErrMessage(player,"set-invalid");
                return;
            }
            switch (args[1]){
                case "on" :
                    if(pd.settings.contains(args[0])){
                        sendErrMessage(player, "set-already-enabled");
                        return;
                    }
                    pd.settings.add(args[0]);
                    break;
                case "off" :
                    if(!pd.settings.contains(args[0])){
                        sendErrMessage(player,"set-already-disabled");
                        return;
                    }
                    pd.settings.remove(args[0]);
                    break;
                default:
                    sendErrMessage(player,"no-such-option",args[1],"on/off");
            }
            sendMessage(player,"set-toggled",args[0],args[1]);
        });

        handler.<Player>register("mute","<name/id>","Mutes or, if muted, unmutes player for you. " +
                "It can be used only on online players.",(args,player)->{
            PlayerD pd = getData(player);
            Player other = findPlayer(args[0]);

            if(other == null){
                sendErrMessage(player,"player-not-found");
                return;
            }
            if(other == player){
                sendErrMessage(player,"mute-self");
                return;
            }

            if(pd.mutes.contains(other.uuid)){
                pd.mutes.remove(other.uuid);
                sendMessage(player,"mute-un",other.name);
            } else {
                pd.mutes.add(other.uuid);
                sendMessage(player,"mute",other.name);
            }
        });

        handler.<Player>register("link","<pin/refuse>",
                "Link your account with discord if you have the pin, or refuse link attempt.",(args,player)->{
            PlayerD pd = getData(player);
            if(!Bot.pendingLinks.containsKey(pd.serverId)){
                sendErrMessage(player,"link-no-request");
                return;
            }
            if(args[0].equals("refuse")){
                Bot.pendingLinks.remove(pd.serverId);
                sendMessage(player,"link-request-refuse");
                return;
            }
            if(args[0].equals(Bot.pendingLinks.get(pd.serverId).pin)){
                pd.discordLink = Bot.pendingLinks.remove(pd.serverId).id;
                sendMessage(player,"link-success");
                return;
            }
            sendErrMessage(player,"link-wrong-pin");
        });

        handler.<Player>register("setrank","<name/id> <rank/restart> [reason...]","Command for admins.",(args,player)->{
            if(!player.isAdmin){
                sendErrMessage(player,"refuse-not-admin");
                return;
            }
            switch (setRankViaCommand(player,args[0],args[1],args.length==3 ? args[2] : null)){
                case notFound:
                    sendErrMessage(player,"player-not-found");
                    break;
                case invalid:
                    sendErrMessage(player,"rank-not-found");
                    sendMessage(player,"rank-s",Arrays.toString(Rank.values()));
                    sendMessage(player,"rank-s-custom",Database.ranks.keySet().toString());
                    break;
                case notPermitted:
                    sendErrMessage(player,"rank-using-admin-rank");
            }
        });

        handler.<Player>register("map","<help/change/restart/gameover/list/info/rate> [name/idx/this] [1-10]",
                "More info via /map help",(args,player)->{
            VoteData voteData;
            String what = "map-restart";
            PlayerD pd = getData(player);
            Map map = world.getMap();
            MapD md = MapManager.played;
            if(args.length>1){
                if(!args[1].equalsIgnoreCase("this")) {
                    map = findMap(args[1]);
                    if(map != null){
                        md = MapManager.getData(map);
                    }
                }
            }
            switch (args[0]){
                case "help":
                    sendInfoPopup(player,"map-help");
                    return;
                case "change":
                    what = "map-change";
                    if(wrongArgAmount(player,args,2)) return;
                    if (map == null){
                        sendErrMessage(player,"map-not-found");
                        return;
                    }
                case "restart":
                    String finalWhat = what;
                    Map finalMap = map;
                    voteData = new VoteData() {
                        {
                            by = player;
                            target = finalMap;
                            reason = finalWhat;
                        }
                        @Override
                        public void run() {
                            loadout.launchAll();
                            Array<Player> players = new Array<>();
                            for(Player player : playerGroup.all()) {
                                players.add(player);
                                player.setDead(true);
                            }
                            logic.reset();
                            Call.onWorldDataBegin();
                            world.loadMap(finalMap, finalMap.applyRules(Gamemode.survival));
                            state.rules = world.getMap().applyRules(Gamemode.survival);
                            logic.play();
                            for(Player player : players){
                                if(player.con == null) continue;

                                player.reset();
                                netServer.sendWorldData(player);
                            }
                        }
                    };
                    break;
                case "gameover":
                    voteData = new VoteData() {
                        {
                            by = player;
                            reason = "map-gameover";
                        }
                        @Override
                        public void run() {
                            Events.fire(new EventType.GameOverEvent(Team.crux));
                            loadout.launchAll();
                        }
                    };
                    break;
                case "list":
                    int page = 1;
                    if(args.length>1 ){
                        if(!Strings.canParsePostiveInt(args[1])){
                            sendErrMessage(player,"refuse-not-integer","2");
                            return;
                        }
                        page = Integer.parseInt(args[1]);
                    }
                    Call.onInfoMessage(player.con, formPage(
                            MapManager.getMapList(),page,getTranslation(pd,"map-list"),20));
                    return;
                case "info":
                    if( md == null || map == null) {
                        sendErrMessage(player,"map-not-found");
                        return;
                    }
                    Call.onInfoMessage(player.con,md.toString(map,pd));
                    return;
                case "rate":
                    if(wrongArgAmount(player,args,3)) return;
                    if(!Strings.canParsePostiveInt(args[2])){
                        sendErrMessage(player,"refuse-not-integer","3");
                        return;
                    }
                    if(md == null){
                        sendErrMessage(player,"map-not-found");
                        return;
                    }
                    byte rating = (byte) Mathf.clamp(Byte.parseByte(args[2]),1,10);
                    md.ratings.put(player.uuid, rating);
                    md.save();
                    sendMessage(player,"map-rate",md.name,
                            "["+(rating<6 ? rating<3 ? "scarlet":"yellow":"green") + "]" + rating + "[]");
                    return;
                default:
                    sendErrMessage(player,"invalid-mode");
                    return;
            }
            vote.aVote(voteData,10,voteData.target != null ? ((Map)voteData.target).name() : "");
        });

        handler.<Player>register("emergency","<time/permanent/stop>","Emergency control.",(args,player)->{
            if(!player.isAdmin){
                sendErrMessage(player,"refuse-not-admin");
                return;
            }
            switch (setEmergencyViaCommand(args)) {
                case success:
                    sendMessage(player,"emergency-started");
                    break;
                case stopSuccess:
                    sendMessage(player,"emergency-stopped");
                    break;
                case invalid:
                    sendErrMessage(player,"emergency-ongoing");
                    break;
                case invalidStop:
                    sendErrMessage(player,"emergency-cannot-stop");
                    break;
                case invalidNotInteger:
                    sendErrMessage(player,"refuse-not-integer","1");
                    break;
                case permanentSuccess:
                    sendMessage(player,"emergency-permanent-started");
            }
        });

        handler.<Player>register("search","<searchKey/sort/rank> [sortType/rankName] [reverse]","Shows first 40 results of search.",(args,player)->{
            ArrayList<String> res = search(args);
            if (res == null) {
                sendErrMessage(player, "search-invalid-mode",Arrays.toString(Stat.values()));
                return;
            }
            int showing = 40;
            StringBuilder mb = new StringBuilder();
            int size = res.size();
            int begin = Math.max(0,size-showing);
            for (int i = begin; i <size; i++) {
                mb.insert(0,cleanColors(res.get(i))+"\n");
            }
            if (res.isEmpty()) {
                sendErrMessage(player, "search-no-results");
            } else {
                player.sendMessage(mb.toString());
                if(size>showing){
                    sendMessage(player, "search-show-report", String.valueOf(size));
                }
            }
        });

        handler.<Player>register("test","<start/egan/help/answer>","More info via /test help.", (args,player)-> {
            Integer penalty = Tester.recent.contains(player);
            PlayerD pd = Database.getData(player);
            boolean isTested = Tester.tests.containsKey(player.uuid);
            switch (args[0]) {
                case "help":
                    sendInfoPopup(player, "test-help");
                    return;
                case "start":
                    if (pd.rank.equals(Rank.griefer.name())) {
                        sendErrMessage(player, "griefer-no-perm");
                        return;
                    }
                    if (Database.hasPerm(player, Perm.high)) {
                        sendErrMessage(player, "test-no-need", Rank.verified.getName());
                        return;
                    }
                    if (penalty != null) {
                        sendErrMessage(player, "test-is-recent", secToTime(penalty));
                        return;
                    }
                    if (isTested) {
                        sendErrMessage(player, "test-already-testing");
                        return;
                    }
                    sendMessage(player, "test-starting");
                    Tester.tests.put(player.uuid, new Tester.Test(player));
                    return;
                case "egan":
                    if (!isTested) {
                        sendErrMessage(player, "test-not-tested");
                        return;
                    }
                    Tester.tests.get(player.uuid).ask(player);
                    return;
                default:
                    if (!isTested) {
                        sendErrMessage(player, "test-not-tested");
                        return;
                    }
                    if (!Strings.canParseInt(args[0])) {
                        sendErrMessage(player, "refuse-not-integer", "1");
                        return;
                    }
                    sendMessage(player, "test-processing");
                    Tester.tests.get(player.uuid).processAnswer(player, Integer.parseInt(args[0]) - 1);
            }
        });

        /*handler.<Player>register("drop","<help/block/meteor/bomb> [blockName/material]",
                "More info via /drop help.", (args,player)->{
            switch (args[0]){
                case "help":
                    return;
                case "block":
                    if(Terraformer.buildBlock(player.getTeam(), Terraformer.getBlockByName(args[1], true), player.tileOn())){

                        player.sendMessage("succes");
                    }
                    return;
                case "meteor":
                    if(!args[1].startsWith("ore")) {

                        return;
                    }
                    Terraformer.dropMeteor(Terraformer.getBlockByName(args[1] , false), player.tileOn(), 100);
                case "bomb":
                    Terraformer.dropMeteor(null, player.tileOn(), 100);
                default:
                    sendErrMessage(player,"invalid-mode");
            }
        });*/

        handler.<Player>register("ranks","<help/normal/special/info> [name/page]",
                "More info via /ranks help.", (args,player)->{
            PlayerD pd = Database.getData(player);
            ArrayList<String> res = new ArrayList<>();
            int page = args.length == 2 && Strings.canParsePostiveInt(args[1]) ? Integer.parseInt(args[1]) : 1;
            switch (args[0]){
                case "help":
                    sendInfoPopup(player, "ranks-help");
                    return;
                case "info":
                    if(wrongArgAmount(player,args,2)) return;
                    SpecialRank sr = Database.ranks.get(args[1]);
                    if(sr == null){
                        sendErrMessage(player,"ranks-rank-not-exist");
                        return;
                    }
                    Call.onInfoMessage(player.con, sr.getDescription(pd));
                    return;
                case "normal":
                    for(Rank r : Rank.values()){
                        res.add(r.getName());
                    }
                    break;
                case "special":
                    for(SpecialRank s : Database.ranks.values()){
                        res.add(s.getSuffix());
                    }
                    break;
                default:
                    sendErrMessage(player,"invalid-mode");
                    return;
            }
            Call.onInfoMessage(player.con, formPage(res, page, args[0] + " ranks", 20));
        });

        handler.<Player>register("l","<put/get/info/help> [amount] [item/all] [condition]","More info via /l help.", (args, player)-> {
            if (args.length == 1) {
                switch (args[0]){
                    case "info":
                        Call.onInfoMessage(player.con, loadout.info());
                        return;
                    case "help":
                        StringBuilder sb = new StringBuilder();
                        for(Item i : Vars.content.items()){
                            if(i.type != ItemType.material) continue;
                            sb.append("[#").append(i.color).append("]").append(i.name).append("[] ");
                        }
                        sendInfoPopup(player,"loadout-help", sb.toString());
                        return;
                    default:
                        sendErrMessage(player, "invalid-mode");
                }
            } else if (args.length >= 3) {
                VoteData data;
                CoreBlock.CoreEntity core = getCore();
                if(core == null){
                    sendErrMessage(player,"loadout-no-cores");
                    return;
                }
                if(!Strings.canParsePostiveInt(args[1])){
                    sendErrMessage(player, "refuse-not-integer","2");
                    return;
                }
                int amount = Integer.parseInt(args[1]);
                Item item = Loadout.getItemByName(args[2]);
                ItemStack stack = null;
                String arg;
                switch (args[0]) {
                    case "fill":
                    case "put":
                        int condition = 0;
                        ArrayList<ItemStack> stacks = new ArrayList<>();
                        if(args[2].equals("all")){

                            if(args.length == 4){
                                if(!Strings.canParsePostiveInt(args[3])){
                                    sendErrMessage(player, "refuse-not-integer", "4");
                                    return;
                                }
                                condition = Integer.parseInt(args[3]);
                            }
                            arg = args[1] + " of all resources";
                            int total = 0;
                            for(Item i : content.items()){
                                if(i.type != ItemType.material) continue;
                                int am = core.items.get(i);
                                if (am < condition) continue;
                                total += am;
                                stacks.add(new ItemStack(i, Mathf.clamp(amount, 0 ,am)));
                            }
                            if(total == 0){
                                sendMessage(player,"loadout-nothing-to-transport");
                                return;
                            }
                        } else if( item == null){
                            sendErrMessage(player, "loadout-item-not-found", args[2]);
                            return;
                        } else {
                            stack = new ItemStack(item, Mathf.clamp(amount, 0, core.items.get(item)));
                            if(stack.amount == 0){
                                sendErrMessage(player,"loadout-nothing-to-transport");
                                return;
                            }
                            arg = Loadout.stackToString(stack);
                        }
                        if(condition != 0) {
                            Hud.addAd("loadout-condition", 10 ,"" + condition, "!gray", "!white");
                        }
                        ItemStack finalStack = stack;
                        data = new VoteData() {
                            {
                                reason = "loadout-put";
                            }
                            @Override
                            public void run() {
                                if(finalStack != null){
                                    ItemStack transported = loadout.canAdd(finalStack);
                                    core.items.remove(transported.item, transported.amount);
                                    loadout.add(transported);
                                } else {
                                    for(ItemStack s : loadout.canAdd(stacks)){
                                        core.items.remove(s.item, s.amount);
                                        loadout.add(s);
                                    }
                                }
                            }
                        };
                        break;
                    case "use":
                    case "get":
                        if (loadout.ships.size() == Loadout.config.shipCount) {
                            sendErrMessage(player, "loadout-no-ships");
                            return;
                        }
                        if (args[2].equals("all")){
                            sendErrMessage(player, "loadout-cannot-all");
                            return;
                        } if( item == null){
                            sendErrMessage(player, "loadout-item-not-found", args[2]);
                            return;
                        } else {
                            stack = new ItemStack(item, Mathf.clamp(amount, 0, loadout.getAmount(item)));
                            int shipSpace = (Loadout.config.shipCount - loadout.ships.size()) * Loadout.config.shipCapacity;
                            stack.amount = Mathf.clamp(stack.amount, 0, shipSpace);
                            if(stack.amount == 0){
                                sendErrMessage(player,"loadout-nothing-to-transport");
                                return;
                            }
                            arg = Loadout.stackToString(stack);
                        }
                        finalStack = stack;
                        data = new VoteData() {
                            {
                                reason = "loadout-get";
                            }
                            @Override
                            public void run() {
                                int ships = Mathf.ceil((float) finalStack.amount / Loadout.config.shipCapacity);
                                ships = Mathf.clamp(ships, 1, Loadout.config.shipCount - loadout.ships.size());
                                loadout.withdraw(finalStack);
                                for(int i = 0; i < ships; i++){
                                    int finalAmount = Mathf.clamp(finalStack.amount, 0, Loadout.config.shipCapacity);
                                    finalStack.amount -= finalAmount;
                                    loadout.ships.add(new Loadout.Ship() {
                                        {
                                            time = Loadout.config.shipSpeed;
                                            stack = new ItemStack(finalStack.item, finalAmount);
                                        }
                                        @Override
                                        public void onArrival() {
                                            core.items.add(stack.item, stack.amount);
                                            sendMessage("loadout-ship-arrived", arg);
                                        }
                                    });
                                }
                            }
                        };
                        break;
                    default:
                        sendErrMessage(player, "invalid-mode");
                        return;
                }
                data.by = player;
                data.target = stack;
                if(Database.hasSpecialPerm(player, Perm.loadout)) {
                    data.run();
                    Hud.addAd("loadout-player-launch", 10, player.name, "!gray");
                    return;
                }
                vote.aVote(data, 3, arg, secToTime(Loadout.config.shipSpeed));
            } else {
                wrongArgAmount(player, args, 3);
            }
        });

        handler.<Player>register("f","<help/build/call/priceof/info> [amount] [unit]","More info via /f help.",(args, player)->{
            if(args.length == 1){
                switch (args[0]) {
                    case "help":
                        sendInfoPopup(player, "factory-help");
                        return;
                    case "info":
                        Call.onInfoMessage(player.con, factory.info());
                        return;
                    default:
                        sendErrMessage(player, "invalid-mode");
                }
            } else if(args.length == 3){
                VoteData data;
                if(!Strings.canParsePostiveInt(args[1])){
                    sendErrMessage(player, "refuse-not-integer", "2");
                    return;
                }
                int amount = Integer.parseInt(args[1]);
                UnitType unit = Factory.getUnitByName(args[2]);
                if(unit == null || !Factory.config.prices.containsKey(unit)){
                    sendErrMessage(player, "factory-does-not-have-this", args[2]);
                    return;
                }
                UnitStack unitStack = new UnitStack(unit, amount);
                String arg;
                String arg2 = "";
                switch (args[0]) {
                    case "priceof":
                        Call.onInfoMessage(player.con,factory.price(unitStack));
                        return;
                    case "build":
                        UnitStack affordable = factory.canAfford(unitStack.unit);
                        if (affordable.amount == 0) {
                            sendErrMessage(player, "factory-cannot-afford");
                            return;
                        }
                        unitStack.amount = Math.min(affordable.amount, unitStack.amount);
                        arg = unitStack.toString();
                        data = new VoteData() {
                            {
                                reason = "factory-build";
                            }

                            @Override
                            public void run() {
                                factory.build(unitStack);
                                factory.threads.add(new Factory.Thread() {
                                    {
                                        stack = unitStack;
                                        time = (int) (Factory.config.prices.get(unit).buildTime * unitStack.amount);
                                        building = true;
                                    }

                                    @Override
                                    public void onFinish() {
                                        factory.add(stack);
                                        sendMessage("factory-build-finish", stack.toString());
                                    }
                                });
                            }
                        };
                        break;
                    case "call":
                        Tile tile = world.tile((int)player.x/8,(int)player.y/8);
                        if(tile == null || tile.solid()){
                            sendErrMessage(player, "factory-cannot-drop-units");
                            return;
                        }
                        arg2 = tile.x + "," + tile.x;
                        UnitStack available = factory.canWithdraw(unitStack);
                        if(available.amount == 0){
                            sendErrMessage(player, "factory-no-unis-available");
                            return;
                        }
                        UnitStack transportable = new UnitStack(unit,0);
                        FactoryConfig.PriceData priceData = Factory.config.prices.get(unit);
                        int space = (Factory.config.shipCount - factory.threads.size());
                        for(int i = 0; i < space; i++){
                            int total = 0;
                            while (true) {
                                total += priceData.size;
                                if(total > Factory.config.shipCapacity){
                                    break;
                                }
                                transportable.amount++;
                            }
                        }
                        transportable.amount = Mathf.clamp(transportable.amount, 0, available.amount);
                        arg = transportable.toString();
                        factory.withdraw(transportable);
                        String finalArg = arg;
                        String finalArg1 = arg2;
                        Vec2 aPos = new Vec2(player.x, player.y);
                        data = new VoteData() {
                            {
                                reason = "factory-call";
                            }
                            @Override
                            public void run() {
                                int usedThreads = Mathf.ceil((float)transportable.amount * priceData.size / Factory.config.shipCapacity);
                                for( int i = 0; i< usedThreads; i++){
                                    int perShip = Mathf.clamp(transportable.amount , 0, Factory.config.shipCapacity / priceData.size);
                                    UnitStack fStack = new UnitStack(unit, perShip);
                                    transportable.amount -= perShip;
                                    factory.threads.add(new Factory.Thread() {
                                        {
                                            stack = fStack;
                                            time = Factory.config.shipSpeed;
                                            building = false;
                                            pos = aPos;
                                        }
                                        @Override
                                        public void onFinish() {
                                            for(int i = 0; i < stack.amount; i++){
                                                BaseUnit bu = stack.unit.create(player.getTeam());
                                                bu.set(pos.x, pos.y);
                                                bu.add();
                                            }
                                            sendMessage("factory-units-arrived", finalArg, finalArg1);
                                        }
                                    });
                                }
                            }
                        };
                        break;
                    default:
                        sendErrMessage(player,"invalid-mode");
                        return;
                }
                data.target = unitStack;
                data.by = player;
                if(Database.hasSpecialPerm(player, Perm.factory)) {
                    data.run();
                    Hud.addAd("factory-player-launch", 10, player.name, "!gray");
                    return;
                }
                vote.aVote(data, 3, arg, arg2);
            } else {
                wrongArgAmount(player, args, 3);
            }
        });

        handler.<Player>register("kickafk", "Kicks all afk players",(args, player)->{
            vote.aVote(new VoteData() {
                {
                    reason = "kickafk";
                    by = player;
                }
                @Override
                public void run() {
                    for(Player p :playerGroup){
                        if(getData(p).afk) {
                            kick(p, "kickafk-kick", 0);
                        }
                    }
                }
            }, 4);
        });

        handler.<Player>register("skipwave", "[1-5]", "Skips amount of waves.",(args, player)->{
            if(args.length == 1 && !Strings.canParsePostiveInt(args[0])){
                sendErrMessage(player, "refuse-not-integer", "1");
                return;
            }
            int amount = args.length == 1 ? Integer.parseInt(args[0]) : 1;
            amount = Mathf.clamp(amount, 1, 5);
            int finalAmount = amount;
            vote.aVote(new VoteData() {
                {
                    by = player;
                    reason = "skipwave";
                }
                @Override
                public void run() {

                    for(int i = 0; i < finalAmount; i++) {
                        logic.runWave();
                    }
                }
            }, 6, "" + amount);
        });

        handler.removeCommand("vote");
        handler.<Player>register("vote", "<y/n>", "One way of voting.",(args, player)->{
            switch (args[0]) {
                case "y":
                case "n":
                    voteKick.addVote(player, args[0]);
                    break;
                default:
                    sendErrMessage(player, "invalid-mode");
            }
        });

        handler.<Player>register("suicide","Kill your self.",(arg, player) -> {
            if(!Database.hasSpecialPerm(player,Perm.suicide)){
                sendErrMessage(player, "suicide-no-perm");
                return;
            }
            player.onDeath();
            player.kill();
            sendMessage(Mathf.random(100) < 2 ? "suicide-committed-special" : "suicide-committed", player.name);
        });

        handler.<Player>register("dm","<player/id/help/message...>","More info via /dm help.",(args,player)->{
            if(args[0].equals("help")) {
                sendMessage(player, "dm-help");
            } else if(args[0].startsWith("!")){
                Player found = findPlayer(args[0].substring(1));
                if(found == null){
                    sendErrMessage(player, "player-not-found");
                    return;
                }
                dms.put(player.uuid, found.uuid);
                sendMessage(player, "dm-channel-set", player.name);
            } else {
                if(dms.containsKey(player.uuid)) {
                    sendErrMessage(player, "dm-no-channel-set");
                    return;
                }
                Player found = playerGroup.find(p -> p.uuid.equals(dms.get(player.uuid)));
                if(found == null){
                    sendErrMessage(player, "dm-target-offline");
                    return;
                }
                player.sendMessage("[#ffdfba][[[#" + found.color + "]" + found.name + "[]]:[white]" + args[0]);
                found.sendMessage("[#ffdfba][[[#" + found.color + "]" + player.name + "[]]:[white]" + args[0]);
            }

        });
    }
}
