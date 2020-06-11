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
    static Map currentMap=maps.all().first();
    public static MapD played;
    static MongoOperations data = new MongoTemplate(Database.client, Config.dbName);

    public MapManager(){
        Events.on(EventType.GameOverEvent.class, e -> endGame(e.winner==Team.sharded));

        Events.on(EventType.PlayEvent.class, e-> {
            currentMap=world.getMap();
            String name=currentMap.name();
            played = data.findOne(new Query(where("name").is(name)),MapD.class);
            if(played == null){
                played = new MapD(currentMap);
                data.save(played);
            }
            played.started = Time.millis();
            Hud.addAd(getWaveInfo(),30);
        });
    }

    public static MapD getData(String filename){
        return data.findOne(new Query(where("fileName").is(filename)),MapD.class);
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
            MapD md = getData(map.file.name());
            int r= md==null ? 5:(int)md.getRating();
            res.add("[yellow]"+i+"[] | [gray]"+m+"[] | "+String.format("[%s]%d/10[]",r<6 ? r<3 ? "scarlet":"yellow":"green",r));
        }
        return res;
    }

    public static String getMapStats(String identifier){
        Map map= identifier==null ? world.getMap():Tools.findMap(identifier);
        if(map==null){
            return null;
        }
        MapD md = getData(map.file.name());
        if(md == null) {
            md =new MapD(currentMap);
            data.save(md);
        }
        return "[orange]--MAP STATS--[]\n\n" + md.toString();
    }

    public String getMapRules(String identifier){
        Map map= identifier==null ? world.getMap():Tools.findMap(identifier);
        if(map==null){
            return null;
        }
        MapD md = getData(map.file.name());
        if(md == null) {
            md =new MapD(currentMap);
            data.save(md);
        }
        Rules rules=map.rules();

        return "[orange]--MAP RULES--[]\n\n"+
                String.format("[gray]name:[] %s\n" +
                        "[gray]mode:[] %s\n" +
                        "[gray]first air-wave:[] "+(md.hasNoAirWave() ? "none" : md.firstAirWave)+"\n " +
                        "[orange]Multipliers[]\n" +
                        "[gray]build cost:[] %.2f\n"+
                        "[gray]build speed:[] %.2f\n"+
                        "[gray]block health:[] %.2f\n"+
                        "[gray]unit build speed:[] %.2f\n"+
                        "[gray]unit damage:[] %.2f\n"+
                        "[gray]unit health:[] %.2f\n"+
                        "[gray]player damage:[] %.2f\n"+
                        "[gray]player health:[] %.2f\n"+
                "",map.name(),rules.mode().name(),
                rules.buildCostMultiplier,
                rules.buildSpeedMultiplier,
                rules.blockHealthMultiplier,
                rules.unitBuildSpeedMultiplier,
                rules.unitDamageMultiplier,
                rules.unitHealthMultiplier,
                rules.playerDamageMultiplier,
                rules.playerHealthMultiplier);
    }



    public void endGame(boolean won) {
        if(currentMap==null){
            return;
        }
        if(played==null) return;
        played.playtime+=Time.timeSinceMillis(played.started);
        played.timesPlayed++;
        if(won) played.timesWon++;
        if(state.wave > played.waveRecord) played.waveRecord=state.wave;
        currentMap=null;
    }

    public String getWaveInfo(){
        if(played.hasNoAirWave()) return "No air waves on this map.";
        return "Air enemy starts at wave [orange]" + played.firstAirWave + "[] !";
    }
}
