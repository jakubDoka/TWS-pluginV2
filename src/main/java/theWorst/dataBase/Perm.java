package theWorst.dataBase;

public enum Perm {
    none(-1),
    normal(),
    high(1),
    higher(4),
    highest(100);


    private final int value;
    Perm(){
        this.value=0;
    }
    Perm(int value){
        this.value=value;
    }
}
