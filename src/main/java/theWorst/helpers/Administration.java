package theWorst.helpers;

import arc.Events;
import arc.math.Mathf;
import arc.util.Timer;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.world.StaticTree;
import mindustry.world.Tile;
import theWorst.Bot;
import theWorst.Global;
import theWorst.database.*;
import theWorst.tools.Millis;

import java.util.ArrayList;
import java.util.HashMap;

import static mindustry.Vars.*;
import static theWorst.tools.Formatting.*;
import static theWorst.tools.Players.*;

public class Administration implements Displayable{

    public static Emergency emergency = new Emergency(0); // Its just placeholder because time is 0
    public static HashMap<String, ArrayList<Long>> recentWithdraws = new HashMap<>();
    public static RecentMap banned = new RecentMap("ag-can-withdraw-egan"){
        @Override
        public long getPenalty() {
            return Global.limits.withdrawPenalty;
        }
    };
    public static RecentMap doubleClicks = new RecentMap(null) {
        @Override
        public long getPenalty() {
            return 300;
        }
    };
    public static Timer.Task recentThread;
    TileInfo[][] data;
    public static HashMap<String, ArrayList<Action>> undo = new HashMap<>();

    public Administration() {
        Action.register();
        Hud.addDisplayable(this);
        //this updates recent map of deposit and withdraw events.
        if(recentThread != null) recentThread.cancel();

        //crete a a new action map when map changes.
        Events.on(EventType.PlayEvent.class, e -> {
            data = new TileInfo[world.height()][world.width()];
            for (int y = 0; y < world.height(); y++) {
                for (int x = 0; x < world.width(); x++) {
                    data[y][x] = new TileInfo();
                }
            }
            undo.clear();
            Action.buildBreaks.clear();
        });

        Events.on(EventType.TapEvent.class, e ->{
            if(e.player == null) return;
            handleInspect(e.player, e.tile);
        } );

        //disable lock if block wos destroyed
        Events.on(EventType.BlockDestroyEvent.class, e -> data[e.tile.y][e.tile.x].lock = 0);
        //this is against spam bots
        Events.on(EventType.PlayerChatEvent.class, e -> {
            if (!e.message.startsWith("/")) return;
            if (CommandUser.map.containsKey(e.player.uuid)) {
                CommandUser.map.get(e.player.uuid).addOne(e.player);
                return;
            }
            CommandUser.map.put(e.player.uuid, new CommandUser(e.player));
        });
        //adding filters, it has to be done after server is loaded
        Events.on(EventType.ServerLoadEvent.class, e -> {
            netServer.admins.addChatFilter((player, message) -> {
                //do not display how people voted
                if (message.equals("y") || message.equals("n")) return null;
                PD pd = Database.getData(player);
                String color = pd.textColor;
                if (!Database.hasEnabled(player, Setting.chat)) {
                    sendErrMessage(player, "chat-disabled");
                    return null;
                }
                //handle griefer messages
                if (pd.rank == Ranks.griefer) {
                    if (Millis.since(pd.lastMessage) < Global.limits.grieferAntiSpamTime) {
                        sendErrMessage(player, "griefer-too-match-messages");
                        return null;
                    }
                    color = Ranks.griefer.color;

                }
                //handle users with color combo permission
                if(pd.textColor != null) {
                    String[] colors = pd.textColor.split("/");
                    if (pd.hasThisPerm(Perm.colorCombo) && colors.length > 1) {
                        StringBuilder indentedMassage = new StringBuilder();
                        int lineLength = 40;
                        int extraChars = 4; // brackets around the name
                        for(int i = 0; i < message.length(); i++) {
                            indentedMassage.append(message.charAt(i));
                            if (i % lineLength == 0 && i > lineLength || (i + pd.name.length() + extraChars == lineLength)) {
                                if(i < message.length() - 1) {
                                    if(message.charAt(i+1) != ' '){
                                        indentedMassage.append("-");
                                    }
                                }
                                indentedMassage.append("\n");
                            }
                        }
                        message = smoothColors(indentedMassage.toString(),colors);
                    } else message = "[" + color + "]" + message;
                } else message = "[" + pd.rank.color + "]" + message;

                //updating stats
                pd.lastMessage = Millis.now();
                Database.data.incOne(pd.id, Stat.messageCount);
                //final sending message, i have my own function for this because people ca have this user muted
                sendChatMessage(player,message);
                return null;
            });

            netServer.admins.addActionFilter( act -> {
                Player player = act.player;
                if (player == null) return true;
                PD pd = Database.getData(player);
                if (pd == null) return true;
                pd.onAction();
                //taping on tiles is ok.
                switch (act.type) {
                    case tapTile:
                        if(pd.isGriefer()) {
                            player.kill();
                        }
                        return true;
                    case rotate:
                        if(!Database.hasEnabled(player, Setting.rotation)) {
                            sendErrMessage(player, "setting-rotation-disabled");
                            return false;
                        }
                }

                //this is against Ag client messing up game
                //if there is emergency

                if(emergency.isActive() && !pd.hasPermLevel(Perm.high)) {
                    sendErrMessage(player,"at-least-verified", Ranks.verified.getSuffix());
                    return false;
                }
                TileInfo ti = data[act.tile.y][act.tile.x];
                //if player has to low permission to interact
                if(!pd.hasPermLevel(ti.lock)){
                    if(pd.isGriefer()){
                        sendErrMessage(player,"griefer-no-perm");
                    }else {
                        sendErrMessage(player,"at-least-verified", Ranks.verified.getSuffix());
                    }
                    return false;
                }
                if(!pd.hasPermLevel(Perm.higher) && !(act.tile.block() instanceof StaticTree)){
                    ArrayList<Action> acts = undo.computeIfAbsent(player.uuid, k -> new ArrayList<>());
                    long now = Millis.now();
                    switch (act.type) {
                        case breakBlock:
                            if(act.tile.entity != null && !world.getMap().rules().bannedBlocks.contains(act.tile.block())){
                                Action.addBuildBreak(acts, new Action.Break() {
                                    {
                                        by = player.uuid;
                                        tile = act.tile;
                                        block = act.tile.block();
                                        config = act.tile.entity.config();
                                        rotation = act.tile.rotation();
                                        age = now;
                                    }
                                });
                            }
                            break;
                        case placeBlock:
                            Action.addBuildBreak(acts, new Action.Build() {
                                {
                                    by = player.uuid;
                                    tile = act.tile;
                                    block = act.block;
                                    age = now;
                                }
                            });
                            break;
                        case depositItem:
                        case withdrawItem:
                            ArrayList<Long> draws = recentWithdraws.computeIfAbsent(player.uuid, k -> new ArrayList<>());
                            if (draws.size() > Global.limits.withdrawLimit) {
                                Long ban = banned.contains(player.uuid);
                                if (ban != null) {
                                    if (ban > 0) {
                                        sendErrMessage(player, "ag-cannot-withdraw", milsToTime(ban));
                                        return false;
                                    } else {
                                        draws.clear();
                                    }
                                } else {
                                    banned.put(player.uuid, now);
                                    return false;
                                }
                            } else {
                                for (Long l : new ArrayList<>(draws)) {
                                    if (Millis.since(l) > 1000) {
                                        draws.remove(0);
                                    }
                                }
                                draws.add(now);
                            }
                            break;
                        case configure:
                            if (act.tile.entity != null) {
                                acts.add(0, new Action.Configure() {
                                    {
                                        block = act.tile.block();
                                        config = act.tile.entity.config();
                                        newConfig = act.config;
                                        tile = act.tile;
                                        age = now;
                                    }
                                });
                            }
                            break;
                        case rotate:
                            acts.add(0, new Action.Rotate() {
                                {
                                    block = act.tile.block();
                                    rotation = act.tile.rotation();
                                    newRotation = (byte) act.rotation;
                                    tile = act.tile;
                                    age = now;
                                }
                            });
                    }
                    if(acts.size() > Global.limits.revertCacheSize){
                        acts.remove(Global.limits.revertCacheSize);
                    }
                    if(!acts.isEmpty()){
                        int burst = 0;
                        Tile currTile = acts.get(0).tile;
                        int actPerTile = 0;
                        for(Action a :acts){
                            if(Millis.since(a.age) < Global.limits.rateLimitPeriod && !(a instanceof Action.Build || a instanceof Action.Break)) {
                                if(a.tile == currTile){
                                    actPerTile++;
                                } else {
                                    currTile = a.tile;
                                    actPerTile = 0;
                                }
                                if(actPerTile > Global.limits.countedActionsPerTile){
                                    continue;
                                }
                                burst ++;
                            } else {
                                break;
                            }
                        }
                        if (burst > Global.limits.configLimit){
                            Bot.onRankChange(pd.name, pd.id, pd.rank.name, Ranks.griefer.name, "Server", "auto");
                            Database.setRank(pd.id, Ranks.griefer);
                            for(Action a: acts) {
                                a.Undo();
                            }
                            acts.clear();
                        }
                    }
                }
                //remember tis action for inspect.
                ti.add(act.type.name(),pd);
                ti.lock = Mathf.clamp(pd.getHighestPermissionLevel(), 0, 1);
                return true;

            });
        });


    }

