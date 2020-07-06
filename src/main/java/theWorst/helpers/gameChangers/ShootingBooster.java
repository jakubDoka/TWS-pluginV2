package theWorst.helpers.gameChangers;

import arc.Events;
import arc.util.Time;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import org.json.simple.JSONArray;
import theWorst.Global;
import theWorst.database.Database;
import theWorst.database.PlayerD;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import static mindustry.Vars.playerGroup;
import static theWorst.Tools.Commands.logInfo;
import static theWorst.Tools.Json.loadJson;
import static theWorst.Tools.Json.saveJson;

public class ShootingBooster {
    static final String weaponFile = Global.configDir + "weapons.json";
    static HashMap<String , Weapon> weapons = new HashMap<>();
    static HashMap<String , Weapon> playerWeapons = new HashMap<>();
    HashMap<String, ShootingD> data = new HashMap<>();
    public ShootingBooster(){
        Events.on(EventType.PlayerJoin.class, e-> data.put(e.player.uuid, new ShootingD()));

        Events.on(EventType.PlayerLeave.class, e-> data.remove(e.player.uuid));

        Events.on(EventType.UnitDestroyEvent.class, e->{
            if(e.unit instanceof Player){
                ShootingD sd =  data.get(((Player) e.unit).uuid);
                if(sd == null) return;
                sd.ammo = 0;
            }
        });

        Events.on(EventType.Trigger.update,() -> playerGroup.all().forEach(player -> {
            PlayerD pd = Database.getData(player);
            if (!pd.pets.isEmpty()) {
                for(Pet p : pd.pets){
                    p.update(player, pd.pets);
                }
            }
            ShootingD sd = data.get(player.uuid);
            if(sd == null) return;
            ItemStack items = player.item();
            if (items == null || items.item == null) return;
            if(sd.item != items.item){
                sd.item = items.item;
                sd.ammo = 0;
            }

            Weapon weapon = playerWeapons.get(sd.item.name);
            if(weapon == null) return;

            if(sd.time > weapon.fireRate){
                sd.loaded = true;
                sd.time = 0f;
            }
            if(!sd.loaded) {
                sd.time += Time.delta();
                return;
            }

            if (!player.isShooting()) return;

            if (sd.ammo == 0) {
                if(items.amount < weapon.consumes){
                    return;
                }
                sd.ammo += weapon.ammoMultiplier;
                items.amount -= weapon.consumes;
            }
            sd.loaded = false;
            sd.ammo--;
            weapon.playerShoot(player);
        }));
        loadWeapons();
    }

    public static void loadWeapons(){
        weapons.clear();
        playerWeapons.clear();
        loadJson(weaponFile,data -> {
            ObjectMapper mapper = new ObjectMapper();
            Weapon[] weapons = mapper.readValue(((JSONArray)data.get("weapons")).toJSONString(),Weapon[].class);
            for(Weapon w : weapons) {
                if (w.item != null) playerWeapons.put(w.item.name, w);
                ShootingBooster.weapons.put(w.name, w);
            }
        },ShootingBooster::defaultWeapons);
    }

    public static void defaultWeapons(){
        try {
            String data = new ObjectMapper().writeValueAsString(new HashMap<String, ArrayList<Weapon>>(){{
                put("weapons",new ArrayList<Weapon>(){{ add(new Weapon()); }});
            }});
            saveJson(weaponFile, data);
            logInfo("files-default-config-created","weapons", weaponFile);
            loadWeapons();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            logInfo("files-default-config-failed","weapons", weaponFile);
        }
    }

    static class ShootingD {
        Item item;
        int ammo;
        float time;
        boolean loaded;
    }
}
