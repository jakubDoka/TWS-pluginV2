package theWorst;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import theWorst.Tools.Json;

import java.util.HashMap;

public class Global {
    public static final String dir = "config/mods/The_Worst/";
    public static final String saveDir = dir + "saves/";
    public static final String configDir = dir + "config/";
    public static final String file = configDir + "general.json";

    public static Config config = new Config();

    public static void load() {
        config = Json.loadJackson(file, Config.class);
        if (config == null) config = new Config();
    }


    public static class Config {
        public String alertPrefix = "!!";
        public String dbName = "mindustryServer";
        public long grieferAntiSpamTime = 1000*10;
        public int rateLimit = 50;
        public long rateLimitPeriod = 1000;
        public int countedActionsPerTile = 5;
        public HashMap<String, String > rules;
        public HashMap<String, String > welcomeMessage;

        public Config() {}

        @JsonCreator public Config(
                @JsonProperty("alertPrefix") String alertPrefix,
                @JsonProperty("dbName") String dbName,
                @JsonProperty("grieferAntiSpamTime") long grieferAntiSpamTime,
                @JsonProperty("rateLimit") int rateLimit,
                @JsonProperty("rateLimitPeriod") long rateLimitPeriod,
                @JsonProperty("countedActionsPerTile") int countedActionsPerTile,
                @JsonProperty("rules") HashMap<String, String > rules,
                @JsonProperty("welcomeMessage") HashMap<String, String > welcomeMessage){
            this.alertPrefix = alertPrefix;
            this.dbName = dbName;
            this.grieferAntiSpamTime = grieferAntiSpamTime;
            this.countedActionsPerTile = countedActionsPerTile;
            this.rateLimit = rateLimit;
            this.rateLimitPeriod = rateLimitPeriod;
            this.rules = rules;
            this.welcomeMessage = welcomeMessage;
        }
    }

}
