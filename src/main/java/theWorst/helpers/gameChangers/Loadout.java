package theWorst.helpers.gameChangers;

import arc.math.Mathf;
import mindustry.Vars;
import mindustry.entities.type.TileEntity;
import mindustry.type.Item;
import mindustry.type.ItemType;
import mindustry.world.blocks.storage.CoreBlock;
import theWorst.Global;
import theWorst.Main;
import theWorst.database.PD;
import theWorst.helpers.Destroyable;
import theWorst.helpers.Displayable;
import theWorst.helpers.Hud;

import java.util.ArrayList;
import java.util.HashMap;

import static theWorst.tools.Formatting.secToTime;
import static theWorst.tools.General.getCore;
import static theWorst.tools.Json.*;
import static theWorst.tools.Players.sendMessage;


public class Loadout implements Displayable, Destroyable {
    public static final String[] itemIcons = {"\uF838", "\uF837", "\uF836", "\uF835", "\uF832", "\uF831", "\uF82F", "\uF82E", "\uF82D", "\uF82C"};
    private final HashMap<Item, Integer> items = new HashMap<>();
    static final String saveFile = Global.saveDir + "loadout.json";
    static final String configFile = Global.configDir + "loadoutConfig.json";
    public static LoadoutConfig config = new LoadoutConfig();
    public ArrayList<Ship> ships = new ArrayList<>();

    @Override
    public String getMessage(PD pd) {
        StringBuilder sb = new StringBuilder();
        for(Ship s: ships){
            String f = "[orange]" + new String[]{"-->", "->-", ">--"}[s.time % 3] + "[]";
            sb.append("[gray]<[]");
            sb.append(s.stack.toString());
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
            if(s.time <= 0){
                s.onArrival();
                ships.remove(s);
            }
        }
    }

    @Override
    public void destroy() {
        for(Ship s : new ArrayList<>(ships)){
            add(s.stack);
            ships.remove(s);
        }
        Hud.addAd("loadout-ships-going-back", 10);
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
            sb.append(new ItemStack(i, items.get(i)).toString());
            sb.append("\n");
        }
        return sb.toString();
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
        config = loadJackson(configFile, LoadoutConfig.class, "loadout");
        if( config == null) config = new LoadoutConfig();

    }

    public static ArrayList<Item> getItemByName(String name){
        ArrayList<Item> items = new ArrayList<>();
        if (name == null) {
            return  items;
        }
        for(Item i : Vars.content.items()){
            if (i.name.startsWith(name)) items.add(i);
        }
        return items;
    }

    public void loadRes(){
        loadJson(saveFile, data -> {
            for(Item i : items.keySet()){
                items.put(i, ((Long) data.get(i.name)).intValue());
            }
        }, this::saveRes);
    }

    public void saveRes(){
        HashMap<String, Integer> data = new HashMap<>();
        for(Item i : items.keySet()){
            data.put(i.name, items.get(i));
        }
        saveSimple(saveFile, data, null);
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

    public static boolean CoreHas(TileEntity core, ArrayList<ItemStack> itemStacks) {
        for(ItemStack i : itemStacks) {
            if(!core.items.has(i.item, i.amount)) return false;
        }
        return true;
    }

}
