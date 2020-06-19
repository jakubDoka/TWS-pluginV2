package theWorst;

import arc.Events;
import arc.math.Mathf;
import arc.struct.Array;
import arc.util.CommandHandler;
import arc.util.Strings;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import mindustry.game.Gamemode;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.maps.Map;
import theWorst.database.*;
import theWorst.helpers.MapD;
import theWorst.helpers.MapManager;
import theWorst.helpers.Tester;
import theWorst.votes.Vote;
import theWorst.votes.VoteData;

import java.util.ArrayList;
import java.util.Arrays;

import static mindustry.Vars.*;
import static theWorst.Tools.*;
import static theWorst.database.Database.getData;

public class InGameCommands {
    public static Vote vote = new Vote("vote-y-n");
    public static Vote voteKick = new Vote("vote-/vote-y-/vote-n");
    interface Command {
        VoteData run(String[] args, Player player);
    }

    Command mkgfCommand = (args,player)->{
        PlayerD pd = Database.findData(args[0]);
        if(pd == null){
            sendErrMessage(player, "player-not-found");
            return null;
        }
        if(pd.uuid.equals(player.uuid)){
            sendErrMessage(player,"mkgf-self");
            return null;
        }
        if(getRank(pd).isAdmin){
            sendErrMessage(player,"mkgf-target-admin");
            return null;
        }

        VoteData voteData = new VoteData(){
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

        if(player.isAdmin){
            voteData.run();
            return null;
        }
        /*if(playerGroup.size()<3){
            sendErrMessage(player,"mkgf-not-enough");
            return null;
        }*/
        return voteData;
    };



    public InGameCommands(CommandHandler handler) {

        Events.on(EventType.PlayerChatEvent.class,e->{
            getData(e.player).onAction(e.player);
            if(e.message.equalsIgnoreCase("y") || e.message.equalsIgnoreCase("n")) {
                if(vote.voting){
                    vote.addVote(e.player,e.message.toLowerCase());
                } else sendErrMessage(e.player,"vote-not-active");
            }
        });

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

        handler.<Player>register("rules","Shows rules of this server.",(args,player)->{

            String rules = Config.rules.getOrDefault(getCountryCode(getData(player).bundle.getLocale()),Config.rules.get("default"));
            if(rules == null){
                sendMessage(player,"There are no rules on this server.");
                return;
            }

            Call.onInfoMessage(player.con,"[orange]==RULES==[]\n\n"+rules);
        });

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
            if(args.length == 0){
                data = getData(player).toString();
            } else {
                if(!Strings.canParsePostiveInt(args[0])){
                    sendErrMessage(player,"refuse-not-integer","1");
                    return;
                }
                PlayerD pd = Database.findData(args[0]);
                if(pd == null){
                    sendErrMessage(player,"player-not-found");
                    return;
                }
                data = pd.toString();
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
                Tools.sendInfoPopup(player,"set-help");
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
                    String preview = Tools.smoothColors("Colors go brrrrr!!", args[1].split("/"));
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
            if(!Tools.enumContains(Setting.values(),args[0])){
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
                    map = Tools.findMap(args[1]);
                    if(map != null){
                        md = MapManager.getData(map);
                    }
                }
            }
            switch (args[0]){
                case "help":
                    Tools.sendInfoPopup(player,"map-help");
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
                            if(finalMap ==null){
                                Events.fire(new EventType.GameOverEvent(Team.crux));
                                return;
                            }

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
                            //todo launch all resources
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
                    Call.onInfoMessage(player.con, Tools.formPage(
                            MapManager.getMapList(),page,Tools.getTranslation(pd,"map-list"),20));
                    return;
                case "info":
                    if( md == null || map == null) {
                        sendErrMessage(player,"map-not-found");
                        return;
                    }
                    Call.onInfoMessage(player.con,md.toString(map,pd));
                    return;
                case "rate":
                    if(Tools.wrongArgAmount(player,args,3)) return;
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
            switch (Tools.setEmergencyViaCommand(args)) {
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
            ArrayList<String> res = Tools.search(args);
            if (res == null) {
                sendErrMessage(player, "search-invalid-mode",Arrays.toString(Stat.values()));
                return;
            }
            int showing = 40;
            StringBuilder mb = new StringBuilder();
            int size = res.size();
            int begin = Math.max(0,size-showing);
            for (int i = begin; i <size; i++) {
                mb.insert(0,Tools.cleanColors(res.get(i))+"\n");
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
                        sendErrMessage(player, "test-is-recent", Tools.secToTime(penalty));
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

        handler.<Player>register("ranks","<normal/special/info> <name/page>",
                "More info via /ranks help.", (args,player)->{
            PlayerD pd = Database.getData(player);
            ArrayList<String> res = new ArrayList<>();
            int page = args.length == 2 && Strings.canParsePostiveInt(args[1]) ? Integer.parseInt(args[1]) : 1;
            switch (args[0]){
                case "info":
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
            Call.onInfoMessage(player.con, Tools.formPage(res, page, args[0] + "ranks", 20));
        });

    }
}
