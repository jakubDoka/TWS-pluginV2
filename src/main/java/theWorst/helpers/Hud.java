package theWorst.helpers;

import arc.Events;
import arc.struct.Array;
import arc.util.Log;
import arc.util.Timer;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import mindustry.gen.Call;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import theWorst.Config;
import theWorst.Main;
import theWorst.Tools;
import theWorst.database.Database;
import theWorst.database.PlayerD;
import theWorst.database.Setting;
import theWorst.votes.Vote;

import java.util.ArrayList;

import static mindustry.Vars.player;
import static mindustry.Vars.playerGroup;

public class Hud {
    final static String messageFile = Config.configDir + "hudMessages.json";
    static Timer.Task update;
    static ArrayList<Displayable> displayable = new ArrayList<>();
    Timer.Task messageCycle;
    Timer.Task coreDamageAlert;
    boolean coreDamaged=false;
    boolean alertIsRed=false;
    static Array<String> messages= new Array<>();
    int speed=10;
    static Array<Ad> adQueue= new Array<>();
    int current=0;


    public Hud(){
        Events.on(EventType.Trigger.teamCoreDamage,()->{
            int alertDuration=10;
            if(coreDamageAlert!=null){
                return;
            }
            coreDamaged=true;
            coreDamageAlert=Timer.schedule(()->{
                coreDamaged=false;
                coreDamageAlert=null;
            },alertDuration);
        });
    }

    public static void addDisplayable(Displayable displayable){
        Hud.displayable.add(displayable);
    }

    void startCycle(int speed){
        this.speed=speed;
        messageCycle=Timer.schedule(()->{
            if(messages.isEmpty()){
                return;
            }

            current+=1;
            current%=messages.size;
        },0,speed*60);
    }

    void update(){
        update=Timer.schedule(()-> {
            try {
                for (Player p : playerGroup) {
                    if (!Database.hasEnabled(p, Setting.hud)) {
                        Call.hideHudText(player.con);
                        continue;
                    }
                    PlayerD pd = Database.getData(player);
                    StringBuilder b = new StringBuilder();
                    if (!messages.isEmpty()) b.append(messages.get(current));
                    for (Displayable d : displayable) {
                        String r = d.getMessage(pd);
                        if (r == null) continue;
                        b.append(r);
                        b.append("\n");
                    }
                    for (Ad a : adQueue) {
                        b.append(a.getMessage(pd)).append("\n");
                    }
                    Call.setHudText(p.con, b.toString());
                }
            } catch (Exception ex) {ex.printStackTrace();}
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
            expiration = Timer.schedule(()->{
                adQueue.remove(this);
            },liveTime);
        }

       String getMessage(PlayerD pd){
           String currentColor;
           if (colors.isEmpty()){
               currentColor="white";
           }else {
               currentColor=colors.get(idx);
               idx++;
               idx%=colors.size();
           }
           return "["+currentColor+"]" + Tools.format(Tools.getTranslation(pd , message),args.toArray(new String[0])) + "[]";
       }
    }

    public static void addAd(String  message,int time,String ... args){
        adQueue.add(new Ad(message,args,time));
    }

    public static void loadMessages(){
        Tools.loadJson(messageFile,data->{
            for(Object o : (JSONArray)data.get("messages")){
                messages.add((String) o);
            }
        },Hud::defaultMessages);
    }

    private static void defaultMessages() {
        JSONObject data = new JSONObject();
        JSONArray array = new JSONArray();
        array.add("Put your messages like this.");
        array.add("And this.");
        data.put("subnet",array);
        Tools.saveJson(messageFile,data.toJSONString());
        Tools.logInfo("files-default-config-created","hud messages", messageFile);
    }

}
