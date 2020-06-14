package theWorst.database;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.Document;
import theWorst.Tools;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;

public class SpecialRank implements Serializable {
    public String name;
    public String color="";
    public String description="missing";
    public int value=0;
    public HashSet<String> permissions = new HashSet<String>(){{ add(Perm.normal.name());}};
    public HashSet<String> linked = null;
    public HashMap<String, HashMap<String,Integer>> quests = null;

    @JsonCreator public SpecialRank(
            @JsonProperty("name") String name,
            @JsonProperty("color") String color,
            @JsonProperty("description") String description,
            @JsonProperty("value") int value,
            @JsonProperty("permissions") HashSet<String> permissions,
            @JsonProperty("linked") HashSet<String> linked,
            @JsonProperty("quests") HashMap<String, HashMap<String,Integer>> quests){
        this.name = name;
        this.color = color;
        this.description = description;
        this.value = value;
        if(permissions != null) this.permissions = permissions;
        this.linked = linked;
        this.quests = quests;
    }

    public SpecialRank() { }


    @JsonIgnore public boolean isPermanent(){
        return quests == null && linked == null;
    }

    public boolean condition(PlayerD tested){
        if(linked != null && !linked.isEmpty()) {
            for (String l : linked){
                SpecialRank other = Database.ranks.get(l);
                if(other == null) {
                    Tools.logInfo("special-rank-error-missing-rank");
                    return false;
                }
                if(!other.condition(tested)) return false;
            }
            return true;
        }
        if(quests == null || quests.isEmpty()) return false;
        Document rawData = Database.getRawMeta(tested.uuid);
        for(String stat : quests.keySet()){
            HashMap<String,Integer> quest = quests.get(stat);
            if(quest.containsKey("required")){
                Long val = (Long)rawData.get(stat);
                if( val < quest.get("required")) return false;
                if(quest.containsKey("frequency") && quest.get("frequency") > val/(tested.playTime/(1000*60*60))) return false;
            }
            if(quest.containsKey("best")){
                int place = 1;
                for(Document d : Database.getAllRawMeta()){
                    if((Long)d.get(stat) > (Long)rawData.get(stat)) place ++;
                }
                if(place > quest.get("best")) return false;
            }
        }
        return true;
    }


    @JsonIgnore public String getSuffix(){
        return "["+color+"]<"+name+">[]";
    }
}
