package theWorst.database;

import arc.util.Log;
import arc.util.Time;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.lang.Nullable;
import mindustry.entities.type.Player;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import theWorst.Global;
import theWorst.helpers.gameChangers.Pet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.ResourceBundle;

import static theWorst.Tools.Bundle.*;
import static theWorst.Tools.Formatting.*;
import static theWorst.Tools.Players.*;
import static theWorst.database.Database.getData;
import static theWorst.database.Database.rawData;

@Document
public class PlayerD {
    //in game stats
    public long buildingsBuilt = 0;
    public long buildingsBroken = 0;
    public long enemiesKilled = 0;
    public long deaths = 0;
    public long gamesPlayed = 0;
    public long gamesWon = 0;
    public long factoryVotes = 0;
    public long loadoutVotes = 0;
    public long buildCoreVotes = 0;
    public long messageCount = 0;
    public long playTime = 1;
    public long age = Time.millis();
    public long connected = Time.millis()+1;
    public long lastActive = 0;
    @Transient public long lastAction = Time.millis();
    @Transient public boolean afk = false;
    public String specialRank = "";
    @Transient public final HashSet<SpecialRank> obtainedRanks = new HashSet<>();
    @Transient public final ArrayList<Pet> pets = new ArrayList<>();

    //user customization
    public String textColor = "white";
    @Indexed public String discordLink;
    public HashSet<String> settings = new HashSet<>();
    public HashSet<String> mutes = new HashSet<>();
    public String donationLevel = "";

    //administration
    @Indexed public long serverId;
    @Id public String uuid;
    public String rank = Rank.defaultRank.name();
    public String ip;
    public String originalName;
    public long lastMessage = 0;



    @Transient PlayerD oldMeta;
    @Transient public ResourceBundle bundle = defaultBundle;

    //empty instance used only when player joins for the firs time
    protected PlayerD(){
        specialRank = "";
        ip = "";
    }

    public PlayerD(Player player){
        uuid = player.uuid;

        //this data can change
        ip = player.con.address;
        originalName = player.name;
        loadMeta(player);
    }

    public void onAction(Player player) {
        lastAction = Time.millis();
        if(afk){
            afk=false;
            Database.updateName(player,this);
            sendMessage("afk-is-not",originalName,Database.AFK);
        }
    }

    public boolean isOnline(){
        return lastActive<connected;
    }

    private void loadMeta(Player player) {
        PlayerD meta = Database.getMeta(uuid);
        //It takes about 3 seconds to figure out bundle for player, thread gets rid of the delay.
        new Thread(()->{
            bundle = ResourceBundle.getBundle(bundlePath, getLocale(ip));
            if(Global.config.welcomeMessage == null) return;
            String welcomeMessage = Global.config.welcomeMessage.getOrDefault(
                    getCountryCode(getData(player).bundle.getLocale()),Global.config.welcomeMessage.get("default"));
            if(meta == null && welcomeMessage != null){
                player.sendMessage(welcomeMessage);
            }
        } ).start();
        //no data about player so this is new player
        if(meta == null){
            //greet a newcomer
            //enable all settings by default
            for(Setting s : Setting.values()){
                settings.add(s.name());
            }
            //its simple and produces easy to remember ids
            serverId = Database.getDatabaseSize();
            Database.data.save(this);
            oldMeta= new PlayerD();
            //meta is null! cannot continue
            return;
        }
        oldMeta = meta;
        serverId = meta.serverId;
        lastActive = meta.lastActive;
        specialRank = meta.specialRank;
        rank = meta.rank;
        textColor = meta.textColor;
        discordLink = meta.discordLink;
        settings = meta.settings;
        mutes = meta.mutes;
        lastMessage = meta.lastMessage;
        age = meta.age;
        playTime = meta.playTime;
        donationLevel = meta.donationLevel;
        meta.connected = connected;
        Database.updateMeta(meta);
    }

    public void disconnect(){
        lastActive=Time.millis();
        update();
    }

    void update() {
        //loading old data
        PlayerD meta = Database.getMeta(uuid);
        //it can happen when mongoDB database is dropped when server is running
        if (meta == null) {
            Database.data.save(this);
            return;
        }
        //updating it
        ///settings
        meta.textColor = textColor;
        meta.discordLink = discordLink;
        meta.settings = settings;
        meta.mutes = mutes;
        ///just data
        meta.connected = connected;
        meta.lastActive = lastActive;
        meta.specialRank = specialRank;
        meta.rank = rank;
        meta.ip = ip;
        meta.originalName = originalName;
        meta.lastMessage = lastMessage;
        meta.donationLevel = donationLevel;
        //replace old one in database
        Database.updateMeta(meta);
    }

