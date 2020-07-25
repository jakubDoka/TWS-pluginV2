package theWorst.database;

import arc.util.Log;

import mindustry.entities.type.Player;
import theWorst.Tools.Bundle;
import theWorst.Tools.Millis;
import theWorst.helpers.gameChangers.Pet;
import theWorst.helpers.gameChangers.ShootingBooster;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.ResourceBundle;

import static theWorst.Tools.Players.sendMessage;
import static theWorst.database.Database.data;
import static theWorst.database.Database.hasEnabled;

public class PD{
    public Player player;
    public String name;
    public String textColor;

    public Rank sRank, dRank, rank;

    public final HashSet<Rank> obtained = new HashSet<>();
    public final HashSet<Perm> perms = new HashSet<>();
    public final ArrayList<Pet> pets = new ArrayList<>();

    public boolean afk, paralyzed;

    public long id;

    public long lastAction;
    public long lastMessage;
    public long joined = lastAction = lastMessage = Millis.now();

    public ResourceBundle bundle = Bundle.defaultBundle;

    public PD() {}


    public PD(Player player, Doc doc) {
        this.player = player;
        name = player.name;
        rank = doc.getRank(RankType.rank);
        textColor = doc.getTextColor();
        id = doc.getId();
        addRank(rank);
    }

    public PD(Player player) {
        this.player = player;
        paralyzed = true;
        rank = Ranks.paralyzed;
        name = player.name;
        id = DataHandler.paralyzedId;
    }

    public Doc getDoc() {
        return data.getDoc(id);
    }

    public void updateName() {
        if (afk) {
            player.name = name + "[gray]<AFK>[]";
        } else if (dRank != null && dRank.displayed) {
            player.name = name + dRank.suffix();
        } else if (sRank != null && sRank.displayed) {
            player.name = name + sRank.suffix();
        } else if (rank != null){
            player.name = name + rank.suffix();
        }
        if (rank != null) {
            player.isAdmin = rank.isAdmin;
        }
    }

    public boolean hasThisPerm(Perm perm) {
        return !(paralyzed || !perms.contains(perm));
    }

    public boolean hasPermLevel(Perm perm) {
        return hasPermLevel(perm.value);
    }

    public boolean hasPermLevel(int level) {
        if(paralyzed) return false;
        for(Perm p : perms) {
            if (p.value >= level) return true;
        }
        return false;
    }

    public boolean isGriefer() {
        return rank == Ranks.griefer || paralyzed;
    }

    public void onAction() {
        if(!afk) return;
        lastAction = Millis.now();
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

    void addPets(Rank rank){
        if(data.contains(id, "settings", Setting.pets.name())) return;
        if(rank != null && rank.pets != null){
            for(String pet : rank.pets){
                Pet found = ShootingBooster.pets.get(pet);
                if(found == null){
                    Log.info("missing pet :" + pet);
                } else {
                    synchronized (pets) {
                        pets.add(new Pet(found));
                    }
                }
            }
        }
    }

    public long getPlayTime() {
        return Database.data.getStat(id,Stat.playTime.name()) + Millis.since(joined);
    }

    public void addRank(Rank rank) {
        synchronized (obtained) {
            obtained.add(rank);
        }
        addPerms(rank);
        addPets(rank);
    }

    public void removeRank(Rank rank) {
        obtained.remove(rank);
        for(String s : rank.permissions) {
            perms.remove(Perm.valueOf(s));
        }
    }
}
