package theWorst.database;

import arc.graphics.Color;
import arc.util.Time;
import mindustry.content.Items;

import java.util.Arrays;
import java.util.HashSet;

public enum Rank implements java.io.Serializable{
    griefer(Color.pink,Perm.none),
    newcomer(Items.copper.color){
        {
            displayed=false;
        }
    },
    verified(Items.titanium.color,Perm.high){
        {
            displayed=false;
        }
    },
    candidate(Items.thorium.color,Perm.higher,Perm.antiGrief),
    admin(Color.blue,Perm.higher){
        {
            displayed=true;
            isAdmin=true;
        }
    };

    public static Rank defaultRank = newcomer;
    Color color;
    public HashSet<Perm> permissions = new HashSet<>();
    boolean displayed=true;
    public boolean isAdmin=false;
    String description="missing description";

    public int getValue() {
        int value=0;
        for(Rank r: Rank.values()){
            if(r==this){
                return value;
            }
            value++;
        }
        return -1;
    }

    Rank(Color color){
        this.color=color;
    }

    Rank(Color color, Perm ...permission){
        this.color=color;
        this.permissions.addAll(Arrays.asList(permission));
    }

    public String getSuffix() {
        return displayed ? "[#"+color+"]<"+name()+">[]":"";
    }

    public String getName() {
        return  "[#"+color+"]<"+name()+">[]";
    }
}