    public String toString(@Nullable PlayerD pd) {
        if (oldMeta == null) oldMeta = new PlayerD();
        //the current instance of player data is counting everything from zero and then just incrementing to database
        // when player disconnects. That is why we have to use old data instance to provide somewhat accurate information
        SpecialRank sr = Database.ranks.get(specialRank);
        SpecialRank dl = Database.ranks.get(donationLevel);
        String activity = isOnline() ? getTranslation(pd,"player-online") :
                format(getTranslation(pd, "player-offline"),milsToTime(Time.timeSinceMillis(lastActive)));
        return format(getTranslation(pd, "player-info"),
                activity,
                "" + serverId,
                "" + getLevel(),
                originalName,
                Rank.valueOf(rank).getName(),
                (sr == null ? "none" : sr.getSuffix()),
                (dl == null ? "none" : dl.getSuffix()),
                milsToTime(playTime),
                milsToTime(Time.timeSinceMillis(age)),
                "" + (messageCount + oldMeta.messageCount),
                "" + (gamesPlayed + oldMeta.gamesPlayed),
                "" + (gamesWon + oldMeta.gamesWon),
                "" + (buildingsBuilt + oldMeta.buildingsBuilt),
                "" + (buildingsBroken + oldMeta.buildingsBroken),
                "" + (loadoutVotes + oldMeta.buildingsBroken),
                "" + (factoryVotes + oldMeta.factoryVotes),
                "" + (enemiesKilled + oldMeta.enemiesKilled),
                bundle.getLocale().getDisplayCountry(),
                "" + (deaths + oldMeta.deaths));
    }

    public int getLevel(){
        long value=
                (buildingsBuilt + oldMeta.buildingsBuilt)*4+
                (buildingsBroken + oldMeta.buildingsBroken)*4+
                (gamesWon + oldMeta.gamesWon)*200+
                (gamesPlayed + oldMeta.gamesPlayed)*2+
                (loadoutVotes + oldMeta.buildingsBroken)*100+
                (factoryVotes + oldMeta.factoryVotes)*100+
                (buildCoreVotes + oldMeta.buildCoreVotes)*1000+
                (enemiesKilled + oldMeta.enemiesKilled)/10+
                oldMeta.playTime/(1000*60)+
                (messageCount + oldMeta.messageCount)*5;
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

    @PersistenceConstructor public PlayerD(long buildingsBuilt,
                   long buildingsBroken,
                   long enemiesKilled,
                   long deaths,
                   long gamesPlayed,
                   long gamesWon,
                   long factoryVotes,
                   long loadoutVotes,
                   long buildCoreVotes,
                   long messageCount,
                   long playTime,
                   long age,
                   long connected,
                   long lastActive,
                   String specialRank,
                   String textColor,
                   String discordLink,
                   String donationLevel,
                   HashSet<String> settings,
                   HashSet<String> mutes,
                   long serverId,
                   String uuid,
                   String rank,
                   String ip,
                   String originalName,
                   long lastMessage) {
        this.buildingsBuilt = buildingsBuilt;
        this.buildingsBroken = buildingsBroken;
        this.enemiesKilled = enemiesKilled;
        this.deaths = deaths;
        this.gamesPlayed = gamesPlayed;
        this.gamesWon = gamesWon;
        this.factoryVotes = factoryVotes;
        this.loadoutVotes = loadoutVotes;
        this.buildCoreVotes = buildCoreVotes;
        this.messageCount = messageCount;
        this.playTime = playTime;
        this.age = age;
        this.connected = connected;
        this.lastActive = lastActive;
        this.specialRank = specialRank;
        this.textColor = textColor;
        this.discordLink = discordLink;
        this.settings = settings;
        this.mutes = mutes;
        this.serverId = serverId;
        this.uuid = uuid;
        this.rank = rank;
        this.ip = ip;
        this.originalName = originalName;
        this.lastMessage = lastMessage;
        this.donationLevel = donationLevel;
    }

    public void increment(Stat relation) {
        rawData.updateOne(Filters.eq("uuid", uuid), Updates.inc(relation.name(),1));
    }
}

