package theWorst;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.org.apache.xpath.internal.objects.XNull;

public class Config {
    public static String dir = "config/mods/The_Worst/";
    public static String saveDir = dir + "saves/";
    public static String configDir = dir + "config/";
    public static String file = configDir + "general.json";
    public static String dbName= "mindustryServer";
    public static long grieferAntiSpamTime = 1000*10;
    public static String welcomeMessage = null;
    public static String rules = null;

    public static void load(){
        Tools.loadJson(file,data -> {
            if(data.containsKey("dbName")) dbName = (String) data.get("dbName");
            if(data.containsKey("grieferAntiSpamTime")) grieferAntiSpamTime = (Long) data.get("grieferAntiSpamTime");
            if(data.containsKey("welcomeMessage")) welcomeMessage = (String) data.get("welcomeMessage");
            if(data.containsKey("rules")) rules = (String) data.get("rules");
        },Config::defaultConfig);
    }

    public static void defaultConfig(){
        Tools.saveJson(file,"{\n" +
                "\t\"dbName\" : \"mindustryServer\",\n" +
                "\t\"grieferAntiSpamTime\" : 10,\n" +
                "\t\"welcomeMessage\":null,\n" +
                "\t\"rules\":null\n" +
                "}");
    }


}
