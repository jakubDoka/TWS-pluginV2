package theWorst.helpers;

import arc.Events;
import arc.struct.Array;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.type.Player;
import mindustry.game.*;
import mindustry.gen.Call;
import mindustry.maps.Map;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import theWorst.Config;
import theWorst.Main;
import theWorst.Tools;
import theWorst.database.Database;
import theWorst.discord.MapParser;

import java.io.*;
import java.util.HashMap;
import java.util.List;

import static mindustry.Vars.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;

public class MapManager {
    public static MapD played;
    static MongoOperations data = new MongoTemplate(Database.client, Config.dbName);

    public MapManager(){
        Events.on(EventType.GameOverEvent.class, e -> endGame(e.winner==Team.sharded));

        Events.on(EventType.PlayEvent.class, e-> {
            played = getData(world.getMap());
            played.started = Time.millis();
            Hud.addAd(played.hasNoAirWave() ? "map-no-air-wave" : "map-first-air-wave",30,"" + played.defaultAirWave);
        });
    }

    public static MapD getData(Map map){
        MapD md = data.findOne(new Query(where("fileName").is(map.file.name())),MapD.class);
        if(md == null){
            md = new MapD(map);
            data.save(md);
        }
        return md;
    }

    public static Array<String> statistics(){
        Array<String> res=new Array<>();
        List<MapD> maps= data.findAll(MapD.class);
        double bestRatio=0;
        for (MapD m:maps){
            double r=m.getPlayRatio();
            if (r>bestRatio) bestRatio=r;
        }
        for (int i=0;i<maps.size();i++){
            MapD m=maps.get(i);
            int ra=(int)(m.getPlayRatio()/bestRatio*10);
            res.add(i+" | "+m.name+" | "+String.format("%.1f/10",m.getRating())+" | "+
                "<"+new String(new char[ra]).replace("\0", "=")+
                    new String(new char[10-ra]).replace("\0", "-")+">\n");
        }
        return res;
    }

    public static Array<String> getMapList() {
        Array<Map> maps=Vars.maps.customMaps();
        Array<String> res=new Array<>();
        for (int i=0;i<maps.size;i++){
            Map map = maps.get(i);
            String m = map.name();
            MapD md = getData(map);
            int r = (int)md.getRating();
            res.add("[yellow]"+i+"[] | [gray]"+m+"[] | "+String.format("[%s]%d/10[]",r<6 ? r<3 ? "scarlet":"yellow":"green",r));
        }
        return res;
    }

    public static void onMapRemoval(Map removed) {
        maps.removeMap(removed);
        data.remove(getData(removed));
        maps.reload();
    }

    public static void onMapAddition(Map added) {
        MapD md = data.findOne(new Query(where("fileName").is(added.file.name())),MapD.class);;
        if(md != null){
            data.remove(md);
        }
        data.save(new MapD(added));
        maps.reload();
    }

    public void endGame(boolean won) {
        if(played==null) return;
        played.playtime+=Time.timeSinceMillis(played.started);
        played.timesPlayed++;
        if(won) played.timesWon++;
        if(state.wave > played.waveRecord) played.waveRecord=state.wave;
        played.save();
    }

    public String getWaveInfo(){
        if(played.hasNoAirWave()) return "No air waves on this map.";
        return "Air enemy starts at wave [orange]" + played.firstAirWave + "[] !";
    }
}
