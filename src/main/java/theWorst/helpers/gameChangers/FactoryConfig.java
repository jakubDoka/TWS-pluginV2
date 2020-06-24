package theWorst.helpers.gameChangers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import mindustry.Vars;
import mindustry.type.Item;
import mindustry.type.ItemType;

import java.util.HashMap;

public class FactoryConfig {
    public int shipCount = 3;
    public int shipCapacity = 3000;
    public int shipSpeed = 60 * 3;
    public HashMap<String,HashMap<String,Integer>> prices = new HashMap<String,HashMap<String,Integer>>(){{
            put("eruptor", createPrice());
    }};

    public FactoryConfig(){}

    @JsonCreator
    public FactoryConfig(
            @JsonProperty("shipCapacity") int shipCapacity,
            @JsonProperty("shipCount") int shipCount,
            @JsonProperty("shipSpeed") int shipSpeed,
            @JsonProperty("prices") HashMap<String,HashMap<String,Integer>> prices
    ){
        this.shipCapacity = shipCapacity;
        this.shipCount = shipCount;
        this.shipSpeed = shipSpeed;
        this.prices = prices;
    }

    private HashMap<String, Integer> createPrice(){
        HashMap<String, Integer> price = new HashMap<>();
        for(Item i : Vars.content.items()){
            if(i.type == ItemType.resource) continue;
            price.put(i.name, 0);
        }
        price.put("buildTime", 10);
        price.put("speedMultiplier", 4);
        price.put("unitSize", 2);
        return price;
    }
}
