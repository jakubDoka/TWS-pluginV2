package theWorst.helpers.gameChangers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import mindustry.Vars;
import mindustry.type.Item;
import mindustry.type.ItemType;

import static theWorst.helpers.gameChangers.Loadout.itemIcons;

public class ItemStack {
    @JsonIgnore public Item item;
    public int amount;

    @JsonCreator public ItemStack(@JsonProperty("item") String item,@JsonProperty("amount") int amount){
        this.item = Loadout.getItemByName(item).get(0);
        this.amount = amount;
    }

    public ItemStack(Item item, int amount){
        this.item = item;
        this.amount = amount;
    }

    @JsonGetter public String getItem() {
        return item.name;
    }

    @Override
    public String toString() {
        int idx = 0;
        for(Item i: Vars.content.items()){
            if(i.type != ItemType.material) continue;
            if( i == item) break;
            idx++;
        }
        return amount + itemIcons[idx];
    }
}
