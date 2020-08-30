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
        public String symbol = "[green]<Survival>[]";
        public String alertPrefix = "!!";
        public String dbName = "mindustryServer";
        public String dbAddress = "mongodb://127.0.0.1:27017";
        public String salt = "TWS";
        public HashMap<String, String > rules;
		public HashMap<String, String > guide;
        public HashMap<String, String > welcomeMessage;
        public int consideredPassive = 10;

        public int vpnTimeout;
        public String vpnApi;

        public Config() {}

        @JsonCreator public Config(
                @JsonProperty("consideredPassive") int consideredPassive,
                @JsonProperty("symbol") String symbol,
                @JsonProperty("dbAddress") String dbAddress,
                @JsonProperty("alertPrefix") String alertPrefix,
                @JsonProperty("dbName") String dbName,
                @JsonProperty("rules") HashMap<String, String > rules,
				@JsonProperty("guide") HashMap<String, String > guide,
                @JsonProperty("welcomeMessage") HashMap<String, String > welcomeMessage,
                @JsonProperty("vpnApi") String vpnApi,
                @JsonProperty("vpnTimeout") int vpnTimeout){
            if(symbol != null) this.symbol = symbol;
            if(dbAddress != null) this.dbAddress = dbAddress;
            if(alertPrefix != null) this.alertPrefix = alertPrefix;
            if(dbName != null) this.dbName = dbName;
            this.consideredPassive = consideredPassive;
            this.rules = rules;
			this.guide = guide;
            this.welcomeMessage = welcomeMessage;
            this.vpnApi = vpnApi;
            this.vpnTimeout = vpnTimeout;
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
        public long minVotePlayTimeReq = 1000 * 60 * 30;
        public int builderMinMaterialReq = 10;
        public long reportPenalty = 1000 * 60 * 30;
        public float builderBoost = .001f;

        public RateLimits() {}

        @JsonCreator public RateLimits(
                @JsonProperty("grieferAntiSpamTime") long grieferAntiSpamTime,
                @JsonProperty("configLimit") int configLimit,
                @JsonProperty("revertCacheSize") int revertCacheSize,
                @JsonProperty("withdrawLimit") int withdrawLimit,
                @JsonProperty("withdrawPenalty") long withdrawPenalty,
                @JsonProperty("rateLimitPeriod") long rateLimitPeriod,
                @JsonProperty("testPenalty") long testPenalty,
                @JsonProperty("minVotePlayTimeReq") long minVotePlayTimeReq,
                @JsonProperty("countedActionsPerTile") int countedActionsPerTile,
                @JsonProperty("builderMinMaterialReq") int builderMinMaterialReq,
                @JsonProperty("reportPenalty") long reportPenalty,
                @JsonProperty("builderBoost") float builderBoost) {

            if (grieferAntiSpamTime != 0) this.grieferAntiSpamTime = grieferAntiSpamTime;
            if (countedActionsPerTile != 0) this.countedActionsPerTile = countedActionsPerTile;
            if (configLimit != 0) this.configLimit = configLimit;
            if (revertCacheSize != 0) this.revertCacheSize = revertCacheSize;
            if (withdrawLimit != 0) this.withdrawLimit = withdrawLimit;
            if (rateLimitPeriod != 0) this.rateLimitPeriod = rateLimitPeriod;
            if (testPenalty != 0) this.testPenalty = testPenalty;
            if (minVotePlayTimeReq != 0) this.minVotePlayTimeReq = minVotePlayTimeReq;
            if (withdrawPenalty != 0) this.withdrawPenalty = withdrawPenalty;
            if (builderMinMaterialReq != 0) this.builderMinMaterialReq = builderMinMaterialReq;
            if (reportPenalty != 0) this.reportPenalty = reportPenalty;
            if(builderBoost != 0) this.builderBoost = builderBoost;
        }
    }

}
