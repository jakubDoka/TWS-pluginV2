package theWorst.helpers;

import arc.Events;
import arc.files.Fi;
import arc.struct.Array;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.io.MapIO;
import mindustry.maps.Map;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import theWorst.Config;
import theWorst.database.Database;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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

    public static void cleanMaps(){
        List<MapD> mapDS = data.findAll(MapD.class);
        for(MapD mapD : mapDS){
            File mapFile = new File("config/maps" + mapD.filename);
            if(mapFile.exists()){
                data.remove(mapD);
                continue;
            }
            try {
                Map map = MapIO.createMap(new Fi(mapFile), true);
                if(new MapD(map).firstAirWave != mapD.firstAirWave) data.remove(mapD);
            } catch (IOException e) {
                data.remove(mapD);
            }
        }
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

    public static ArrayList<String> getMapList() {
        Array<Map> maps=Vars.maps.customMaps();
        ArrayList<String> res=new ArrayList<>();
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
        played = null;
        maps.reload();
    }

    public static void onMapAddition(Map added) {
        MapD md = data.findOne(new Query(where("fileName").is(added.file.name())),MapD.class);
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
}
