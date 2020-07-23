package theWorst.database;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static theWorst.Tools.Commands.logInfo;
import static theWorst.Tools.Players.getCountryCode;
import static theWorst.Tools.Players.getTranslation;
import static theWorst.database.Database.*;

public class Rank implements Serializable {
    static final long hour = 1000 * 60 * 60;
    public String name = "noName";
    public String color = "red";
    public boolean displayed = true;
    public boolean isAdmin = false;
    public int value=0;
    public HashSet<String> permissions = new HashSet<String>(){{ add(Perm.normal.name());}};
    public HashSet<String> linked = null;
    public HashMap<String,String> description = null;
    public HashMap<String, HashMap<String,Integer>> quests = null;
    public ArrayList<String> pets = null;

    public Rank() {}

    @JsonCreator public Rank(
            @JsonProperty("isAdmin") boolean isAdmin,
            @JsonProperty("name") String name,
            @JsonProperty("color") String color,
            @JsonProperty("description") HashMap<String,String> description,
            @JsonProperty("value") int value,
            @JsonProperty("permissions") HashSet<String> permissions,
            @JsonProperty("linked") HashSet<String> linked,
            @JsonProperty("quests") HashMap<String, HashMap<String,Integer>> quests,
            @JsonProperty("pets")ArrayList<String> pets){

        this.isAdmin = isAdmin;
        if(name == null) logInfo("missing-name","special rank");
        else this.name = name;
        if(color == null) logInfo("special-missing-color",name);
        else this.color = color;
        this.description = description;
        this.value = value;
        if(permissions != null) this.permissions = permissions;
        this.linked = linked;
        this.quests = quests;
        this.pets = pets;
    }

    @JsonIgnore public boolean isPermanent(){
        return quests == null && linked == null;
    }

    public boolean condition(DataHandler.Doc tested, PD pd){
        if (pd.obtained.contains(this)) return true;
        if(linked != null) {
            for (String l : linked){
                Rank other = Ranks.special.get(l);
                if (pd.obtained.contains(other)) continue;
                if(!other.condition(tested, pd)) return false;
            }
        }
        if(quests == null){
            return linked != null;
        }
        for(String stat : quests.keySet()){
            HashMap<String,Integer> quest = quests.get(stat);
            Long val = tested.getStat(stat);
            long played = tested.getStat(Stat.playTime)/hour;
            if(played == 0 ){
                played = 1;
            }
            if(quest.containsKey(Mod.required.name()) && val < quest.get(Mod.required.name())) return false;
            if(quest.containsKey(Mod.frequency.name()) && quest.get(Mod.frequency.name()) > val/played) return false;
            if(quest.containsKey(Mod.best.name()) && data.getPlace(tested, stat) > quest.get(Mod.best.name())) return false;
        }
        synchronized (pd.obtained){
            pd.obtained.add(this);
        }
        return true;
    }


    @JsonIgnore public String getSuffix(){
        return "["+color+"]<"+name+">[]";
    }

    @JsonIgnore public String Suffix() {
        return displayed ? "["+color+"]<"+name+">[]" : "";
    }

    //todo fix
    public String getDescription(PD pd) {
        String desc = getTranslation(pd, "special-missing-description");
        DataHandler.Doc doc = data.getDoc(pd.id);
        if(description != null){
            String resolved = description.getOrDefault(getCountryCode(pd.bundle.getLocale()), description.get("default"));
            if(resolved != null) desc = resolved;
        }
        StringBuilder condition = new StringBuilder();
        if(isPermanent()) condition = new StringBuilder(getTranslation(pd, "special-is-permanent"));
        else {
            if(linked != null){
                condition.append(getTranslation(pd, "special-links")).append("\n");
                for(String l : linked){
                    String color = Ranks.special.get(l).condition(doc, pd) ? "green" : "scarlet";
                    condition.append("[").append(color).append("]").append(l).append("[],");
                }
                condition.append("\n");
            }
            if(quests != null){
                condition.append(getTranslation(pd, "special-requirements")).append("\n");
                for(String s : quests.keySet()){
                    condition.append("[orange]").append(getTranslation(pd, "special-" + s)).append(":[]\n");
                    HashMap<String, Integer> quest = quests.get(s);
                    for(String l : quest.keySet()){
                        int req = quest.get(l);
                        long val = doc.getStat(l);
                        String color = "green";
                        switch (Mod.valueOf(l)){
                            case best:
                                val = data.getPlace(doc, s);
                                if (val > req) color = "scarlet";
                                break;
                            case required:
                                if (val < req) color = "scarlet";
                                break;
                            case frequency:
                                long played = doc.getStat(Stat.playTime)/hour;
                                if (played == 0) played = 1;
                                if (val/played < req) color = "scarlet";

                        }
                        condition.append("[").append(color).append("]");
                        condition.append(getTranslation(pd, "special-" + l)).append(":");
                        condition.append(val).append("/").append(req).append("[]\n");
                    }
                }
            }
        }
        return getSuffix() + "\n[gray]" + desc + "\npermissions:" + permissions + "\n" + condition + "\n";
    }

    enum Mod{
        best,
        required,
        frequency
    }
}
