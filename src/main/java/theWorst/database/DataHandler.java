package theWorst.database;


import arc.Core;
import arc.util.Log;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import mindustry.entities.type.Player;
import org.bson.Document;
import org.bson.conversions.Bson;
import theWorst.Bot;
import theWorst.tools.Millis;
import theWorst.tools.VPNDetection;

import java.util.Objects;

import static com.mongodb.client.model.Filters.and;
import static theWorst.tools.Formatting.getSubnet;
import static theWorst.tools.Players.*;
import static theWorst.database.Database.*;

public class DataHandler {
    MongoCollection<Document> data;
    MongoCollection<Document> counter;
    public final static long paralyzedId = -1;
    public final static long invalidId = -2;



    enum Indexed {
        uuid,
        ip,
        discordLink
    }



    public DataHandler(MongoCollection<Document> data, MongoCollection<Document> counter){
        this.data = data;
        this.counter = counter;
        for(Indexed i : Indexed.values()) {
            data.createIndex(Indexes.descending(i.name()));
        }
        Doc doc = getDoc(paralyzedId);
        if(doc == null) {
            data.insertOne(new Document("_id", paralyzedId));
        }
    }

    public Bson findUuid(String uuid) {
        return Filters.eq("uuid", uuid);
    }

    public Bson find(Player player){
        return findUuid(player.uuid);
    }

    public Bson find(String ip){
        return Filters.eq("ip", ip);
    }

    public Bson find(long id){
        return Filters.eq("_id", id);
    }

    public FindIterable<Document> byUuid(String uuid) {
        return data.find(findUuid(uuid));
    }

    public long count(Bson filter) {
        return data.countDocuments(filter);
    }

    public String getSuggestions(String uuid, String ip) {
        StringBuilder sb = new StringBuilder("[yellow]");
        FindIterable<Document> fits = data.find(Filters.or(findUuid(uuid), find(ip)));
        for(Document fit : fits) {
            Doc doc = Doc.getNew(fit);
            sb.append(doc.getName()).append("[gray] || []").append(doc.getId()).append("\n");
        }
        return sb.toString();
    }

    public Doc getDoc(long id) {
        return Doc.getNew(data.find(find(id)).first());
    }

    public Doc getDocBiLink(String link) {
        return Doc.getNew(data.find(Filters.eq("discordLink", link)).first());
    }

    public void delete(long id){
        data.deleteOne(find(id));
    }

    public void set(long id, String field, Object value) {
        data.updateOne(find( id), Updates.set(field, value));
    }

    public void setUuid(long id, String uuid) {
        set(id, "uuid", uuid);
    }

    public void setIp(long id, String ip) {
        set(id, "ip", ip);
    }

    public void addToSet(long id, String field, Object value) {
        data.updateOne(find( id), Updates.addToSet(field, value));
    }

    public void pull(long id, String field, Object value) {
        data.updateOne(find( id), Updates.pull(field, value));
    }

    public boolean contains(long id, String field, Object value) {
        return data.find(and(find( id), Filters.eq(field, value))).first() != null;
    }


    public Object get(long id, String field) {
        Document dc = data.find(find( id)).first();
        if (dc == null) return null;
        return dc.get(field);
    }

    public void inc(long id, Stat stat, long amount){
        data.updateOne(find( id), Updates.inc(stat.name(), amount));
    }

    public void incOne(long id, Stat stat) {
        inc( id, stat, 1);
    }

    public Long getStat(long id, String stat) {
        Long val = (Long) get( id, stat);
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

    public Rank getRank(long id, RankType type) {
        String rankName = (String) get( id, type.name());
        if (rankName == null) {
            return null;
        }
        return Ranks.getRank(rankName, type);
    }

    public void remove(long id, String field) {
        data.updateOne(find(id), Updates.unset(field));
    }

    public void removeRank(long id, RankType type) {
        remove( id, type.name());
    }

    public void setRank(String ip, Rank rank, RankType type) {
        data.updateMany(find(ip), Updates.set(type.name(), rank.name));
    }

    public void setRank(long id, Rank rank, RankType type) {
        data.updateMany(find(id), Updates.set(type.name(), rank.name));
    }

    public void free(PD pd) {
        long id = pd.id;
        set(id, "textColor", pd.textColor);
        inc(id, Stat.playTime, Millis.since(pd.joined));
        set(id, "lastActive", Millis.now());
        Doc doc = getDoc(pd.id);
        if (doc == null) {
            return;
        }
        set(id, "level", doc.getLevel());

        if (pd.dRank != null) set(id, RankType.donationRank.name(), pd.dRank.name);
        else remove(id, RankType.donationRank.name());
        if (pd.sRank != null) set(id, RankType.specialRank.name(), pd.sRank.name);
        else remove(id, RankType.specialRank.name());
    }

    public Doc findData(Player player) {
        String ip = player.con.address;
        long resultCount = data.countDocuments(Filters.and(find(player), find(ip)));
        if(resultCount == 1) {
            return Doc.getNew(data.find(Filters.or(find(player), find(ip))).first());
        }
        if(resultCount == 0) {
            boolean exists = false;
            for(Document d : data.find(Filters.or(find(player), find(player.con.address)))){
                exists = true;
                Doc doc = Doc.getNew(d);
                if(doc.isProtected()) continue;
                Log.info("fit found");
                return doc;
            }
            if (!exists) {
                Log.info("no fit making new");
                return null;
            }
        }

        sendInfoPopup(player,"database-must-log-in", getSuggestions(player.uuid, player.con.address));
        return Doc.getNew(new Document("paralyzed", true));
    }

    public void bind(Player player, long id) {
        setUuid(id, player.uuid);
        setIp(id, player.con.address);
    }

    public PD LoadData(Player player) {
        Doc doc = findData(player);
        if(doc != null && doc.isParalyzed()) {
            return new PD(player);
        } else if(doc == null) {
            doc = MakeNewAccount(player);
        }
        PD pd = new PD(player, doc);
        set(pd.id, "name", player.name);
        return pd;
    }

    public Doc MakeNewAccount(Player player){
        long id = getNewId();
        data.insertOne(new Document("_id", id));
        for(Setting s :Setting.values()) {
            addToSet(id, "settings", s.name());
        }
        bind(player, id);
        String sub = getSubnet(player.con.address);
        if(vpn.contains(sub) || VPNDetection.isVpnUser(player.con.address)) {
            sendErrMessage(player,"database-vpn-detected");
            Bot.onRankChange(player.name, id, Ranks.newcomer.name, Ranks.griefer.name, "Server", "VPN detected.");
            setRank(id, Ranks.griefer, RankType.rank);
            vpn.add(sub);
            Database.saveVpn();
        } else {
            setRank(id, Ranks.newcomer, RankType.rank);
        }

        set(id, "age", Millis.now());

        return getDoc(id);
    }

    private long getNewId() {
        if(counter.updateOne(find(0), Updates.inc("id", 1)).getModifiedCount() == 0){
            long id = 0;
            Document latest = data.find().sort(new Document("_id", -1)).first();
            if(latest != null) {
                id = (long)latest.get("_id");
            }
            counter.insertOne(new Document("_id", 0).append("id",id));
        }
        Document counter = this.counter.find().first();
        if(counter == null){
            throw new IllegalStateException("You have to have mongodb installed.");
        }
        return (long) counter.get("id");
    }
}

