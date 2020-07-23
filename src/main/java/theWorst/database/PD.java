package theWorst.database;

import arc.util.Time;
import mindustry.entities.type.Player;
import theWorst.Tools.Bundle;
import theWorst.helpers.gameChangers.Pet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.ResourceBundle;

import static theWorst.Tools.Players.sendMessage;

public class PD{
    public Player player;
    public String name;
    public String textColor;

    public Rank sRank, dRank, rank;
    public final HashSet<Rank> obtained = new HashSet<>();
    public final HashSet<Perm> perms = new HashSet<>();
    public final ArrayList<Pet> pets = new ArrayList<>();
    public boolean afk;
    public long id;
    public long joined;
    public long lastAction;
    public long lastMessage;
    public ResourceBundle bundle = Bundle.defaultBundle;

    public void updateName() {
        if (afk) {
            player.name = name + "[gray]<AFK>[]";
        } else if (dRank != null) {
            player.name = name + dRank.getSuffix();
        } else if (sRank != null) {
            player.name = name + sRank.getSuffix();
        }else if (rank != null){
            player.name = name + rank.getSuffix();
        }
        if (rank != null) {
            player.isAdmin = rank.isAdmin;
        }
    }

    public boolean hasThisPerm(Perm perm) {
        return perms.contains(perm);
    }

    public boolean hasPermLevel(Perm perm) {
        return hasPermLevel(perm.value);
    }

    public boolean hasPermLevel(int level) {
        for(Perm p : perms) {
            if (p.value > level) return true;
        }
        return false;
    }

    public boolean isGriefer() {
        return rank == Ranks.griefer;
    }

    public void onAction() {
        if(!afk) return;
        lastAction = Time.millis();
        afk = false;
        updateName();
        sendMessage("afk-is-not", name, Database.AFK);
    }

    public int getHighestPermissionLevel() {
        int highest = -1;
        for( Perm p : perms) {
            if(p.value > highest) highest = p.value;
        }
        return highest;
    }

    public void addPerms(Rank rank) {
        if(rank.permissions == null) return;
        for(String p : rank.permissions) {
            synchronized (perms) {
                perms.add(Perm.valueOf(p));
            }
        }
    }

    public long getPlayTime() {
        return Database.data.getStat(player ,Stat.playTime.name()) + Time.timeSinceMillis(joined);
    }
}
