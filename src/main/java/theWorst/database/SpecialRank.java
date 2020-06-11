package theWorst.database;

import arc.struct.Array;
import arc.util.Log;
import arc.util.serialization.Jval;
import org.bson.Document;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.Serializable;


public class SpecialRank implements Serializable {
    String name;
    public Array<Perm> permissions=new Array<>(new Perm[]{Perm.normal});
    Array<String> linked=new Array<>();
    Stat stat=Stat.none;
    Mode mode= Mode.none;
    String color="";
    String description="missing";
    boolean permanent=false;
    int value=0;
    int req=0;
    int freq=0;

    public SpecialRank(String name, JSONObject obj, JSONObject all){
        this.name=name;
        try {
            if(obj.containsKey("permission")){
                permissions.clear();
                Object data=obj.get("permission");
                if(data instanceof String){
                    permissions.add(Perm.valueOf((String)data));
                }else {
                    for(Object o:(JSONArray)data){
                        permissions.add(Perm.valueOf((String)o));
                    }
                }
            }
            if(obj.containsKey("linked")){
                for(Object o:(Jval.JsonArray)obj.get("linked")){
                    if(!all.containsKey(o)) continue;
                    linked.add((String)o);
                }
            }
            if(obj.containsKey("tracked")) stat=Stat.valueOf((String)obj.get("tracked"));
            if(obj.containsKey("mode")) mode= Mode.valueOf((String)obj.get("mode"));
            if(obj.containsKey("color")) color=(String)obj.get("color");
            if(obj.containsKey("description")) description=(String)obj.get("description");
            if(obj.containsKey("permanent")) permanent=(boolean)obj.get("permanent");
            if(obj.containsKey("value")) value=((Long)obj.get("value")).intValue();
            if(obj.containsKey("requirement")) req=((Long)obj.get("requirement")).intValue();
            if(obj.containsKey("frequency")) freq=((Long)obj.get("frequency")).intValue();
        } catch (IllegalArgumentException ex){
            Log.info("In "+name+":permission or stat or mode has invalid value.");
        } catch (ClassCastException ex){
            Log.info("In "+name+":color or description or permanent or requirement or frequency has invalid " +
                    "type value.Stack trace may give you a hint.");
            ex.printStackTrace();
        }


    }

    public SpecialRank() { }

    public boolean condition(PlayerD tested){
        if(permanent) return false;
        long val=(long) Database.getRawMeta(tested.uuid).get(stat.name());
        switch (mode){
            case best:
                for(Document pd:Database.getAllRawMeta()){
                    if((long)pd.get(stat.name())>val) return false;
                }
                return true;
            case req:
                return val>=req;
            case reqFreq:
                return val>=req && val/((tested.playTime+1)/(1000*60*60f))>=freq;
            case merge:
                for(String s:linked){
                    if(!Database.ranks.get(s).condition(tested)) return false;
                }
                return true;
            default:
                return false;
        }
    }

    public String getSuffix(){
        return "["+color+"]<"+name+">[]";
    }

    enum Mode{
        best{{
           description="If tracked of the player is highest among all players he will obtain the rank";
        }},
        reqFreq{{
            description="If tracked of the player has same or higher value then requirement and also tracked/" +
                    "player_play_time_in_hours is higher then frequency he will obtain the rank";
        }},
        req{{
            description="If tracked of the player has same or higher value then requirement he will obtain the rank";
        }},
        merge{{
            description="If condition of all liked ranks is met player will obtain this rank. " +
                    "WARMING: i greatly recommend testing if everything is OK before before using it for real.";
        }},
        none;
        public String description=null;
    }

    public static void help(){
        Log.info(Database.rankFile+" allows you to add custom ranks used on your server.\n" +
                "Custom rank is created by writhing {\"rankName\":{\"permanent\":true}} to the file. " +
                "in this case you created rank that can be only assigned by command. There are more examples" +
                " of custom ranks in automatically generated file and i will just describe what optional " +
                "properties does.\n" +
                "Permission determinate special special ability rank owner obtains.\n" +
                "Here is list of them and what they does, if you miss any kind of permission write issue on " +
                "github.");
        for(Perm p:Perm.values()){
            if(p.description!=null){
                Log.info(p.name()+"-"+p.description);
            }
        }
        Log.info("Tracked determinate witch property of player info is tracked by this rank.\n" +
                "Here is the list of properties that can be tracked,if you cant find desired option you " +
                "know what to do:");
        for(Stat s:Stat.values()){
            Log.info(s.name());
        }
        Log.info("Requirement is related to req and reqFreq mode.\n" +
                "Frequency is related to reqFreq mode.\n" +
                "Mode determinate wey of determining, yes, weather player gets the rank.\n" +
                "Here is list of them and what they does, if you miss any kind of mode you know what to do:");
        for(Mode m: Mode.values()){
            if(m.description!=null){
                Log.info(m.name()+"-"+m.description);
            }
        }
        Log.info("Value-when player fulfill more the one rank value determinate witch rank will be " +
                "chosen. Higher value is prioritized.\n" +
                "Color just makes rank displayed in different color. There are two ways to define " +
                "color both shown in generated file.\n" +
                "Permanent determinate weather the rank is achievable or settable only by command." +
                "it will not go evey unit you reset players rank or some achievable rank with higher value surpasses it.\n"+
                "Linked is used with merge setting you can put names of other ranks here and if condition of this ranks" +
                "are med simultaneously player will obtain this rank");
    }
}
