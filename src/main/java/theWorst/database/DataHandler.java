package theWorst.database;

import arc.util.Time;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import mindustry.entities.type.Player;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;

import static com.mongodb.client.model.Filters.and;
import static theWorst.Tools.Formatting.*;
import static theWorst.Tools.Players.getTranslation;
import static theWorst.database.Database.*;

public class DataHandler {
    MongoCollection<Document> data;




    enum Indexed {
        serverId,
        ip,
        discordLink
    }



    public DataHandler(MongoCollection<Document> data){
        this.data = data;
        for(Indexed i : Indexed.values()) {
            data.createIndex(Indexes.descending(i.name()));
        }
    }

    public Bson find(Player player){
        return Filters.eq("_id", player.uuid);
    }

    public Bson find(String ip){
        return Filters.eq("ip", ip);
    }

    public Bson find(long id){
        return Filters.eq("serverId", id);
    }


    public Doc getDocByUuid(String uuid) {
        return Doc.getNew(data.find(Filters.eq("_id", uuid)).first());
    }

    public Doc getDoc(Player player) {
        return Doc.getNew(data.find(find(player)).first());
    }

    public Doc getDoc(String ip) {
        return Doc.getNew(data.find(find(ip)).first());
    }

    public Doc getDoc(long id) {
        return Doc.getNew(data.find(find(id)).first());
    }

    public Doc getDocBiLink(String link) {
        return Doc.getNew(data.find(Filters.eq("discordLink", link)).first());
    }



    public void set(Player player, String field, Object value) {
        data.updateOne(find(player), Updates.set(field, value));
    }

    public void addToSet(Player player, String field, Object value) {
        data.updateOne(find(player), Updates.addToSet(field, value));
    }

    public void pull(Player player, String field, Object value) {
        data.updateOne(find(player), Updates.pull(field, value));
    }

    public boolean contains(Player player, String field, Object value) {
        return data.find(and(find(player), Filters.eq(field, value))).first() != null;
    }


    public Object get(Player player, String field) {
        Document dc = data.find(find(player)).first();
        if (dc == null) return null;
        return dc.get(field);
    }

    public Long getId(Player player){
        return (Long) get(player, "serverId");
    }



    public void inc(Player player, Stat stat, long amount){
        data.updateOne(find(player), Updates.inc(stat.name(), amount));
    }

    public void incOne(Player player, Stat stat) {
        inc(player, stat, 1);
    }

    public Long getStat(Player player, String stat) {
        Long val = (Long) get(player, stat);
        return val == null ? 0 : val;
    }

    public FindIterable<Document> gt(Document doc, String stat) {
        return data.find(Filters.gt(stat, doc.get(stat)));
    }

    public long getPlace(Doc doc, String stat){
        long res = 0;
        for(Document d: gt(doc.data, stat)){
            res++;
        }
        return res;
    }

    public Rank getRank(Player player, RankType type) {
        String rankName = (String) get(player, type.name());
        if (rankName == null) {
            return null;
        }
        return Ranks.getRank(rankName, type);
    }

    public void remove(Player player, String field) {
        data.updateOne(find(player), Updates.unset(field));
    }

    public void remove(long id, String field) {
        data.updateOne(find(id), Updates.unset(field));
    }

    public void removeRank(Player player, RankType type) {
        remove(player, type.name());
    }

    public void removeRank(long id, RankType type) {
        remove(id, type.name());
    }


    public void setRank(String ip, Rank rank, RankType type) {
        data.updateMany(find(ip), Updates.set(type.name(), rank.name));
    }

    public void setRank(long id, Rank rank, RankType type) {
        data.updateMany(find(id), Updates.set(type.name(), rank.name));
    }

    public void free(PD pd) {
        set(pd.player, "textColor", pd.textColor);
        inc(pd.player, Stat.playTime, Time.millisToNanos(pd.joined));
        set(pd.player, "lastActive", Time.millis());
    }

    public PD LoadData(Player aPlayer) {
        Doc doc = getDoc(aPlayer);
        if(doc == null) {
            data.insertOne(new Document("_id", aPlayer.uuid));
            for(Setting s :Setting.values()){
                addToSet(aPlayer, "settings", s.name());
            }
            set(aPlayer, "serverId", getDatabaseSize());
            set(aPlayer, "rank", Ranks.newcomer);
        }
        set(aPlayer, "ip", aPlayer.con.address);
        set(aPlayer, "name", aPlayer.name);
        Doc finalDoc = getDoc(aPlayer);
        return new PD() {{
            player = aPlayer;
            name = aPlayer.name;
            rank = getRank(aPlayer, RankType.rank);
            textColor = finalDoc.getTextColor();
            joined = lastAction = lastMessage = Time.millis();
            id = finalDoc.getId();
        }};
    }

    public static class Doc {
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

        public String getLink(){
            return (String) data.get("link");
        }

        public String getUuid() {
            return (String) data.get("_id");
        }

        public String getIp() {
            return (String) data.get("ip");
        }

        public String getTextColor() {
            return (String) data.get("textColor");
        }

        public long getId() {
            return (Long) data.get("serverId");
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
               val = (long)(val * 1.1d);
            }
            return level;
        }

        public String toString(PD pd) {
            Rank rk = getRank(RankType.rank);
            Rank sr = getRank(RankType.specialRank);
            Rank dl = getRank(RankType.donationRank);
            String activity = online.containsKey(getUuid()) ? getTranslation(pd,"player-online") :
                    format(getTranslation(pd, "player-offline"), milsToTime(Time.timeSinceMillis(getLatestActivity())));
            return format(getTranslation(pd, "player-info"),
                    activity,
                    "" + getId(),
                    "" + getLevel(),
                    getName(),
                    rk.Suffix(),
                    (sr == null ? "none" : sr.Suffix()),
                    (dl == null ? "none" : dl.Suffix()),
                    milsToTime(getStat(Stat.playTime)),
                    milsToTime(Time.timeSinceMillis(getStat(Stat.age))),
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
}

