package theWorst.database;

import arc.graphics.Color;
import arc.util.Time;
import mindustry.content.Items;

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
    candidate(Items.thorium.color,Perm.higher),
    admin(Color.blue,Perm.higher){
        {
            displayed=true;
            isAdmin=true;
        }
    },
    pluginDev(Color.olive,Perm.highest){
        {
            description="Its me Mlokis.";
            isAdmin=true;
        }
    },
    owner(Color.gold,Perm.highest){
        {
            isAdmin=true;
        }
    };

    Color color;
    public Perm permission=Perm.normal;
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

    Rank(Color color, Perm permission){
        this.color=color;
        this.permission=permission;
    }

    public String getSuffix() {
        return displayed ? "[#"+color+"]<"+name()+">[]":"";
    }

    public String getName() {
        return  "[#"+color+"]<"+name()+">[]";
    }
}