    private void handleInspect(Player player, Tile tile){
        if (!Database.hasEnabled(player, Setting.inspect)) return;
        Long pn = doubleClicks.contains(player.uuid);
        if(pn == null || pn < 0) {
            doubleClicks.add(player.uuid);
            return;
        }
        StringBuilder msg = new StringBuilder();
        TileInfo ti = data[tile.y][tile.x];
        if (ti.data.isEmpty()) {
            msg.append("No one interacted with this tile.");
        } else {
            msg.append(ti.lock == 1 ? Ranks.verified.getSuffix() + "\n" : "");
            for (String s : ti.data.keySet()) {
                msg.append("[orange]").append(s).append(":[white] ");
                for (PD pd : ti.data.get(s)){
                    msg.append(pd.id).append(" = ").append(pd.name).append(" | ");
                }
                msg.delete(msg.length() - 1, msg.length());
                msg.append("\n");
            }
        }
        Call.onLabel(player.con, msg.toString().substring(0, msg.length() - 1), 8, tile.getX(), tile.getY());
    }

    @Override
    public String getMessage(PD pd) {
        return emergency.getReport(pd);
    }

    @Override
    public void onTick() {
        emergency.onTick();
    }



    static class CommandUser{
        static HashMap<String,CommandUser> map = new HashMap<>();
        int commandUseLimit=5;
        String uuid;
        int used=1;
        Timer.Task thread;


