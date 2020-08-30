package theWorst.helpers.gameChangers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.type.Item;
import mindustry.type.ItemType;
import mindustry.type.UnitType;

import java.util.ArrayList;
import java.util.HashMap;

public class FactoryConfig {
    public int shipCount = 3;
    public int shipCapacity = 3000;
    public int shipSpeed = 60 * 3;
    public int dropZoneRadius = 40;
    @JsonSerialize(contentAs = PriceData.class)
    public HashMap<UnitType, PriceData> prices = new HashMap<UnitType, PriceData>(){{
        put(UnitTypes.eruptor, new PriceData());
    }};

    public FactoryConfig(){}

    @JsonCreator
    public FactoryConfig(
            @JsonProperty("shipCapacity") int shipCapacity,
            @JsonProperty("shipCount") int shipCount,
            @JsonProperty("shipSpeed") int shipSpeed,
            @JsonProperty("dropZoneRadius") int dropZoneRadius,
            @JsonProperty("prices") HashMap<String, PriceData> prices){
        this.shipCapacity = shipCapacity;
        this.shipCount = shipCount;
        this.shipSpeed = shipSpeed;
        this.dropZoneRadius = dropZoneRadius;
        this.prices.clear();
        for(String p : prices.keySet()){
            if(Factory.getUnitByName(p).isEmpty()) continue;
            this.prices.put(Factory.getUnitByName(p).get(0), prices.get(p));
        }
    }

    public static class PriceData {
        @JsonIgnore public ArrayList<ItemStack> items = new ArrayList<>();
        public float buildTime;
        public int size;

        public PriceData() {
            for(Item i : Vars.content.items()){
                if(i.type == ItemType.resource) continue;
                items.add(new ItemStack(i, 10));
            }
            buildTime = 20;
            size = 2;
        }

        @JsonCreator public PriceData(
                @JsonProperty("items") HashMap<String, Integer> items,
                @JsonProperty("buildTime") float buildTime,
                @JsonProperty("size") int size){
            this.size = size;
            this.buildTime = buildTime;
            for(String s : items.keySet()){
                if(Loadout.getItemByName(s).isEmpty()) continue;
                this.items.add(new ItemStack(Loadout.getItemByName(s).get(0), items.get(s)));
            }
        }

        @JsonGetter public HashMap<String, Integer> getItems(){
            HashMap<String, Integer> res = new HashMap<>();
            for(ItemStack i : items) {
                res.put(i.item.name, i.amount);
            }
            return res;
        }
    }
}
