package theWorst.database;

public enum Perm {
    normal(),
    high(1),
    higher(2),
    highest(3),

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

    public final int value;
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
}
