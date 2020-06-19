package theWorst.database;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import mindustry.entities.type.Player;
import org.bson.Document;
import theWorst.Tools;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

import static theWorst.Tools.getTranslation;
import static theWorst.Tools.logInfo;

public class SpecialRank implements Serializable {
    public String name;
    public String color="";

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
        this.name = name;
        if(name == null){
            this.name = "noName";
            logInfo("missing-name","special rank");
        }
        this.color = color;
        if(color == null){
            this.color = "red";
            logInfo("special-missing-color",name);
        }
        if(description != null) this.description = description;
        this.value = value;
        if(permissions != null) this.permissions = permissions;
        this.linked = linked;
        this.quests = quests;
        this.pets = pets;
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
                    logInfo("special-rank-error-missing-rank");
                    return false;
                }
                if(!other.condition(tested)) return false;
            }
        }
        if(quests == null && linked == null) return false;
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

    public String getDescription(PlayerD pd) {
        String desc = description.get(Tools.getCountryCode(pd.bundle.getLocale()));
        if(desc == null) desc = description.get("default");
        if(desc == null) desc = getTranslation(pd, "special-missing-description");
        String condition = "";
        if(isPermanent()) condition = getTranslation(pd,"special-is-permanent");
        else {
            if(linked != null) condition += getTranslation(pd, "special-is-linked") + linked + "\n";
            if(quests != null) condition += getTranslation(pd,"special-other-conditions") + "\n" + quests.toString();
        }
        return getSuffix() + "\n[gray]" + desc + "\n" + condition + "\n";
    }
}
