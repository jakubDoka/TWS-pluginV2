package theWorst;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Array;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import com.mongodb.client.model.Filters;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.type.BaseUnit;
import mindustry.entities.type.Player;
import mindustry.entities.type.TileEntity;
import mindustry.game.EventType;
import mindustry.game.Gamemode;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.maps.Map;
import mindustry.type.Item;
import mindustry.type.ItemType;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import org.bson.Document;
import org.javacord.api.entity.user.User;
import theWorst.helpers.maps.MDoc;
import theWorst.helpers.maps.MapManager;
import theWorst.tools.Formatting;
import theWorst.database.*;
import theWorst.helpers.*;
import theWorst.helpers.gameChangers.*;
import theWorst.tools.General;
import theWorst.tools.Players;
import theWorst.votes.Vote;
import theWorst.votes.VoteData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import static mindustry.Vars.*;
import static theWorst.database.Database.online;
import static theWorst.tools.Commands.*;
import static theWorst.tools.Formatting.*;
import static theWorst.tools.General.*;
import static theWorst.tools.Maps.findMap;
import static theWorst.tools.Maps.getFreeTiles;
import static theWorst.tools.Players.*;
import static theWorst.database.Database.getData;


public class InGameCommands {
    public Administration.RecentMap spammers = new Administration.RecentMap(null){
        @Override
        public long getPenalty() {
            return 10000;
        }
    };



    public static Vote vote = new Vote("vote-y-n");
    public static Vote voteKick = new Vote("vote-/vote-y-/vote-n");
    public static Loadout loadout;
    public static Factory factory;
    public HashMap<String, String> dms = new HashMap<>();
    HashMap<Long, String> passwordConfirm = new HashMap<>();

    interface Command {
        VoteData run(String[] args, Player player);
    }


    public InGameCommands(){
        Vote.loadPassive();
        Events.on(EventType.PlayerChatEvent.class,e->{
            getData(e.player).onAction();
            if(e.message.equalsIgnoreCase("y") || e.message.equalsIgnoreCase("n")) {
                vote.addVote(e.player,e.message.toLowerCase());
            }
        });

        Events.on(EventType.PlayerLeave.class,e->{
            voteKick.resolve();
            vote.resolve();
        });

        Events.on(EventType.ServerLoadEvent.class, e->{
            loadout = new Loadout();
            factory = new Factory(loadout);
        });
    }

    private boolean handleHammer(Vote vote, Doc doc, Player player) {
        if(vote.voting && vote.voteData.target instanceof Doc && ((Doc) vote.voteData.target).getUuid().equals(doc.getUuid())){
            vote.addVote(player, "y");
            return true;
        }
        return false;
    }


