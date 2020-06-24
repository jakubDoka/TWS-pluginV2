package theWorst.helpers.gameChangers;

import arc.math.Mathf;
import com.fasterxml.jackson.annotation.JsonCreator;
import mindustry.Vars;
import mindustry.type.UnitType;
import org.json.simple.JSONObject;
import theWorst.Config;
import theWorst.Tools;
import theWorst.database.Database;
import theWorst.database.PlayerD;
import theWorst.helpers.Destroyable;
import theWorst.helpers.Displayable;
import theWorst.helpers.Hud;

import java.util.ArrayList;
import java.util.HashMap;

public class Factory implements Destroyable, Displayable {
    static final String configFile = Config.configDir + "factoryConfig.json";
    static final String saveFile = Config.saveDir + "factory.json";
    static FactoryConfig config = new FactoryConfig();
    HashMap<UnitType, Integer> units = new HashMap<>();
    public ArrayList<Thread> threads = new ArrayList<>();
    Loadout loadout;

    public Factory(Loadout loadout){
        this.loadout = loadout;
        for(UnitType u : Vars.content.units()){
            units.put(u, 0);
        }
        loadUnits();
    }

    @Override
    public void destroy() {
        for(Thread t : new ArrayList<>(threads)){
            if(t.building) continue;
            add(t.stack);
            threads.remove(t);
        }
        Hud.addAd("factory-ships-going-back", 10);
    }

    @Override
    public String getMessage(PlayerD pd) {
        return null;
    }

    @Override
    public void onTick() {
        for(Thread t : new ArrayList<>(threads)){
            t.time--;
            if(t.time == 0){
                threads.remove(t);
                t.onArrival();
            }
        }
    }

    public static abstract class Thread {
        public int time;
        public boolean building;
        public UnitStack stack;
        public abstract void onArrival();
    }

    public void loadConfig(){
        config = Tools.loadJackson(configFile, FactoryConfig.class);
        if( config == null) config = new FactoryConfig();
    }

    public void add(UnitStack stack){
        units.put(stack.unit, units.get(stack.unit) + stack.amount);
        saveUnits();
    }

    public void withdraw(UnitStack stack){
        units.put(stack.unit, units.get(stack.unit) - stack.amount);
        saveUnits();
    }

    public UnitStack canWithdraw(UnitStack stack){
        return new UnitStack(stack.unit, Mathf.clamp(stack.amount, 0, units.get(stack.unit)));
    }

    public void loadUnits(){
        Tools.loadJson(saveFile, data -> {
            for(UnitType u : Vars.content.units()){
                units.put(u, ((Long) data.get(u.name)).intValue());
            }
        },this::saveUnits);
    }

    private void saveUnits() {
        JSONObject data = new JSONObject();
        for(UnitType u : Vars.content.units()){
            data.put(u, units.get(u));
        }
        Tools.saveJson(saveFile,data.toJSONString());
    }
}
