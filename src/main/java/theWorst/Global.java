package theWorst;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import theWorst.tools.Json;

import java.util.HashMap;

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
        public String dbAddress = "mongodb://127.0.0.1:27017";
        public String salt = "TWS";
        public HashMap<String, String > rules;
        public HashMap<String, String > welcomeMessage;

        public Config() {}

        @JsonCreator public Config(
                @JsonProperty("dbAddress") String dbAddress,
                @JsonProperty("alertPrefix") String alertPrefix,
                @JsonProperty("dbName") String dbName,
                @JsonProperty("rules") HashMap<String, String > rules,
                @JsonProperty("welcomeMessage") HashMap<String, String > welcomeMessage){
            if(dbAddress != null) this.dbAddress = dbAddress;
            if(alertPrefix != null) this.alertPrefix = alertPrefix;
            if(dbName != null) this.dbName = dbName;
            this.rules = rules;
            this.welcomeMessage = welcomeMessage;
        }
    }

    public static class RateLimits {
        public long grieferAntiSpamTime = 1000 * 10;
        public int configLimit = 50;
        public int withdrawLimit = 50;
        public int revertCacheSize = 200;
        public long rateLimitPeriod = 1000;
        public int countedActionsPerTile = 5;
        public long withdrawPenalty = 30 * 1000;
        public long testPenalty = 15 * 60 * 1000;
        public long minVotePlayTime = 1000 * 60 * 30;

        public RateLimits() {}

        @JsonCreator public RateLimits(
                @JsonProperty("grieferAntiSpamTime") long grieferAntiSpamTime,
                @JsonProperty("configLimit") int configLimit,
                @JsonProperty("revertCacheSize") int revertCacheSize,
                @JsonProperty("withdrawLimit") int withdrawLimit,
                @JsonProperty("withdrawPenalty") long withdrawPenalty,
                @JsonProperty("rateLimitPeriod") long rateLimitPeriod,
                @JsonProperty("testPenalty") long testPenalty,
                @JsonProperty("minVotePlayTime") long minVotePlayTime,
                @JsonProperty("countedActionsPerTile") int countedActionsPerTile){

            this.grieferAntiSpamTime = grieferAntiSpamTime;
            this.countedActionsPerTile = countedActionsPerTile;
            this.configLimit = configLimit;
            this.revertCacheSize = revertCacheSize;
            this.withdrawLimit = withdrawLimit;
            this.rateLimitPeriod = rateLimitPeriod;
            this.testPenalty = testPenalty;
            this.minVotePlayTime = minVotePlayTime;
            this.withdrawPenalty = withdrawPenalty;
        }
    }

}
