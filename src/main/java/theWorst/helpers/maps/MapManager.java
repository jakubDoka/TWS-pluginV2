package theWorst.helpers.maps;

import arc.Events;
import arc.files.Fi;
import arc.math.Mathf;
import arc.struct.Array;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Gamemode;
import mindustry.game.Team;
import mindustry.io.MapIO;
import mindustry.maps.Map;
import org.bson.Document;
import theWorst.helpers.Hud;
import theWorst.tools.Millis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import static mindustry.Vars.*;

public class MapManager {

    public static MDoc played;

    public static MongoCollection<Document> data = MongoClients.create().getDatabase("mindustryMaps").getCollection("maps");

    public MapManager(){
        Events.on(EventType.GameOverEvent.class, e -> endGame(e.winner==Team.sharded));

        Events.on(EventType.PlayEvent.class, e-> {
            played = new MDoc(world.getMap());
            played.started = Millis.now();
            Hud.addAd(played.hasNoAirWave() ? "map-no-air-wave" : "map-first-air-wave", 30, "" + played.getStat(Stat.firstAirWave));
        });
    }

    public static void cleanMaps(){
        FindIterable<Document> maps = data.find();
        for(Document d : maps){
            File mapFile = new File("config/maps/" + d.get("file"));
            if(!mapFile.exists()){
                data.deleteOne(d);
                continue;
            }
            try {
                Map map = MapIO.createMap(new Fi(mapFile), true);
                if(MDoc.getID(map) != (long)d.get("_id")) data.deleteOne(d);
            } catch (IOException e) {
                data.deleteOne(d);
            }
        }
    }

    public static ArrayList<String> statistics(){
        ArrayList<String> res = new ArrayList<>();

        ArrayList<MDoc> maps = new ArrayList<>();
        for(Map m : Vars.maps.customMaps()){
            maps.add(new MDoc(m));
        }

        if(maps.isEmpty()){
            res.add("No info to show");
            return res;
        }

        double bestRatio=0;
        for (MDoc m : maps){
            double r = m.getPlayRatio();
            if (r > bestRatio) bestRatio = r;
        }

        for (int i = 0;i<maps.size();i++) {
            MDoc m = maps.get(i);
            int ra = (int) (m.getPlayRatio() / bestRatio * 10);
            ra = Mathf.clamp(ra, 0, 10);
            res.add(i + " | " + m.map.name() + " | " + String.format("%.1f/10", m.getRating()) + " | " +
                    "<" + new String(new char[ra]).replace("\0", "=") +
                    new String(new char[10 - ra]).replace("\0", "-") + ">\n");
        }
        return res;
    }

    public static ArrayList<String> getMapList(Gamemode gamemode) {
        Array<Map> maps=Vars.maps.customMaps();
        ArrayList<String> res=new ArrayList<>();
        for (int i=0;i<maps.size;i++){
            Map map = maps.get(i);
            if(gamemode != null && map.rules().mode() != gamemode) continue;
            String m = map.name();
            MDoc md = new MDoc(map);
            int r = (int)md.getRating();
            res.add("[yellow]"+i+"[] | [gray]"+m+"[] | "+String.format("[%s]%d/10[]",r<6 ? r<3 ? "scarlet":"yellow":"green",r));
        }
        return res;
    }

    public static void onMapRemoval(Map removed) {
        data.deleteOne(Filters.eq("_id", MDoc.getID(removed)));
    }

    public static void onMapAddition(Map added) {
        onMapRemoval(added);
    }

    public static void endGame(boolean won) {
        if(played==null) return;
        long playtime = Millis.since(played.started);
        played.inc(Stat.playtime, playtime);
        if(played.getStat(Stat.longest) < playtime) played.set(Stat.longest.name(), playtime);
        if(played.getStat(Stat.shortest) > playtime) played.set(Stat.shortest.name(), playtime);
        played.inc(Stat.played, 1);
        if(won) played.inc(Stat.won, 1);
        if(state.wave > played.getStat(Stat.record)) played.set(Stat.record.name(), state.wave);
    }
}
