package theWorst.helpers;

import arc.Events;
import arc.struct.Array;
import arc.util.Log;
import arc.util.Time;
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
    static Timer.Task messageCycle = null;
    boolean showCoreAlert;
    static Array<String> messages= new Array<>();
    static int speed=10;
    static Array<Ad> adQueue= new Array<>();
    static int current=0;


    public Hud(){
        //this is necessary, if local player is null, team core damage will not be triggered
        player = new Player();
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
                for (Player p : playerGroup) {
                    //player has hud disabled so hide the hud
                    if (!Database.hasEnabled(p, Setting.hud)) {
                        Call.hideHudText(p.con);
                        continue;
                    }
                    PlayerD pd = Database.getData(p);
                    StringBuilder b = new StringBuilder().append("[#cbcbcb]");
                    if (!messages.isEmpty()) b.append(messages.get(current)).append("\n");
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
        speed = 3;
        Tools.loadJson(messageFile,data->{
            if(data.containsKey("messages")){
                for(Object o : (JSONArray)data.get("messages")){
                    messages.add((String) o);
                }
            }
            if(data.containsKey("speed")) speed = ((Long)data.get("speed")).intValue();
        },Hud::defaultMessages);
        if(messageCycle != null){
            messageCycle.cancel();
        }
        messageCycle=Timer.schedule(()->{
            if(messages.isEmpty()){
                return;
            }
            current+=1;
            current%=messages.size;
        },0,speed*60);
    }

    private static void defaultMessages() {
        JSONObject data = new JSONObject();
        JSONArray array = new JSONArray();
        array.add("To toggle thous messages modify file [orange]"+messageFile+"[].");
        array.add("Put your messages like this.");
        array.add("And this.");
        data.put("messages",array);
        data.put("speed",1);
        Tools.saveJson(messageFile,data.toJSONString());
        Tools.logInfo("files-default-config-created","hud messages", messageFile);
    }

}
