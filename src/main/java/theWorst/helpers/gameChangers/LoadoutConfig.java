package theWorst.helpers.gameChangers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class LoadoutConfig {
    public int capacity = 1000 * 1000 * 10;
    public int shipCount = 3;
    public int shipCapacity = 3000;
    public int shipSpeed = 60 * 3;

    public LoadoutConfig(){}

    @JsonCreator public LoadoutConfig(
            @JsonProperty("capacity") int capacity,
            @JsonProperty("shipCapacity") int shipCapacity,
            @JsonProperty("shipCount") int shipCount,
            @JsonProperty("shipSpeed") int shipSpeed
            ){
        this.capacity = capacity;
        this.shipCapacity = shipCapacity;
        this.shipCount = shipCount;
        this.shipSpeed = shipSpeed;
    }
}
