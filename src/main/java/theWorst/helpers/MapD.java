package theWorst.helpers;

import arc.util.Time;
import mindustry.game.Rules;
import mindustry.game.SpawnGroup;
import mindustry.maps.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Query;
import theWorst.dataBase.PlayerD;

import java.util.HashMap;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static theWorst.Tools.Formatting.format;
import static theWorst.Tools.Formatting.milsToTime;
import static theWorst.Tools.Players.getTranslation;

@Document
public class MapD {
    @Transient static final int defaultAirWave = 1000000;
    @Id String filename;
    long timesPlayed = 0;
    long timesWon = 0;
    long waveRecord = 0;
    long longestTime = 0;
    long shortestTime = Long.MAX_VALUE;
    int firstAirWave = defaultAirWave;
    @Transient long started = Time.millis();
    long playtime = 0;
    long born = Time.millis();
    public String name;

    public HashMap<String,Byte> ratings=new HashMap<>();

    public MapD(Map map){
        filename = map.file.name();
        name=map.name();
        for(SpawnGroup sg:map.rules().spawns){
            if(sg.type.flying && sg.begin<firstAirWave) firstAirWave=sg.begin;
        }
    }

    @PersistenceConstructor public MapD(
            String filename,
            long timesPlayed,
            long timesWon,
            long waveRecord,
            int firstAirWave,
            long playtime,
            long born,
            long longestTime,
            long shortestTime,
            String name,
            HashMap<String,Byte> ratings){
        this.filename = filename;
        this.timesPlayed = timesPlayed;
        this.timesWon = timesWon;
        this.waveRecord = waveRecord;
        this.firstAirWave = firstAirWave;
        this.playtime = playtime;
        this.born = born;
        this.longestTime = longestTime;
        this.shortestTime = shortestTime;
        this.name = name;
        this.ratings = ratings;
    }

    double getPlayRatio(){
        return (playtime)/(double)Time.timeSinceMillis(born);
    }

    public float getRating(){
        if (ratings.isEmpty()) return 0;
        float total=0;
        for(byte b : ratings.values()){
            total += b;
        }
        return total / ratings.size();
    }

    public boolean hasNoAirWave() {
        return firstAirWave == defaultAirWave;
    }


    public String toString(Map map, PlayerD pd) {
        Rules rules = map.rules();
        return format(getTranslation(pd,"map-info"),
                name,
                map.author(),
                firstAirWave==defaultAirWave ? "no air waves" : ""+firstAirWave,
                "" + timesPlayed,
                "" + timesWon,
                "" + waveRecord,
                milsToTime(Time.timeSinceMillis(born)),
                milsToTime(playtime),
                milsToTime(shortestTime),
                milsToTime(longestTime),
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
                String.format("%.2f",rules.solarPowerMultiplier));
    }

    public void save() {
        MapManager.data.findAndReplace(new Query(where("filename").is(filename)),this);
    }
}
