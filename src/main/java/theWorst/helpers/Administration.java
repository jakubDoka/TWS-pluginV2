package theWorst.helpers;

import arc.Events;
import arc.util.Time;
import arc.util.Timer;
import com.sun.scenario.effect.impl.prism.ps.PPSBlend_SOFT_LIGHTPeer;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import mindustry.gen.Call;
import org.springframework.context.annotation.MBeanExportConfiguration;
import theWorst.Config;
import theWorst.Tools;
import theWorst.database.*;

import java.util.HashMap;
import java.util.TimerTask;

import static mindustry.Vars.netServer;
import static mindustry.Vars.world;
import static theWorst.Tools.*;

public class Administration {
    public static Emergency emergency = new Emergency(0); // Its just placeholder because time is 0
    TileInfo[][] data;

    public Administration() {
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
                } else if (Database.hasSpecialPerm(player, Perm.colorCombo) || Database.hasPerm(player, Perm.highest)) {
                    String[] colors = pd.textColor.split("/");
                    if(colors.length>1){
                        message = smoothColors(message,colors);
                    }

                }

                pd.lastMessage = Time.millis();
                pd.messageCount++;
                sendChatMessage(player,message);
                return null;
            });

            netServer.admins.addActionFilter((act) -> {
                Player player = act.player;
                if (player == null) return false;
                PlayerD pd=Database.getData(player);
                if (pd == null) return false;
                pd.onAction(player);
                Rank rank = Tools.getRank(pd);

                if(act.type== mindustry.net.Administration.ActionType.tapTile) return true;
                TileInfo ti=data[act.tile.y][act.tile.x];
                if(emergency.isActive() && rank.permission.getValue()<Perm.high.getValue()) {
                    Tools.sendErrMessage(player,"at-least-verified",Rank.verified.getName());
                    return false;
                }
                if(!(rank.permission.getValue()>=ti.lock)){
                    if(rank==Rank.griefer){
                        Tools.sendErrMessage(player,"griefer-no-perm");
                    }else {
                        Tools.sendErrMessage(player,"at-least-verified",Rank.verified.getName());
                    }
                    return false;

                }
                ti.data.put(act.type.name(),pd);
                return true;

            });
        });


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
                //Todo Tools.errMessage(player,commandUseLimit-used+" more quick uses of command and you will be banned for spam.");
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
            time = 60*min;
        }

        public boolean isActive(){
            return time>0 || permanent;
        }

        public String getReport(){
            if(permanent){
                return "emergency-permanent";
            }
            if(time<=0){
                return null;
            }
            time--;
            red = !red;
            return "emergency";
        }
    }


}
