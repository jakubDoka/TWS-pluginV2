package theWorst.database;

public enum Perm {
    none(-1),
    normal(),
    high(1),
    higher(4),
    highest(100),

    loadout(Stat.loadoutVotes),
    factory(Stat.factoryVotes),
    restart,
    change,
    gameOver,
    build,
    destruct,
    suicide,
    colorCombo,
    antiGrief,
    skip,
    coreBuild(Stat.buildCoreVotes);

    private final int value;
    public String description=null;
    public Stat relation = null;
    Perm(Stat relation){
        this.relation = relation;
        this.value=0;
    }
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
