package theWorst;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.org.apache.xpath.internal.objects.XNull;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;

public class Config {
    public static final String dir = "config/mods/The_Worst/";
    public static final String saveDir = dir + "saves/";
    public static final String configDir = dir + "config/";
    public static final String file = configDir + "general.json";
    public static String dbName = "mindustryServer";
    public static long grieferAntiSpamTime = 1000*10;
    public static String welcomeMessage = null;
    public static String rules = null;

    public static void load(){
        Tools.loadJson(file,data -> {
            if(data.containsKey("dbName")) dbName = (String) data.get("dbName");
            if(data.containsKey("grieferAntiSpamTime")) grieferAntiSpamTime = (Long) data.get("grieferAntiSpamTime");
            if(data.containsKey("welcomeMessage")) welcomeMessage = (String) data.get("welcomeMessage");
            if(data.containsKey("rules")) rules = (String) data.get("rules");
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
            Tools.saveJson(file, data.toJSONString());
        });
    }


}
