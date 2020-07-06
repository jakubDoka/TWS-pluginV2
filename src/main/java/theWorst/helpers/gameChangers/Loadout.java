package theWorst.helpers.gameChangers;

import arc.math.Mathf;
import mindustry.Vars;
import mindustry.type.Item;
import mindustry.type.ItemType;
import mindustry.world.blocks.storage.CoreBlock;
import org.json.simple.JSONObject;
import theWorst.Global;
import theWorst.Main;
import theWorst.database.PlayerD;
import theWorst.helpers.Destroyable;
import theWorst.helpers.Displayable;
import theWorst.helpers.Hud;

import java.util.ArrayList;
import java.util.HashMap;

import static theWorst.Tools.Formatting.secToTime;
import static theWorst.Tools.General.getCore;
import static theWorst.Tools.Json.*;
import static theWorst.Tools.Players.sendMessage;


public class Loadout implements Displayable, Destroyable {
    public static final String[] itemIcons = {"\uF838", "\uF837", "\uF836", "\uF835", "\uF832", "\uF831", "\uF82F", "\uF82E", "\uF82D", "\uF82C"};
    private final HashMap<Item, Integer> items = new HashMap<>();
    static final String saveFile = Global.saveDir + "loadout.json";
    static final String configFile = Global.configDir + "loadoutConfig.json";
    public static LoadoutConfig config = new LoadoutConfig();
    public ArrayList<Ship> ships = new ArrayList<>();

    @Override
    public String getMessage(PlayerD pd) {
        StringBuilder sb = new StringBuilder();
        for(Ship s: ships){
            String f = "[gray]" + new String[]{"-->", "->-", ">--"}[s.time % 3] + "[]";
            sb.append("[gray]<[]");
            sb.append(stackToString(s.stack));
            sb.append(f).append(secToTime(s.time)).append(f);
            sb.append("\uf869");
            sb.append("[gray]>[]");
        }
        for(int i = ships.size(); i < config.shipCount; i++){
            sb.append("[gray]<[green]free[]>[]");
        }
        return sb.toString();
    }

    @Override
    public void onTick() {
        for(Ship s : new ArrayList<>(ships)){
            s.time--;
            if(s.time == 0){
                s.onArrival();
                ships.remove(s);
            }
        }
    }

    @Override
    public void destroy() {
        for(Ship s : new ArrayList<>(ships)){
            sendMessage("loadout-ships-going-back", stackToString(s.stack));
            ships.remove(s);
        }
    }

    public static abstract class Ship {
        public int time;
        public ItemStack stack;
        public abstract void onArrival();
    }

    public Loadout(){

        for(Item i : Vars.content.items()){
            if(i.type != ItemType.material) continue;
            items.put(i, 0);
        }
        loadRes();
        loadConfig();
        Hud.addDisplayable(this);
        Main.addDestroyable(this);
    }

    public String info(){
        StringBuilder sb = new StringBuilder("[orange]==LOADOUT==[]\n\n");
        for(Item i : items.keySet()){
            sb.append(stackToString(new ItemStack(i, items.get(i))));
            sb.append("\n");
        }
        return sb.toString();
    }

    public static String stackToString(ItemStack stack){
        int idx = 0;
        for(Item i: Vars.content.items()){
            if(i.type != ItemType.material) continue;
            if( i == stack.item) break;
            idx++;
        }
        return stack.amount + itemIcons[idx];
    }

    public void launchAll(){
        CoreBlock.CoreEntity core = getCore();
        if(core == null) return;
        for(Item i : Vars.content.items()){
            if(i.type == ItemType.resource) continue;
            add(new ItemStack(i, core.items.get(i)));
        }
        core.items.clear();
        Hud.addAd("loadout-all-launched", 10, "!green", "!gray");
    }

    public static void loadConfig(){
        config = loadJackson(configFile, LoadoutConfig.class);
        if( config == null) config = new LoadoutConfig();

    }

    public static Item getItemByName(String name){
        for(Item i : Vars.content.items()){
            if (i.name.equals(name)) return i;
        }
        return null;
    }

    public void loadRes(){
        loadJson(saveFile, data -> {
            for(Item i : items.keySet()){
                items.put(i, ((Long) data.get(i.name)).intValue());
            }
        }, this::saveRes);
    }

    public void saveRes(){
        JSONObject data = new JSONObject();
        for(Item i : items.keySet()){
            data.put(i.name, items.get(i));
        }
        saveJson(saveFile,data.toJSONString());
    }

    public boolean has(ItemStack stack){
        return items.get(stack.item) >= stack.amount;
    }

    public boolean has(ArrayList<ItemStack> stacks){
        for(ItemStack stack : stacks){
            if(!has(stack)) return false;
        }
        return true;
    }

    public int getAmount(Item item){
        return items.get(item);
    }

    public void add(ItemStack stack){
        items.put(stack.item, getAmount(stack.item) + stack.amount);
        saveRes();
    }

    public void add(ArrayList<ItemStack> stacks){
        for(ItemStack stack : stacks){
            add(stack);
        }
    }

    public void withdraw(ItemStack stack){
        items.put(stack.item, getAmount(stack.item) - stack.amount);
        saveRes();
    }

    public ItemStack canWithdraw(ItemStack stack){
        return new ItemStack(stack.item, Mathf.clamp(stack.amount, 0, getAmount(stack.item)));
    }

    public ItemStack canAdd(ItemStack stack){
        int stored = getAmount(stack.item);
        int overflow = stored + stack.amount - config.capacity;
        ItemStack copy = new ItemStack(stack.item, stack.amount);
        if(overflow > 0){
           copy.amount -= overflow;
        }
        return copy;
    }

    public ArrayList<ItemStack> canAdd(ArrayList<ItemStack> stacks){
        ArrayList<ItemStack> res = new ArrayList<>();
        for(ItemStack stack : stacks){
            res.add(canAdd(stack));
        }
        return res;
    }


}
