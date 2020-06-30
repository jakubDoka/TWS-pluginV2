package theWorst.helpers.gameChangers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import mindustry.type.Item;

public class ItemStack {
    @JsonIgnore public Item item;
    public int amount;

    @JsonCreator public ItemStack(@JsonProperty("item") String item,@JsonProperty("amount") int amount){
        this.item = Loadout.getItemByName(item);
        this.amount = amount;
    }

    public ItemStack(Item item, int amount){
        this.item = item;
        this.amount = amount;
    }

    @JsonGetter public String getItem() {
        return item.name;
    }

}