    public void register(CommandHandler handler) {
        handler.removeCommand("help");
        handler.removeCommand("t");

        handler.<Player>register("login", "[ServerID/new] [password]", "Logs you in, so you can play normally.", (args, player)->{
            PD pd = getData(player);
            if(pd.rank == Ranks.griefer) {
                sendErrMessage(player, "griefer-no-perm");
                return;
            }

            if (args.length == 0) {
                sendInfoPopup(player, "login-help", Database.data.getSuggestions(player.uuid, player.con.address));
                return;
            }
            boolean abandon = args[0].equals("abandon");
            if (args[0].equals("new") || abandon){
                if(!abandon && (!pd.paralyzed && !pd.getDoc().isProtected())){
                    sendErrMessage(player, "login-abandon");
                    return;
                }
                Database.data.MakeNewAccount(player);
                Database.disconnectAccount(pd);
                online.put(player.uuid,Database.data.LoadData(player));
                sendMessage(player, "login-new");
                return;
            } else if(args.length == 1) {
                wrongArgAmount(player, args, 2);
                return;
            }
            if(!Strings.canParsePostiveInt(args[0])){
                sendErrMessage(player, "refuse-not-integer", "1");
                return;
            }

            long id = Long.parseLong(args[0]);
            Doc doc = Database.data.getDoc(id);
            if(doc == null) {
                sendErrMessage(player, "login-not-found");
                return;
            }

            Object password = doc.getPassword();
            if(!player.con.address.equals(doc.getIp()) && !player.uuid.equals(doc.getUuid()) && password == null) {
                sendErrMessage(player, "login-invalid");
            } else if(password == null || password.equals(hash(args[1])) || password.equals(hash2(args[1]))){
                Database.disconnectAccount(pd);
                Database.reLogPlayer(player, id);
            } else {
                sendErrMessage(player, "login-password-incorrect");
            }
        });

        handler.<Player>register("protect", "<password>", "Protect your account with password",(args, player)-> {
            PD pd = getData(player);
            if(pd.isGriefer()){
                sendErrMessage(player, "griefer-no-perm");
            }
            String password = passwordConfirm.get(pd.id);
            String hashed = hash2(args[0]);
            if (password != null) {
                if (password.equals(args[0])) {
                    sendMessage(player, "protect");
                    Database.data.set(pd.id, "password", hashed);
                    passwordConfirm.remove(pd.id);
                    Database.data.bind(player, pd.id);
                    return;
                }
                sendErrMessage(player, "protect-confirm-failed");
                passwordConfirm.remove(pd.id);
                return;
            }
            Object current = pd.getDoc().getPassword();
            if(current != null) {
                if(current.equals(hashed) || current.equals(hash(args[0]))) {
                    Database.data.remove(pd.id, "password");
                    sendMessage(player, "protect-protection-deleted");
                    return;
                }
                sendErrMessage(player, "protect-already-protected");
                return;
            }
            if (args[0].length() < 8) {
                sendErrMessage(player, "protect-too-short");
                return;
            }
            if (args[0].toLowerCase().equals(args[0])) {
                sendErrMessage(player, "protect-missing-capital");
                return;
            }
            if (Formatting.hasNoDigit(args[0])) {
                sendErrMessage(player, "protect-missing-digit");
                return;
            }
            sendMessage(player, "protect-confirm");
            passwordConfirm.put(pd.id, args[0]);
        });
        //todo test
        handler.<Player>register("report", "<id> <reason...>", "Report the griefer to admins. Usage is limited to prevent spam.", (args, player)->{
            Doc doc = getData(player).getDoc();
            switch (ReportPlayer(doc, null, args[1], args[0], doc.getId())){
                case invalidNotInteger:
                    sendErrMessage(player, "refuse-not-integer", "1");
                    break;
                case notFound:
                    sendErrMessage(player, "player-not-found");
                    break;
                case invalid:
                    sendErrMessage(player, "report-disabled");
                    break;
                case notPermitted:
                    sendErrMessage(player, "report-penalty", milsToTime(Global.limits.reportPenalty));
                    break;
                case adminReport:
                    sendErrMessage(player, "report-admin");
                    break;
                case grieferReport:
                    sendErrMessage(player, "report-already-marked");
                    break;
                case success:
                    sendMessage(player, "report-reported");
            }
        });

        handler.<Player>register("t", "<text...>", "This command straight up bans you from server, don't use it.",(args, player)->{
            Long pen = spammers.contains(player.uuid);
            if (pen != null && pen > 0){
                netServer.admins.addSubnetBan(getSubnet(player.con.address));
                player.con.kick("Sorry but we don't need spammers here");
                spammers.remove(player.uuid);
                return;
            }
            spammers.add(player.uuid);
            sendErrMessage(player, "t-stop-spamming");
        });

        handler.<Player>register("help","[page]","Shows all available commands and how to use them.",(args,player)->{
            ArrayList<String> res = new ArrayList<>();
            PD pd = getData(player);
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
                    "" + (int)( (float) currentFreeSpace / totalFreeSpace * 100),
                    "" + Database.data.count(Filters.eq("rank", "griefer")),
                    "" + Database.data.count(Filters.eq("rank", "verified")));
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
		
		handler.<Player>register("guide","If you are not confident in passing the test, take a look at this guide.",(args,player)->{
            String guide = null;
            if(Global.config.guide != null){
                String dm = Global.config.guide.get("default");
                guide = Global.config.guide.getOrDefault(getCountryCode(getData(player).bundle.getLocale()),dm);
            }

            if(guide == null){
                sendErrMessage(player, "no-guide");
                return;
            }

            Call.onInfoMessage(player.con,"[orange]==GUIDE==[]\n\n"+guide);
        });

        Command mkgfCommand = (args,player)-> {
            Doc doc = Database.findData(cleanName(args[0]));

            if (doc == null) {
                sendErrMessage(player, "player-not-found");
                player.sendMessage(getPlayerList());
                return null;
            }

            if(handleHammer(vote, doc, player) || handleHammer(voteKick, doc, player)){
                return null;
            }

            if (doc.getUuid().equals(player.uuid)) {
                sendErrMessage(player, "mkgf-self");
                return null;
            }

            if (doc.isAdmin()) {
                sendErrMessage(player, "mkgf-target-admin");
                return null;
            }

            if (doc.isGriefer() && !getData(player).hasThisPerm(Perm.antiGrief)){
                sendErrMessage(player, "mkgf-not-permitted");
            }
            Rank prev = doc.getRank(RankType.rank);
            boolean isGriefer = prev == Ranks.griefer;
            VoteData voteData = new VoteData() {
                {
                    special = Perm.antiGrief;
                    by = player;
                    target = doc;
                    reason = isGriefer ? "mkgf-remove" : "mkgf-add";
                }

                @Override
                public void run() {
                    Rank nw = Ranks.newcomer;
                    if (!isGriefer) {
                        nw = Ranks.griefer;
                    }
                    Database.setRank(doc.getId(), nw);
                    Bot.onRankChange(doc.getName(), doc.getId(), prev.name, nw.name, cleanName(by.name), "mkgf");

                }
            };

            if (player.isAdmin) {
                voteData.run();
                sendMessage(isGriefer ?  "mkgf-admin-removed" : "mkgf-admin-marked" , getData(player).name, doc.getName());
                return null;
            }

            if (playerGroup.size() < 3) {
                sendErrMessage(player, "mkgf-not-enough");
                return null;
            }
            return voteData;
        };

        handler.<Player>register("mkgf","<name/id...>","Opens vote for marking player a griefer.",(args,player)->{
            VoteData voteData = mkgfCommand.run(args,player);
            if(voteData==null) return;
            vote.aVote(voteData, 4, ((Doc)voteData.target).getName());
        });

        handler.<Player>register("votekick","<name/id...>","Opens vote for marking player a griefer.",(args,player)->{
            VoteData voteData = mkgfCommand.run(args,player);
            if(voteData==null) return;
            voteKick.aVote(voteData, 4,((Doc)voteData.target).getName());
        });

        handler.<Player>register("info","<s/n> [id]","Displays players profile.",(args, player)->{
            String data;
            PD pd = getData(player);
            if(!args[0].equals("s") && !args[0].equals("n")){
                sendErrMessage(player, "invalid-mode");
                return;
            }
            if(args.length == 1){
                Doc doc = pd.getDoc();
                data = args[0].equals("s") ?  doc.statsToString(pd) : doc.toString(pd);
            } else {
                if(!Strings.canParsePostiveInt(args[1])){
                    sendErrMessage(player,"refuse-not-integer","2");
                    return;
                }
                Doc doc = Database.data.getDoc(Long.parseLong(args[1]));
                if(doc == null){
                    sendErrMessage(player,"player-not-found");
                    return;
                }
                data = args[0].equals("s") ?  doc.statsToString(pd) : doc.toString(pd);
            }
            Call.onInfoMessage(player.con,data);
        });

        handler.<Player>register("set","[setting/permission/help] [on/off/argument]",
                "/set to see setting options. /set help for more info.",(args,player)->{
            PD pd = getData(player);
            if(args.length==0) {
                StringBuilder sb = new StringBuilder("[orange]==SETTINGS==[gray]\n\n");
                for(Setting s : Setting.values()){
                    String val = Database.hasEnabled(player, s) ? "[green]on[]" : "[scarlet]off[]";
                    sb.append(s.name()).append(":").append(val).append("\n");
                }
                for(Perm p : pd.perms){
                    String val = Database.hasDisabled(player, p) ?  "[scarlet]off[]" : "[green]on[]";
                    sb.append(p.name()).append(": ").append(val).append("\n");
                }
                Call.onInfoMessage(player.con, sb.toString());
                return;
            }
            if(args.length == 1){
                if (args[0].equals("help")) {
                    sendInfoPopup(player, "set-help", Ranks.verified.name ,Formatting.smoothColors("Colors go brrrrr!", "ffff00", "00ffff"));
                    return;
                }
                wrongArgAmount(player,args,2);
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
                    if (!pd.hasThisPerm(Perm.colorCombo)) {
                        sendErrMessage(player, "set-color-no-perm");
                        return;
                    }
                    String preview = smoothColors("Colors go brrrrr!!", args[1].split("/"));
                    sendMessage(player, "set-color-preview", preview);
                } else {
                    //checking if player is verified
                    if (!pd.hasPermLevel(Perm.high)) {
                        sendErrMessage(player, "at-least-verified", Ranks.verified.getSuffix());
                        return;
                    }
                    boolean tooDark = false;
                    if(args[1].startsWith("#")){
                        try{
                            Color col = new Color(args[1].substring(1));
                            tooDark = col.r < 40 && col.g < 40 && col.b < 40;
                        } catch (Exception ex){
                            ex.printStackTrace();
                        }
                    }
                    if(args[1].contains("[") || args[1].contains("]")){
                        sendErrMessage(player,"set-color-illegal-characters");
                        return;
                    }
                    if(args[1].contains("black") || tooDark) {
                        sendErrMessage(player, "set-color-invalid-shade");
                        return;
                    }
                    sendMessage(player, "set-textColor", args[1]);
                }
                pd.textColor = args[1];
                return;
            }
            boolean isSetting = enumContains(Setting.values(),args[0]);
            if(!isSetting && !enumContains(Perm.values(), args[0])){
                sendErrMessage(player,"set-invalid");
                return;
            }
            Setting s = isSetting ? Setting.valueOf(args[0]) : null;
            Perm p = !isSetting ? Perm.valueOf(args[0]) : null;
            long id = pd.id;
            switch (args[1]){
                case "on" :
                    if(isSetting) {
                        if(Database.hasEnabled(player, s)){
                            sendErrMessage(player, "set-already-enabled");
                            return;
                        }
                        Database.data.addToSet(id, "settings", args[0]);
                    } else {
                        if(!Database.hasDisabled(player, p)){
                            sendErrMessage(player, "set-already-enabled");
                            return;
                        }
                        Database.data.pull(id, "settings", args[0]);
                    }
                    break;
                case "off" :
                    if(isSetting) {
                        if(!Database.hasEnabled(player, s)){
                            sendErrMessage(player, "set-already-disabled");
                            return;
                        }
                        Database.data.pull(id, "settings", args[0]);
                    } else {
                        if(Database.hasDisabled(player, p)){
                            sendErrMessage(player, "set-already-disabled");
                            return;
                        }
                        Database.data.addToSet(id, "settings", args[0]);
                    }
                    break;
                default:
                    sendErrMessage(player,"no-such-option",args[1],"on/off");
                    return;
            }
            sendMessage(player,"set-toggled",args[0],args[1]);
        });

        handler.<Player>register("mute","<name/id/info>","Mutes or, if muted, unmutes player for you. " +
                "It can be used only on online players.",(args,player)->{
            Player other = findPlayer(args[0]);
            PD pd = getData(player);
            if(other == null){
                if(args[0].equals("info")){
                    StringBuilder sb = new StringBuilder("[orange]==MUTES==[]\n\n");
                    for(String s : (String[]) Database.data.get(pd.id, "mutes")){
                        for(Document d : Database.data.byUuid(s)) {
                            Doc doc = Doc.getNew(d);
                            sb.append(doc.getName()).append(" [white](").append(doc.getId()).append(")[gray],");
                        }

                    }
                    Call.onInfoMessage(player.con, sb.toString());
                    return;
                }
                sendErrMessage(player,"player-not-found-or-offline");
                return;
            }
            if(other == player){
                sendErrMessage(player,"mute-self");
                return;
            }

            if(Database.data.contains(pd.id, "mutes", other.usid)){
                Database.data.pull(pd.id, "mutes", other.uuid);
                sendMessage(player,"mute-un",other.name);
            } else {
                Database.data.addToSet(pd.id, "mutes", other.uuid);
                sendMessage(player,"mute",other.name);
            }
        });

        handler.<Player>register("link","<pin/refuse>",
                "Link your account with discord if you have the pin, or refuse link attempt.",(args,player)->{
            Doc doc = getData(player).getDoc();
            long id = doc.getId();
            if(!Bot.pendingLinks.containsKey(id)){
                sendErrMessage(player,"link-no-request");
                return;
            }
            if(args[0].equals("refuse")){
                Bot.pendingLinks.remove(id);
                sendMessage(player,"link-request-refuse");
                return;
            }
            if(args[0].equals(Bot.pendingLinks.get(id).pin)){
                String link =  Bot.pendingLinks.remove(id).id;
                Database.data.set(id, "link",link);
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
            switch (setRank(player,args[0],args[1],args.length==3 ? args[2] : null)){
                case notFound:
                    sendErrMessage(player,"player-not-found");
                    break;
                case invalid:
                    sendErrMessage(player,"rank-not-found");
                    sendMessage(player,"rank-s", Ranks.buildIn.keySet().toString());
                    sendMessage(player,"rank-s-custom", Ranks.special.keySet().toString());
                    break;
                case notPermitted:
                    sendErrMessage(player,"rank-using-admin-rank");
            }
        });

        handler.<Player>register("map","<help/change/restart/gameover/list/info/rate> [name/idx/this] [1-10/mode]",
                "More info via /map help",(args,player)->{
            VoteData voteData;
            String what = "map-restart";
            PD pd = getData(player);
            Map map = world.getMap();
            MDoc md = MapManager.played;
            Perm spec = Perm.restart;
            if(args.length>1){
                if(!args[1].equalsIgnoreCase("this")) {
                    map = findMap(args[1]);
                    if(map != null){
                        md = new MDoc(map);
                    }
                }
            }
            switch (args[0]){
                case "help":
                    sendInfoPopup(player,"map-help");
                    return;
                case "change":
                    what = "map-change";
                    spec = Perm.change;
                    if(wrongArgAmount(player,args,2)) return;
                    if (map == null){
                        sendErrMessage(player,"map-not-found");
                        return;
                    }
                case "restart":
                    String finalWhat = what;
                    Map finalMap = map;
                    Perm finalSpec = spec;
                    voteData = new VoteData() {
                        {
                            special = finalSpec;
                            by = player;
                            target = finalMap;
                            reason = finalWhat;
                        }
                        @Override
                        public void run() {
                            loadout.launchAll();
                            MapManager.endGame(false);
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
                            special = Perm.gameOver;
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
                    Gamemode gamemode = null;
                    if(args.length > 1){
                        if(!Strings.canParsePostiveInt(args[1])){
                            sendErrMessage(player,"refuse-not-integer","2");
                            return;
                        }
                        page = Integer.parseInt(args[1]);
                    }
                    if(args.length == 3) {
                        if(enumContains(Gamemode.values(), args[2])){
                            gamemode = Gamemode.valueOf(args[2]);
                        } else {
                            sendErrMessage(player, "map-wrong-game-mode");
                            return;
                        }
                    }
                    Call.onInfoMessage(player.con, formPage(
                            MapManager.getMapList(gamemode),page,getTranslation(pd,"map-list"),20));
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
                    md.addRating(player.uuid, rating);
                    sendMessage(player,"map-rate",md.map.name(),
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
            switch (setEmergency(args)) {
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

        handler.<Player>register("search","<searchKey/sort/rank/specialrank/donationlevel/online> [sortType/rankName] [reverse]","Shows first 40 results of search.",(args,player)->{
            if(args[0].equals("help")) {
                sendInfoPopup(player, "search-help", Arrays.toString(Stat.values()));
            }
            new Thread(()-> {
                ArrayList<String> res = Database.search(args, 40, Database.getData(player));
                for (String s : res) {
                    player.sendMessage(s);
                }
                if (res.isEmpty()) {
                    sendErrMessage(player, "search-no-results");
                }
            }).start();
        });

        handler.<Player>register("test","<start/egan/help/answer>","More info via /test help.", (args,player)-> {
            Long penalty = Tester.recent.contains(player.uuid);
            PD pd = Database.getData(player);
            boolean isTested = Tester.tests.containsKey(player.uuid);
            switch (args[0]) {
                case "help":
                    sendInfoPopup(player, "test-help");
                    return;
                case "start":
                    if (pd.isGriefer()) {
                        sendErrMessage(player, "griefer-no-perm");
                        return;
                    }
                    if (pd.hasPermLevel(Perm.high)) {
                        sendErrMessage(player, "test-no-need", Ranks.verified.getSuffix());
                        return;
                    }
                    if (penalty != null && penalty > 0) {
                        sendErrMessage(player, "test-is-recent", milsToTime(penalty));
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

        handler.<Player>register("ranks","<help/normal/special/info/update> [name/page]",
                "More info via /ranks help.", (args,player)->{
            PD pd = Database.getData(player);
            ArrayList<String> res = new ArrayList<>();
            int page = args.length == 2 && Strings.canParsePostiveInt(args[1]) ? Integer.parseInt(args[1]) : 1;
            switch (args[0]){
                case "help":
                    sendInfoPopup(player, "ranks-help");
                    return;
                case "info":
                    if(wrongArgAmount(player,args,2)) return;
                    Rank sr = Ranks.special.get(args[1]);
                    if(sr == null) {
                        sr = Ranks.buildIn.get(args[1]);
                    }
                    if(sr == null) {
                        sr = Ranks.donation.get(args[1]);
                    }
                    if(sr == null){
                        sendErrMessage(player,"ranks-rank-not-exist");
                        return;
                    }
                    Call.onInfoMessage(player.con, sr.getDescription(pd));
                    return;
                case "normal":
                    for(Rank r : Ranks.buildIn.values()){
                        res.add(r.getSuffix());
                    }
                    break;
                case "special":
                    for(Rank s : Ranks.special.values()){
                        String indicator = pd.obtained.contains(s) ? "[green]V[]":"[scarlet]X[]";
                        res.add(indicator + s.getSuffix() + indicator);
                    }
                    break;
                case "donation":
                    for(Rank s : Ranks.donation.values()){
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
                if (amount == 0) {
                    sendErrMessage(player, "redundant");
                    return;
                }
                ArrayList<Item> items = Loadout.getItemByName(args[2]);//todo test
                Item item = null;
                if(items.size() > 1 ){
                    sendErrMessage(player, "loadout-ambiguous-key", items.toString());
                } else if(!items.isEmpty()) {
                    item = items.get(0);
                }

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
                            arg = stack.toString();
                        }
                        if(condition != 0) {
                            Hud.addAd("loadout-condition", 10 ,"" + condition, "!gray", "!white");
                        }
                        ItemStack finalStack = stack;
                        data = new VoteData() {
                            {
                                special = Perm.loadout;
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
                            arg = stack.toString();
                        }
                        finalStack = stack;
                        data = new VoteData() {
                            {
                                special = Perm.loadout;
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
                                            sendMessage("loadout-ship-arrived", stack.toString());
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
                if (amount == 0) {
                    sendErrMessage(player, "redundant");
                    return;
                }
                ArrayList<UnitType> units = Factory.getUnitByName(args[2]);//todo test
                if(units.size() > 1) {
                    sendErrMessage(player, "factory-ambiguous-key", units.toString());
                    return;
                }
                if(units.isEmpty() || !Factory.config.prices.containsKey(units.get(0))){
                    sendErrMessage(player, "factory-does-not-have-this", args[2]);
                    return;
                }
                UnitType unit = units.get(0);
                UnitStack unitStack = new UnitStack(unit, amount);
                String arg;
                String arg2 = "";
                switch (args[0]) {
                    case "priceof":
                        Call.onInfoMessage(player.con,factory.price(unitStack));
                        return;
                    case "build":
                        UnitStack affordable = factory.canAfford(unitStack.unit);
                        if(Factory.config.shipCount - factory.threads.size() == 0) {
                            sendErrMessage(player, "factory-busy");
                            return;
                        }
                        if (affordable.amount == 0) {
                            sendErrMessage(player, "factory-cannot-afford");
                            return;
                        }
                        unitStack.amount = Math.min(affordable.amount, unitStack.amount);
                        arg = unitStack.toString();
                        data = new VoteData() {
                            {
                                special = Perm.factory;
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
                    case "send":
                    case "call":
                        Tile tile = world.tile((int)player.x/8,(int)player.y/8);
                        if(tile == null || tile.solid()){
                            sendErrMessage(player, "factory-cannot-drop-units");
                            return;
                        }
                        arg2 = tile.x + "," + tile.y;
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
                        String finalArg1 = arg2;
                        Vec2 aPos = new Vec2(player.x, player.y);
                        data = new VoteData() {
                            {
                                special = Perm.factory;
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
                                            sendMessage("factory-units-arrived", stack.toString(), finalArg1);
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
                vote.aVote(data, 3, arg, arg2);
            } else {
                wrongArgAmount(player, args, 3);
            }
        });

        handler.<Player>register("kickafk", "Kicks all afk players",(args, player)-> vote.aVote(new VoteData() {
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
            }}, 4));

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
                    special = Perm.skip;
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
            if(!getData(player).hasThisPerm(Perm.suicide)){
                sendErrMessage(player, "suicide-no-perm");
                return;
            }
            if(player.isDead()){
                sendErrMessage(player, "suicide-already-dead");
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
                    sendErrMessage(player, "player-not-found-or-offline");
                    return;
                }
                if(found == player){
                    sendErrMessage(player, "dm-cannot-dm-your-self");
                    return;
                }
                dms.put(player.uuid, found.uuid);
                sendMessage(player, "dm-channel-set", found.name);
            } else {
                if(!dms.containsKey(player.uuid)) {
                    sendErrMessage(player, "dm-no-channel-set");
                    return;
                }
                Player found = playerGroup.find(p -> p.uuid.equals(dms.get(player.uuid)));
                if(found == null){
                    sendErrMessage(player, "dm-target-offline");
                    return;
                }
                String msg = Formatting.formatMessage(player, Formatting.MessageMode.direct) + args[0];
                player.sendMessage(msg);
                found.sendMessage(msg);
            }

        });

        handler.<Player>register("revert" , "<id> <amount>" , "Admin usage only.",(args, player)->{
            if(!player.isAdmin){
                sendErrMessage(player, "refuse-not-admin");
                return;
            }
            if(!Strings.canParsePostiveInt(args[0])){
                sendErrMessage(player, "refuse-not-integer", "1");
                return;
            }
            if(!Strings.canParsePostiveInt(args[1])){
                sendErrMessage(player, "refuse-not-integer", "2");
                return;
            }
            Doc doc = Database.data.getDoc(Long.parseLong(args[0]));
            if(doc == null){
                sendErrMessage(player, "player-not-found");
                return;
            }
            if(doc.isAdmin()){
                sendErrMessage(player, "revert-cannot-revert");
                return;
            }
            ArrayList<Action> acts = Administration.undo.get(doc.getUuid());
            if(acts == null || acts.isEmpty()){
                sendErrMessage(player, "revert-no-actions", doc.getName());
                return;
            }
            int max = Integer.parseInt(args[1]);
            int count = 0;
            for(Action a : new ArrayList<>(acts)){
                if(count == max) break;
                a.Undo();
                acts.remove(0);
                count++;
            }
            sendMessage(player, "revert-reverted");
        });

        //todo test
        handler.<Player>register("buildcore", "<small/normal/big>", "Builds core on your coordinates.", (args, player)-> {
            Block core = Blocks.coreShard;
            TileEntity existingCore = player.getClosestCore();
            if (existingCore == null) {
                sendChatMessage(player, "buildcore-no-resources");
                return;
            }
            float priceRatio = .2f;
            Tile tile = player.tileOn();
            if(tile.solid()){
                sendErrMessage(player, "buildcore-cannot-build");
                return;
            }
            switch (args[0]) {
                case "normal":
                    core = Blocks.coreFoundation;
                    priceRatio = .3f;
                    break;
                case "big":
                    core = Blocks.coreNucleus;
                    priceRatio = .4f;
            }
            ArrayList<ItemStack> price = new ArrayList<>();
            int storageSize = 0;
            for (CoreBlock.CoreEntity c : state.teams.cores(Team.sharded)) {
                storageSize += c.block.itemCapacity;
            }
            for (Item i : content.items()) {
                if (i.type == ItemType.resource) continue;
                price.add(new ItemStack(i, (int) (storageSize * priceRatio)));
            }
            boolean has = true;
            StringBuilder sb = new StringBuilder();
            for (ItemStack i : price) {
                if (!existingCore.items.has(i.item, i.amount)) {
                    i.amount -= existingCore.items.get(i.item);
                    sb.append(i).append(" ");
                    has = false;
                }
            }
            if (!has) {
                sendErrMessage(player, "buildcore-missing", sb.toString());
                return;
            }
            Block finalCore = core;
            Block finalCore1 = core;
            vote.aVote(new VoteData() {
                {
                    by = player;
                    reason = "buildcore";
                    special = Perm.coreBuild;
                }

                @Override
                public void run() {
                    Call.onConstructFinish(tile, finalCore, 0, (byte) 0, player.getTeam(), true);
                    if (tile.block() == finalCore) {
                        for (ItemStack i : price) {
                            existingCore.items.remove(i.item, i.amount);
                        }
                        sendMessage("buildcore-success", finalCore1.name, tile.x + " " + tile.y);
                    } else {
                        sendMessage("buildcore-failed");
                    }
                }
            }, 3, core.name, tile.x + " " + tile.y);
        });

        handler.<Player>register("a", "<text...>", "Sends message just to admins.", (args, player)->{
            if(!General.isAdminOnline()) {
                sendErrMessage(player, "a-no-admin-online");//todo
                return;
            }
            Players.sendMessageToAdmins(player, "blank", args[0]);
            player.sendMessage(formatMessage(player, MessageMode.direct) + args[0]);
        });

        /*handler.<Player>register("o", "<server-name> <text...>", "Sends message just to admins.", (args, player)->{

            User bot = Bot.config.bots.get(args[0]);
            if(bot == null) {
                player.sendMessage("no bot with that name found. Options: " + Bot.config.bots.keySet().toString());
            }
            bot.sendMessage(Global.config.symbol +formatMessage(player, MessageMode.remote) + args[1]);
        });*/
    }
}
