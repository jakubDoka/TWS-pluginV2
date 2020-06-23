package theWorst.helpers;

import arc.Events;
import arc.util.Time;
import arc.util.Timer;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import mindustry.gen.Call;
import theWorst.Config;
import theWorst.Tools;
import theWorst.database.*;

import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;

import static mindustry.Vars.netServer;
import static mindustry.Vars.world;
import static theWorst.Tools.*;

public class Administration implements Displayable{
    public static Emergency emergency = new Emergency(0); // Its just placeholder because time is 0
    public static HashMap<String, Integer> recent = new HashMap<>();
    public static Timer.Task recentThread;
    private final int maxAGFreq = 5;
    TileInfo[][] data;

    public Administration() {
        Hud.addDisplayable(this);
        //this updates recent map of deposit and withdraw events.
        if(recentThread != null) recentThread.cancel();
        recentThread = Timer.schedule(()->{
            for(String s : new HashSet<>(recent.keySet())){
                int val = recent.get(s);
                if(val <= 0){
                    recent.remove(s);
                    continue;
                }
                recent.put(s, val - maxAGFreq);
            }
        },0,1);

        //crete a a new action map when map changes.
        Events.on(EventType.PlayEvent.class, e -> {
            data = new TileInfo[world.height()][world.width()];
            for (int y = 0; y < world.height(); y++) {
                for (int x = 0; x < world.width(); x++) {
                    data[y][x] = new TileInfo();
                }
            }
        });
        //displaying of inspect messages
        Events.on(EventType.TapEvent.class, e -> {
            //do this only id player hes inspect enabled
            if (!Database.hasEnabled(e.player, Setting.inspect)) return;
            StringBuilder msg = new StringBuilder();
            TileInfo ti = data[e.tile.y][e.tile.x];
            if (ti.data.isEmpty()) {
                msg.append("No one interacted with this tile.");
            } else {
                msg.append(ti.lock == 1 ? Rank.verified.getName() + "\n" : "");
                for (String s : ti.data.keySet()) {
                    PlayerD pd = ti.data.get(s);
                    msg.append(String.format("[orange]%s : [gray]name[] - %s [gray]id[white] - %d[]\n", s, pd.originalName, pd.serverId));
                }
            }
            Call.onLabel(e.player.con, msg.toString(), 8, e.tile.x * 8, e.tile.y * 8);
        });
        //updating verification of tiles
        Events.on(EventType.BlockBuildEndEvent.class, e -> {
            //in case this is builder drone
            if (e.player == null) return;
            TileInfo ti = data[e.tile.y][e.tile.x];
            if (e.breaking) {
                ti.lock = 0;
            } else {
                if (Tools.getRank(Database.getData(e.player)).permission.getValue() >= Perm.high.getValue()) {
                    ti.lock = 1;
                }
            }
        });
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
                PlayerD pd = Database.getData(player);
                String color = pd.textColor;
                if (!Database.hasEnabled(player, Setting.chat)) {
                    sendErrMessage(player, "chat-disabled");
                    return null;
                }
                //handle griefer messages
                if (pd.rank.equals(Rank.griefer.name())) {
                    if (Time.timeSinceMillis(pd.lastMessage) < Config.grieferAntiSpamTime) {
                        sendErrMessage(player, "griefer-too-match-messages");
                        return null;
                    }
                    color = "pink";

                }
                //handle users with color combo permission
                String[] colors = pd.textColor.split("/");
                if (Database.hasSpecialPerm(player, Perm.colorCombo) && colors.length > 1) {
                    message = smoothColors(message,colors);
                } else message = "[#" + color + "]" + message;
                //updating stats
                pd.lastMessage = Time.millis();
                pd.messageCount++;
                //final sending message, i have my own function for this because people ca have this user muted
                sendChatMessage(player,message);
                return null;
            });

            netServer.admins.addActionFilter((act) -> {
                Player player = act.player;
                if (player == null) return true;
                PlayerD pd=Database.getData(player);
                if (pd == null) return false;
                pd.onAction(player);
                Rank rank = Tools.getRank(pd);
                //taping on tiles is ok.
                if(act.type == mindustry.net.Administration.ActionType.tapTile) return true;
                //this is against Ag client messing up game
                if(act.type == mindustry.net.Administration.ActionType.depositItem ||
                        act.type == mindustry.net.Administration.ActionType.withdrawItem){
                    int val = recent.getOrDefault(player.uuid, 0);
                    if(val > maxAGFreq){
                        sendErrMessage(player , "AG-slow-down");
                        recent.put(player.uuid,30);
                        return false;
                    }
                    recent.put(player.uuid, val+1);
                }
                TileInfo ti=data[act.tile.y][act.tile.x];
                //if there is emergency
                if(emergency.isActive() && rank.permission.getValue()<Perm.high.getValue()) {
                    Tools.sendErrMessage(player,"at-least-verified",Rank.verified.getName());
                    return false;
                }
                //if player has to low permission to interact
                if(!(rank.permission.getValue()>=ti.lock)){
                    if(rank==Rank.griefer){
                        Tools.sendErrMessage(player,"griefer-no-perm");
                    }else {
                        Tools.sendErrMessage(player,"at-least-verified",Rank.verified.getName());
                    }
                    return false;

                }
                //remember tis action for inspect.
                ti.data.put(act.type.name(),pd);
                return true;

            });
        });


    }

    @Override
    public String getMessage(PlayerD pd) {
        return emergency.getReport(pd);
    }

    @Override
    public void onTick() {
        emergency.onTick();
    }

    static class TileInfo{
        HashMap<String ,PlayerD> data = new HashMap<>();
        int lock= Perm.normal.getValue();
    }

    static class CommandUser{
        static HashMap<String,CommandUser> map = new HashMap<>();
        int commandUseLimit=5;
        String uuid;
        int used=1;
        Timer.Task thread;


        private void addOne(Player player){
            if(Database.hasPerm(player,Perm.high)) return;
            used+=1;
            if(used>=commandUseLimit){
                netServer.admins.addSubnetBan(player.con.address.substring(0,player.con.address.lastIndexOf(".")));
                Tools.kick(player,"kick-spamming");
                terminate();
                return;
            }
            if (used>=2){
                Tools.sendMessage(player,"warming-spam",String.valueOf(commandUseLimit-used));
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

        public String getReport(PlayerD pd){
            if(permanent){
                return Tools.format(Tools.getTranslation(pd,"emergency-permanent"),Rank.verified.getName());
            }
            if(time <= 0){
                return null;
            }
            String left = Tools.secToTime(time);
            return Tools.format(Tools.getTranslation(pd,"emergency"),left,left);
        }

        public void onTick(){
            if(time <= 0) return;
            time--;
            red = !red;
        }
    }


}
