package theWorst.helpers.gameChangers;

import arc.Events;

import arc.util.Time;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import theWorst.Global;
import theWorst.database.Database;
import theWorst.database.PD;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static mindustry.Vars.playerGroup;
import static theWorst.tools.Commands.logInfo;
import static theWorst.tools.Json.loadSimpleHashmap;
import static theWorst.tools.Json.saveSimple;

public class ShootingBooster {
    static final String weaponFile = Global.configDir + "weapons.json";
    static final String petFile = Global.configDir + "pets.json";

    static HashMap<String , Weapon> weapons = new HashMap<>();
    static HashMap<String , Weapon> playerWeapons = new HashMap<>();
    public static HashMap<String, Pet> pets = new HashMap<>();
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
        Set<Player> uniquePlayers = new HashSet<Player>();
        Events.on(EventType.Trigger.update,() -> {
          uniquePlayers.clear();
          playerGroup.all().forEach(player -> {
            uniquePlayers.add(player);
          });

          uniquePlayers.forEach(player -> {
            PD pd = Database.getData(player);
            if (pd == null) return;
            if (!pd.pets.isEmpty()) {
                for(Pet p : new ArrayList<>(pd.pets)){
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
          });
        });

        loadWeapons();
        loadPets();
    }

    public static void loadWeapons(){
        weapons.clear();
        playerWeapons.clear();
        HashMap<String, Weapon[]> weapons = loadSimpleHashmap(weaponFile, Weapon[].class, ShootingBooster::defaultWeapons);
        if(weapons == null) return;
        Weapon[] wp = weapons.get("weapons");
        if(wp == null) return;
        for(Weapon w : wp) {
            if (w.item != null) playerWeapons.put(w.item.name, w);
            ShootingBooster.weapons.put(w.name, w);
        }
    }

    public static void defaultWeapons(){
        saveSimple(weaponFile,new HashMap<String, ArrayList<Weapon>>(){{
            put("weapons",new ArrayList<Weapon>(){{ add(new Weapon()); }});
        }}, "weapons");
        loadWeapons();
    }

    public static void loadPets(){
        pets.clear();
        HashMap<String, Pet[]> pets = loadSimpleHashmap(petFile, Pet[].class, ShootingBooster::defaultPets);
        if(pets == null) return;
        Pet[] pts = pets.get("pets");
        if(pts == null) return;
        for(Pet p : pts) {
            if(p.trail == null) {
                logInfo("pet-invalid-trail", p.name);
                continue;
            }
            ShootingBooster.pets.put(p.name,p);
        }

    }

    public static void defaultPets(){
        saveSimple(petFile, new HashMap<String, ArrayList<Pet>>(){{
            put("pets",new ArrayList<Pet>(){{ add(new Pet()); }});
        }},"pets");

    }

    static class ShootingD {
        Item item;
        int ammo;
        float time;
        boolean loaded;
    }
}
