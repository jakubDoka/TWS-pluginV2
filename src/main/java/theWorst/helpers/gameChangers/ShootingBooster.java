package theWorst.helpers.gameChangers;

import arc.Events;
import arc.util.Log;
import arc.util.Time;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mindustry.game.EventType;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import theWorst.Config;
import theWorst.database.Database;
import theWorst.database.PlayerD;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import static mindustry.Vars.playerGroup;
import static theWorst.Tools.Commands.logInfo;
import static theWorst.Tools.Json.loadJson;
import static theWorst.Tools.Json.saveJson;

public class ShootingBooster {
    static final String weaponFile = Config.configDir + "weapons.json";
    static HashMap<String , Weapon> weapons = new HashMap<>();
    static HashMap<String , Weapon> playerWeapons = new HashMap<>();
    HashMap<String, ShootingD> data = new HashMap<>();
    public ShootingBooster(){
        Events.on(EventType.PlayerJoin.class, e-> data.put(e.player.uuid, new ShootingD()));

        Events.on(EventType.PlayerLeave.class, e-> data.remove(e.player.uuid));

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
            if(sd.item == null){
                sd.item = items.item;
            }
            Weapon weapon = playerWeapons.get(sd.item.name);
            if(weapon == null) {
                sd.item = items.item;
                return;
            }
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
                if(items.item != sd.item){
                    sd.item = items.item;
                }
                sd.ammo += weapon.ammoEfficiency;
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
            try {
                ObjectMapper mapper = new ObjectMapper();
                Weapon[] weapons = mapper.readValue(((JSONArray)data.get("weapons")).toJSONString(),Weapon[].class);
                for(Weapon w : weapons) {
                    if(!w.valid) continue;
                    if (w.item != null) playerWeapons.put(w.item.name, w);
                    ShootingBooster.weapons.put(w.name, w);
                }
            } catch (IOException ex){
                ex.printStackTrace();
            }
        },()->{

            try {
                ObjectMapper mapper = new ObjectMapper();

                JSONObject weapon = (JSONObject) new JSONParser().parse(mapper.writeValueAsString(new Weapon()));
                JSONObject data = new JSONObject();
                JSONArray array = new JSONArray();
                array.add(weapon);
                data.put("weapons",array);
                saveJson(weaponFile, data.toJSONString());
                logInfo("files-default-config-created","weapons", weaponFile);
            } catch (JsonProcessingException | ParseException e) {
                e.printStackTrace();
            }
        });
    }

    static class ShootingD {
        Item item;
        int ammo;
        float time;
        boolean loaded;
    }
}
