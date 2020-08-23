package theWorst.helpers.maps;


import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import mindustry.game.Rules;
import mindustry.game.SpawnGroup;
import mindustry.maps.Map;
import org.bson.Document;
import org.bson.conversions.Bson;
import theWorst.database.PD;
import theWorst.tools.Millis;

import static theWorst.tools.Formatting.format;
import static theWorst.tools.Formatting.milsToTime;
import static theWorst.tools.General.hash;
import static theWorst.tools.Players.getTranslation;


public class MDoc {
    static long defaultAirWave = 1000000;
    public Document data;
    public Map map;
    long id;
    long started;

    String ratings = "ratings";

    public static long getID(Map map) {
        return hash(map.name() + map.author() + map.description() + map.file.name());
    }

    public static long getFirstAyrWave(Map map) {
        long firstAirWave = defaultAirWave;
        for(SpawnGroup sg:map.rules().spawns){
            if(sg.type.flying && sg.begin<firstAirWave) firstAirWave=sg.begin;
        }
        return firstAirWave;
    }

    public MDoc(Map map){
        this.map = map;
        this.id = getID(map);
        data = MapManager.data.find(find()).first();
        if(data == null){
            MapManager.data.insertOne(new Document("_id", id));
            set(Stat.age.name(), Millis.now());
            set("file", map.file.name());
            set(Stat.shortest.name(), Long.MAX_VALUE);
            set(Stat.firstAirWave.name(), getFirstAyrWave(map));
            data = MapManager.data.find(find()).first();
        }
    }

    Bson find(){
        return Filters.eq("_id", id);
    }

    long getStat(Stat stat) {
        Long val = (Long) data.get(stat.name());
        if (val == null) return 0;
        return val;
    }

    void inc(Stat stat, long amount) {
        MapManager.data.updateOne(find(), Updates.inc(stat.name(), amount));
    }

    void set(String name, Object value) {
        MapManager.data.updateOne(find(), Updates.set(name, value));
    }

    public void addRating(String uuid, long value){
        MapManager.data.updateOne(find(), Updates.set(ratings + "." + uuid, value));
    }

    double getPlayRatio(){
        return (getStat(Stat.playtime))/(double) Millis.since(getStat(Stat.age));
    }

    public double getRating(){
        Document ratings = (Document) data.get(this.ratings);
        if(ratings == null) return 0;
        double total = 0;
        for(Object rating : ratings.values()) {
            total += (long) rating;
        }
        return total / ratings.size();
    }

    public boolean hasNoAirWave() {
        return getStat(Stat.firstAirWave) == defaultAirWave;
    }


    public String toString(Map map, PD pd) {
        Rules rules = map.rules();
        long firstAirWave = getStat(Stat.firstAirWave);
        long shortest = getStat(Stat.shortest);
        return format(getTranslation(pd,"map-info"),
                map.name(),
                map.author(),
                firstAirWave==defaultAirWave ? "no air waves" : ""+firstAirWave,
                "" + getStat(Stat.played),
                "" + getStat(Stat.won),
                "" + getStat(Stat.record),
                milsToTime(Millis.since(getStat(Stat.age))),
                milsToTime(getStat(Stat.playtime)),
                shortest != Long.MAX_VALUE ? milsToTime(shortest) : "none",
                milsToTime(getStat(Stat.longest)),
                String.format("%.1f/10",getRating()),
                rules.mode().name(),
                String.format("%.2f",rules.buildCostMultiplier),
                String.format("%.2f",rules.buildSpeedMultiplier),
                String.format("%.2f",rules.blockHealthMultiplier),
                String.format("%.2f",rules.unitBuildSpeedMultiplier),
                String.format("%.2f",rules.unitDamageMultiplier),
                String.format("%.2f",rules.unitHealthMultiplier),
                String.format("%.2f",rules.playerDamageMultiplier),
                String.format("%.2f",rules.playerHealthMultiplier),
                String.format("%.2f",rules.solarPowerMultiplier),
                String.format("%.2f",rules.deconstructRefundMultiplier));
    }
}
