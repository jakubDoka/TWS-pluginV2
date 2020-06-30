package theWorst.helpers.gameChangers;

import mindustry.type.UnitType;

public class UnitStack {
    public UnitType unit;
    public int amount;

    public UnitStack(UnitType unit, int amount){
        this.unit = unit;
        this.amount = amount;
    }

    @Override
    public String toString(){
        return amount + " " + unit.name;
    }
}
