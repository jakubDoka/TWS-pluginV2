package theWorst;

import org.json.simple.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;

import static theWorst.Tools.Json.loadJson;
import static theWorst.Tools.Json.saveJson;

public class Config {
    public static final String dir = "config/mods/The_Worst/";
    public static final String saveDir = dir + "saves/";
    public static final String configDir = dir + "config/";
    public static final String file = configDir + "general.json";
    public static String dbName = "mindustryServer";
    public static long grieferAntiSpamTime = 1000*10;
    public static HashMap<String, String > rules = new HashMap<>();
    public static HashMap<String, String > welcomeMessage = new HashMap<>();

    public static void load(){
        loadJson(file,data -> {
            if(data.containsKey("dbName")) dbName = (String) data.get("dbName");
            if(data.containsKey("grieferAntiSpamTime")) grieferAntiSpamTime = (Long) data.get("grieferAntiSpamTime");
            if(data.containsKey("rules")) {
                JSONObject ru = (JSONObject) data.get("rules");
                if(ru != null){
                    for (Object o : ru.keySet()) {
                        rules.put((String) o, (String) ru.get(o));
                    }
                }

            }
            if(data.containsKey("welcomeMessage")) {
                JSONObject wm = (JSONObject) data.get("welcomeMessage");
                if(wm != null){
                    for (Object o : wm.keySet()) {
                        welcomeMessage.put((String) o, (String) wm.get(o));
                    }
                }
            }
        },()->{
            JSONObject data = new JSONObject();
            for(Field f : Config.class.getDeclaredFields()){
                if(Modifier.isFinal(f.getModifiers())) continue;
                try {
                    data.put(f.getName(),f.get(null));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            saveJson(file, data.toJSONString());
        });
    }


}
