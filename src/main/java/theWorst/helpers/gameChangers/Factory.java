package theWorst.helpers.gameChangers;

import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Vec2;

import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.net.Administration;
import mindustry.type.UnitType;
import theWorst.Global;
import theWorst.Main;
import theWorst.tools.Formatting;
import theWorst.tools.Players;
import theWorst.database.PD;
import theWorst.helpers.Destroyable;
import theWorst.helpers.Displayable;
import theWorst.helpers.Hud;

import java.util.ArrayList;
import java.util.HashMap;

import static theWorst.tools.Formatting.secToTime;
import static theWorst.tools.Json.*;

public class Factory implements Destroyable, Displayable {
    static final String configFile = Global.configDir + "factoryConfig.json";
    static final String saveFile = Global.saveDir + "factory.json";
    public static FactoryConfig config = new FactoryConfig();
    HashMap<UnitType, Integer> units = new HashMap<>();
    public ArrayList<Thread> threads = new ArrayList<>();
    Loadout loadout;
    public float dropZoneRotation = 0;

    public Factory(Loadout loadout){
        Events.on(EventType.ServerLoadEvent.class, e-> Vars.netServer.admins.addActionFilter(act-> {
            Player player = act.player;
            if (player == null) return true;
            if(act.type == Administration.ActionType.breakBlock) return true;
            for (Thread t : threads) {
                if (t.building) continue;
                if (new Vec2(t.pos).sub(new Vec2(act.tile.getX(), act.tile.getY())).len() < config.dropZoneRadius) {
                    Players.sendErrMessage(player, "factory-drop-zone");
                    return false;
                }
            }
            return true;
        }));

        Events.on(EventType.Trigger.update, ()->{
           for(Thread t : threads){
               if(t.building) continue;
               Vec2 pos = new Vec2(t.pos).add(new Vec2(config.dropZoneRadius, 0).rotate(dropZoneRotation));
               Call.onEffectReliable(Fx.fire, pos.x, pos.y, 0f, Color.white);
           }
           dropZoneRotation += 20 * Time.delta();
        });

        this.loadout = loadout;
        for(UnitType u : Vars.content.units()){
            units.put(u, 0);
        }
        loadUnits();
        loadConfig();
        Hud.addDisplayable(this);
        Main.addDestroyable(this);
    }

    public static ArrayList<UnitType> getUnitByName(String name) {
        ArrayList<UnitType> units = new ArrayList<>();
        if(name == null) return units;
        for(UnitType u : Vars.content.units()){
            if(u.name.startsWith(name)) {
                units.add(u);
            };
        }
        return units;
    }

    @Override
    public void destroy() {
        if(threads.isEmpty()) return;
        for(Thread t : new ArrayList<>(threads)){
            if(t.building) continue;
            add(t.stack);
            threads.remove(t);
        }
        Hud.addAd("factory-ships-going-back", 10);
    }

    @Override
    public String getMessage(PD pd) {
        StringBuilder sb = new StringBuilder();
        for(Thread t: threads){
            sb.append("[gray]<[]");
            sb.append(t.stack.toString());
            if(t.building){
                String f = "[orange]" + new String[]{"<--", "-<-", "--<"}[t.time % 3] + "[]";
                sb.append(f).append(secToTime(t.time)).append(f);
                sb.append("\uf851");
            } else {
                String f = "[orange]" + new String[]{"-->", "->-", ">--"}[t.time % 3] + "[]";
                sb.append(f).append(secToTime(t.time)).append(f);
                sb.append("\uf869");
            }
            sb.append("[gray]>[]");
        }
        for(int i = threads.size(); i < config.shipCount; i++){
            sb.append("[gray]<[green]free[]>[]");
        }
        return sb.toString();
    }

    @Override
    public void onTick() {
        for(Thread t : new ArrayList<>(threads)){
            t.time--;
            if(t.time <= 0){
                threads.remove(t);
                t.onFinish();
            }
        }
    }

    public void build(UnitStack stack) {
        FactoryConfig.PriceData data = config.prices.get(stack.unit);
        for(ItemStack i : data.items){
            loadout.withdraw(new ItemStack(i.item, i.amount * stack.amount));
        }
    }

    public static abstract class Thread {
        public int time;
        public boolean building;
        public UnitStack stack;
        public Vec2 pos;
        public abstract void onFinish();
    }

    public static void loadConfig(){
        config = loadJackson(configFile, FactoryConfig.class, "factory");
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

    public boolean canBuild(UnitStack stack){
        for(ItemStack i : config.prices.get(stack.unit).items){
            if(!loadout.has(new ItemStack(i.item, i.amount * stack.amount))) {
                return false;
            }
        }
        return true;
    }

    public UnitStack canAfford(UnitType unit) {
        UnitStack stack = new UnitStack(unit , 1);
        while (canBuild(stack)){
            stack.amount++;
        }
        stack.amount--;
        return stack;
    }

    public void loadUnits(){
        loadJson(saveFile, data -> {
            for(UnitType u : Vars.content.units()){
                units.put(u, ((Long) data.get(u.name)).intValue());
            }
        },this::saveUnits);
    }

    private void saveUnits() {
        saveSimple(saveFile, units, null);
    }

    public String price(UnitStack stack){
        FactoryConfig.PriceData pd = config.prices.get(stack.unit);
        StringBuilder sb = new StringBuilder();
        sb.append("[orange]==").append(stack.amount).append(" ");
        sb.append(stack.unit.name.toUpperCase()).append("==[]\n\n");
        for(ItemStack i : pd.items){
            int have = loadout.getAmount(i.item);
            int price = i.amount * stack.amount;
            sb.append(have < price ? "[scarlet]":"[green]");
            sb.append(have).append("[]/").append(new ItemStack(i.item, price).toString());
            sb.append("[]\n");
        }
        int buildTime = (int)(stack.amount * pd.buildTime);
        sb.append("[gray]total build time:[]").append(Formatting.secToTime(buildTime)).append("\n");
        sb.append("[gray]total size:[]").append(stack.amount * pd.size);
        return sb.toString();
    }

    public String info(){
        StringBuilder sb = new StringBuilder();
        sb.append("[orange]==FACTORY INFO==[]\n\n");
        for(UnitType u : config.prices.keySet()){
            sb.append(new UnitStack(u, units.get(u)).toString()).append("\n");
        }
        return sb.toString();
    }
}
