package theWorst.database;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.Document;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static theWorst.Tools.Commands.logInfo;
import static theWorst.Tools.Players.getCountryCode;
import static theWorst.Tools.Players.getTranslation;

public class SpecialRank implements Serializable {
    static final long hour = 1000 * 60 * 60;
    public String name = "noName";
    public String color = "red";

    public int value=0;
    public HashSet<String> permissions = new HashSet<String>(){{ add(Perm.normal.name());}};
    public HashSet<String> linked = null;
    public HashMap<String,String> description = null;
    public HashMap<String, HashMap<String,Integer>> quests = null;
    public ArrayList<String> pets = null;

    @JsonCreator public SpecialRank(
            @JsonProperty("name") String name,
            @JsonProperty("color") String color,
            @JsonProperty("description") HashMap<String,String> description,
            @JsonProperty("value") int value,
            @JsonProperty("permissions") HashSet<String> permissions,
            @JsonProperty("linked") HashSet<String> linked,
            @JsonProperty("quests") HashMap<String, HashMap<String,Integer>> quests,
            @JsonProperty("pets")ArrayList<String> pets){

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

    public boolean condition(PlayerD tested){
        if(linked != null && !linked.isEmpty()) {
            for (String l : linked){
                if(!Database.ranks.get(l).condition(tested)) return false;
            }
        }
        if(quests == null && linked == null) return false;
        Document rawData = Database.getRawMeta(tested.uuid);
        for(String stat : quests.keySet()){
            HashMap<String,Integer> quest = quests.get(stat);
            if(quest.containsKey(Mod.required.name())){
                Long val = (Long)rawData.get(stat);
                if( val < quest.get(Mod.required.name())) return false;
                if(quest.containsKey(Mod.frequency.name()) && quest.get(Mod.frequency.name()) > val/(tested.playTime/hour)) return false;
            }
            if(quest.containsKey(Mod.best.name())){
                if(getPlace(stat, rawData) > quest.get(Mod.best.name())) return false;
            }
        }
        return true;
    }

    int getPlace(String stat, Document rawData){
        int place = 1;
        for(Document d : Database.getAllRawMeta()){
            if((Long)d.get(stat) > (Long)rawData.get(stat)) place ++;
        }
        return place;
    }

    @JsonIgnore public String getSuffix(){
        return "["+color+"]<"+name+">[]";
    }

    public String getDescription(PlayerD pd) {
        String desc = getTranslation(pd, "special-missing-description");
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
                    String color = Database.ranks.get(l).condition(pd) ? "green" : "scarlet";
                    condition.append("[").append(color).append("]").append(l).append("[],");
                }
                condition.append("\n");
            }
            if(quests != null){
                Document rawData = Database.getRawMeta(pd.uuid);
                condition.append(getTranslation(pd, "special-requirements")).append("\n");
                for(String s : quests.keySet()){
                    condition.append("[orange]").append(getTranslation(pd, "special-" + s)).append(":[]\n");
                    HashMap<String, Integer> quest = quests.get(s);
                    for(String l : quest.keySet()){
                        int req = quest.get(l);
                        int val = 0;
                        String color = "green";
                        switch (Mod.valueOf(l)){
                            case best:
                                val = getPlace(s, rawData);
                                if (val > req) color = "scarlet";
                                break;
                            case required:
                                val = ((Long) rawData.get(s)).intValue();
                                if (val < req) color = "scarlet";
                                break;
                            case frequency:
                                val = ((Long)((Long)rawData.get(s)/(pd.playTime/hour))).intValue();
                                if (val < req) color = "scarlet";
                        }
                        condition.append("[").append(color).append("]");
                        condition.append(getTranslation(pd, "special-" + l)).append(":");
                        condition.append(val).append("/").append(req).append("[]\n");
                    }
                }
            }
        }
        return getSuffix() + "\n[gray]" + desc + "\n" + condition + "\n";
    }

    enum Mod{
        best,
        required,
        frequency
    }
}
