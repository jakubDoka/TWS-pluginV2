package theWorst;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import theWorst.Tools.Json;

import java.util.HashMap;

import static theWorst.Tools.Commands.logInfo;

public class Global {
    public static final String dir = "config/mods/The_Worst/";
    public static final String saveDir = dir + "saves/";
    public static final String configDir = dir + "config/";
    public static final String configFile = configDir + "general.json";
    public static final String limitFile = configDir + "limits.json";


    public static Config config = new Config();
    public static RateLimits limits = new RateLimits();

    public static void loadConfig() {
        config = Json.loadJackson(configFile, Config.class, "general staff");
        if (config == null) config = new Config();
    }

    public static void loadLimits() {
        limits = Json.loadJackson(limitFile, RateLimits.class, "rate limits");
        if (limits == null) limits = new RateLimits();
    }


    public static class Config {
        public String alertPrefix = "!!";
        public String dbName = "mindustryServer";
        public HashMap<String, String > rules;
        public HashMap<String, String > welcomeMessage;

        public Config() {}

        @JsonCreator public Config(
                @JsonProperty("alertPrefix") String alertPrefix,
                @JsonProperty("dbName") String dbName,
                @JsonProperty("rules") HashMap<String, String > rules,
                @JsonProperty("welcomeMessage") HashMap<String, String > welcomeMessage){
            this.alertPrefix = alertPrefix;
            this.dbName = dbName;
            this.rules = rules;
            this.welcomeMessage = welcomeMessage;
        }
    }

    public static class RateLimits {
        public long grieferAntiSpamTime = 1000*10;
        public int configLimit = 50;
        public int withdrawLimit = 50;
        public long rateLimitPeriod = 1000;
        public int countedActionsPerTile = 5;

        public RateLimits() {}

        @JsonCreator public RateLimits(
                @JsonProperty("grieferAntiSpamTime") long grieferAntiSpamTime,
                @JsonProperty("configLimit") int configLimit,
                @JsonProperty("withdrawLimit") int withdrawLimit,
                @JsonProperty("rateLimitPeriod") long rateLimitPeriod,
                @JsonProperty("countedActionsPerTile") int countedActionsPerTile){

            this.grieferAntiSpamTime = grieferAntiSpamTime;
            this.countedActionsPerTile = countedActionsPerTile;
            this.configLimit = configLimit;
            this.withdrawLimit = withdrawLimit;
            this.rateLimitPeriod = rateLimitPeriod;
        }
    }

}