        private void addOne(Player player){
            if(Database.getData(player).hasPermLevel(Perm.high)) return;
            used+=1;
            if(used>=commandUseLimit){
                netServer.admins.addSubnetBan(player.con.address.substring(0,player.con.address.lastIndexOf(".")));
                kick(player,"kick-spamming",0);
                terminate();
                return;
            }
            if (used>=2){
                sendMessage(player,"warming-spam",String.valueOf(commandUseLimit-used));
            }
        }

        private void terminate(){
            map.remove(uuid);
            thread.cancel();
        }

        private CommandUser(Player player) {
            uuid = player.uuid;
            map.put(uuid, this);
            thread=Timer.schedule(()->{
                used--;
                if (used == 0) {
                    terminate();
                }
            }, 2, 2);
        }
    }

    public static class Emergency{
        int time;
        boolean red;
        boolean permanent;

        public Emergency(int min){
            if(min == -1) permanent = true;
            time = 60 * min;
        }

        public boolean isActive(){
            return time > 0 || permanent;
        }

        public String getReport(PD pd){
            if(permanent){
                return format(getTranslation(pd,"emergency-permanent"), Ranks.verified.getSuffix());
            }
            if(time <= 0){
                return null;
            }
            String left = secToTime(time);
            return format(getTranslation(pd,"emergency"),left,left);
        }

        public void onTick(){
            if(time <= 0) return;
            time--;
            red = !red;
        }
    }

    public static abstract class RecentMap extends HashMap<String, Long>{
        String endMessage;

        public RecentMap(String endMessage){
            this.endMessage = endMessage;
        }

        public abstract long getPenalty();

        public void add(String id){
            put(id, Millis.now());
            Timer.schedule(()->{
                if(endMessage == null) return;
                Player found = playerGroup.find(p -> p.uuid.equals(id));
                if(found == null) return;
                sendMessage(found, endMessage);
            }, getPenalty()/1000f);
        }

        public Long contains(String id){
            Long res = get(id);
            if(res == null) return null;
            res = getPenalty() - Millis.since(res);
            if( res < 0) {
                remove(player.uuid);
            }
            return res;
        }
    }
}

