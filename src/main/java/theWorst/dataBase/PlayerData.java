package theWorst.dataBase;

import arc.util.Time;
import mindustry.entities.type.Player;
import mindustry.net.Administration;
import theWorst.Main;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;

import static mindustry.Vars.*;


public class PlayerData implements Cloneable, Serializable {
    public Rank rank = Rank.newcomer;
    public Rank trueRank = Rank.newcomer;
    public String specialRank=null;
    public long serverId;
    public long buildingsBuilt = 0;
    public long buildingsBroken = 0;
    public long enemiesKilled = 0;
    public long deaths = 0;
    public long gamesPlayed = 0;
    public long gamesWon = 0;
    public long factoryVotes =0;
    public long loadoutVotes =0;
    public long messageCount =0;
    public long playTime = 1;
    public long born = Time.millis();
    public long connected = 0;
    public long lastMessage = 0;
    public long lastActive;
    public long lastAction = Time.millis();
    public long bannedUntil = 0;
    public boolean banned = false;
    public String banReason = "";
    public String originalName = "";
    public String textColor = "white";
    public String discordLink = "";
    public String infoId;
    public String ip;


    HashSet<String> settings=new HashSet<>();
    HashMap<String,Object> advancedSettings=new HashMap<>();


    public PlayerData(Player player){
        lastActive=Time.millis();
        infoId=player.getInfo().id;
        serverId=Database.data.size();
    }

    public String toString(){
        String special=specialRank==null ? "none":Database.getSpecialRank(this).getSuffix();
        String activity=connected>lastActive ? "[green]currently active[]":
                "[gray]inactive for []" + Main.milsToTime(Time.timeSinceMillis(lastActive));
        return "[orange]==PLayer data==[]\n\n" +
                "[yellow]Level:[]" + getLevel() + " | [yellow]server ID:[]" + serverId + "\n" +
                "[gray]name:[] " + originalName + "\n" +
                "[gray]rank:[] " + trueRank.getName() + "\n" +
                "[gray]special rank:[] " + special + "\n" +
                "[gray]playtime:[] " + Main.milsToTime(playTime) + "\n" +
                "[gray]server age[]: " + Main.milsToTime(Time.timeSinceMillis(born)) + "\n" +
                "[gray]messages sent[]:" + messageCount + "\n" +
                activity + "\n" +
                "[gray]games played:[] " + gamesPlayed + "\n" +
                "[gray]games won:[] " + gamesWon + "\n" +
                "[gray]buildings built:[] " +buildingsBuilt + "\n" +
                "[gray]buildings broken:[] " +buildingsBroken + "\n" +
                "[gray]successful loadout votes:[] " +loadoutVotes+"\n" +
                "[gray]successful factory votes:[] " +factoryVotes+"\n" +
                "[gray]enemies killed:[] " + enemiesKilled + "\n" +
                "[gray]deaths:[] " + deaths;
    }

    public Administration.PlayerInfo getInfo(){
        return netServer.admins.getInfo(infoId);
    }

    public int getLevel(){
        long value=buildingsBuilt*2+
                buildingsBroken*2+
                gamesWon*200+
                gamesPlayed*2+
                loadoutVotes*100+
                factoryVotes*100+
                enemiesKilled*5+
                playTime/(1000*60);
        int level=1;
        int first=500;
        while (true){
           value-=first* Math.pow(1.1,level);
           if(value<0){
               break;
           }
           level++;
        }
        return level;
    }

    public Object clone() throws CloneNotSupportedException{return super.clone();}

    public void connect(Player player) {
        originalName=player.name;
        connected=Time.millis();
        ip=player.con.address;
    }

    public void disconnect() {
        playTime+=Time.timeSinceMillis(connected);
        lastActive=Time.millis();
    }

    public long getStat(Stat stat){
        switch (stat) {
            case deaths:
                return deaths;
            case gamesWon:
                return gamesWon;
            case buildingsBroken:
                return buildingsBroken;
            case buildingsBuilt:
                return buildingsBuilt;
            case gamesPlayed:
                return gamesPlayed;
            case enemiesKilled:
                return enemiesKilled;
            case playTime:
                return playTime;
            case factoryVotes:
                return factoryVotes;
            case loadoutVotes:
                return loadoutVotes;
            case messageCount:
                return messageCount;
            case age:
                return Time.timeSinceMillis(born);
            default:
                return 0;
        }
    }


}

