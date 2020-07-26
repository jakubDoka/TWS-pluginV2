package theWorst.database;

import org.bson.Document;
import theWorst.tools.Millis;

import java.util.ArrayList;

import static theWorst.tools.Formatting.format;
import static theWorst.tools.Formatting.milsToTime;
import static theWorst.tools.Players.getTranslation;
import static theWorst.database.Database.online;

public class Doc {
    Document data;

    public static Doc getNew(Document document){
        if (document == null) return null;
        return new Doc(document);
    }

    public Doc(Document data){
        this.data = data;
    }

    public Long getStat( Stat stat) {
        return getStat(stat.name());
    }

    public Long getStat(String stat) {
        Long val = (Long) data.get(stat);
        return val == null ? 0 : val;
    }

    public boolean isProtected() {
        return getPassword() != null;
    }

    public boolean isParalyzed() {
        return data.get("paralyzed") != null;
    }

    public Long getPassword() {
        return (Long) data.get("password");
    }

    public String getLink(){
        return (String) data.get("link");
    }

    public Long getPrevious() {
        return (Long) data.get("previous");
    }

    public Long getId() {
        return (Long) data.get("_id");
    }

    public String getIp() {
        return (String) data.get("ip");
    }

    public String getTextColor() {
        return (String) data.get("textColor");
    }

    public String getUuid() {
        return (String) data.get("uuid");
    }

    public boolean isAdmin() {
        return getRank(RankType.rank).isAdmin;
    }

    public long getLatestActivity() {
        return (Long) data.get("lastActive");
    }

    public Rank getRank(RankType type) {
        String rankName = (String) data.get(type.name());
        if (rankName == null) {
            return null;
        }
        return Ranks.getRank(rankName, type);
    }

    public String getName() {
        return (String) data.get("name");
    }

    public boolean isGriefer() {
        return getRank(RankType.rank) == Ranks.griefer;
    }

    public long getLevel() {
        long total = 0;
        for(Stat s : Stat.values()) {
            total += s.value * getStat(s);
        }
        long level = 0;
        long val = 10000;
        while (total > 0){
            level++;
            total -= val;
            val = (long)(val * 1.2d);
        }
        return level;
    }

    public String toString(PD pd) {
        Rank rk = getRank(RankType.rank);
        Rank sr = getRank(RankType.specialRank);
        Rank dl = getRank(RankType.donationRank);
        String activity = online.containsKey(getUuid()) ? getTranslation(pd,"player-online") :
                format(getTranslation(pd, "player-offline"), milsToTime(Millis.since(getLatestActivity())));
        return format(getTranslation(pd, "player-info"),
                activity,
                "" + getId(),
                "" + data.get("level"),
                getName(),
                rk.getSuffix(),
                (sr == null ? "none" : sr.getSuffix()),
                (dl == null ? "none" : dl.getSuffix()),
                milsToTime(getStat(Stat.playTime)),
                milsToTime(Millis.since(getStat(Stat.age))),
                (String) data.get("country"));
    }

    public String statsToString(PD pd) {
        ArrayList<String> res = new ArrayList<>();
        for(Stat s : Stat.values()){
            if(s.inStats) res.add(String.valueOf(getStat(s)));
        }
        return format(getTranslation(pd, "player-stats"), res.toArray(new String[0]));
    }
}
