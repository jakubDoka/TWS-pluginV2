package theWorst.dataBase;

public enum Perm {
    none(-1),
    normal(),
    high(1),
    higher(4),
    highest(100),

    loadout,
    factory,
    restart,
    change,
    gameOver,
    build,
    destruct,
    suicide,
    colorCombo,
    antiGrief,
    skip;

    private final int value;
    public String description=null;
    Perm(){
        this.value=0;
    }
    Perm(int value){
        this.value=value;
    }
    public int getValue() {
        return value;
    }
}
