package theWorst.helpers;

import arc.Events;
import arc.struct.Array;
import arc.util.Timer;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import theWorst.Global;
import theWorst.database.Database;
import theWorst.database.PD;
import theWorst.database.Setting;

import java.util.ArrayList;

import static mindustry.Vars.player;
import static mindustry.Vars.playerGroup;
import static theWorst.tools.Formatting.format;
import static theWorst.tools.Json.loadJackson;
import static theWorst.tools.Players.getTranslation;

public class Hud {
    final static String messageFile = Global.configDir + "hudMessages.json";
    static MessageCycle cycle = new MessageCycle();
    static Timer.Task update;
    static ArrayList<Displayable> displayable = new ArrayList<>();
    boolean showCoreAlert;
    static Array<Ad> adQueue= new Array<>();

    public Hud(){
        //this is necessary, if local player is null, team core damage event will not be triggered
        player = new Player();
        player.setTeam(Team.sharded);
        Events.on(EventType.Trigger.teamCoreDamage,()->{
            if(!showCoreAlert) return;
            addAd("hud-core-under-attack",10,"!scarlet","!gray");
            showCoreAlert = false;
            Timer.schedule(()->showCoreAlert=true,10);
        });
        loadMessages();
        update();
    }

    public static void addDisplayable(Displayable displayable){
        Hud.displayable.add(displayable);
    }

    void update(){
        update=Timer.schedule(()-> {
            try {
                for(Displayable d: displayable){
                    d.onTick();
                }
                for (Player p : playerGroup) {
                    //player has hud disabled so hide the hud
                    if (!Database.hasEnabled(p, Setting.hud)) {
                        Call.hideHudText(p.con);
                        continue;
                    }
                    PD pd = Database.getData(p);
                    StringBuilder b = new StringBuilder().append("[#cbcbcb]");
                    if (cycle.on) b.append(cycle.getMessage()).append("\n");
                    //displayable are registered in their constructors
                    for (Displayable d : displayable) {
                        String r = d.getMessage(pd);
                        if (r == null) continue;
                        b.append(r);
                        b.append("\n");
                    }
                    //is there are adds going on show them
                    for (Ad a : adQueue) {
                        b.append(a.getMessage(pd)).append("\n");
                    }
                    Call.setHudText(p.con, b.toString().substring(0,b.length()-1)); //get rid of \n character
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            },0,1);



    }

    static class Ad{
       Timer.Task expiration;
       private final String message;
        ArrayList<String> args = new ArrayList<>();
       ArrayList<String> colors = new ArrayList<>();
       int idx=0;

        Ad(String message,String[] args,int liveTime){
            this.message=message;
            for(String arg : args){
                if(arg.startsWith("!")){
                    colors.add(arg.replace("!",""));
                } else this.args.add(arg);
            }
            for(Player p : playerGroup){
                if(!Database.hasEnabled(p, Setting.hud)){
                    p.sendMessage(getMessage(Database.getData(p)));
                }
            }
            expiration = Timer.schedule(()-> adQueue.remove(this),liveTime);
        }

       String getMessage(PD pd){
           String currentColor;
           if (colors.isEmpty()){
               currentColor="white";
           }else {
               currentColor=colors.get(idx);
               idx++;
               idx%=colors.size();
           }
           return "["+currentColor+"]" + format(getTranslation(pd , message),args.toArray(new String[0])) + "[]";
       }
    }

    public static void addAd(String  message,int time,String ... args){
        adQueue.add(new Ad(message,args,time));
    }

    public static void loadMessages(){
        MessageCycle mc = loadJackson(messageFile, MessageCycle.class, "hud messages");
        if(mc == null) return;
        cycle.thread.cancel();
        cycle = mc;
    }

    public static class MessageCycle {
        public String[] messages = new String[]{
                "To toggle thous messages modify file [orange]"+messageFile+"[].",
                "Put your messages like this.",
                "And this."
        };
        public int speed = 1;
        public boolean on = true;
        int idx = 0;
        Timer.Task thread;

        public MessageCycle() {start();}

        @JsonCreator public MessageCycle(
                @JsonProperty("messages") String[] messages,
                @JsonProperty("speed") int speed) {
            this.messages = messages;
            this.speed = speed;
            on = messages.length > 0;
            if(on){
                start();
            }
        }

        private void start(){
            thread = Timer.schedule(()->{
                idx++;
                idx %= messages.length;
            }, 0, speed * 60);
        }

        String getMessage() {
            return messages[idx];
        }
    }

}
