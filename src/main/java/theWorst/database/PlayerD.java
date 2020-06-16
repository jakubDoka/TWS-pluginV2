package theWorst.database;

import arc.Core;
import arc.graphics.Color;
import arc.math.geom.Vec2;
import arc.util.Log;
import arc.util.Time;
import com.fasterxml.jackson.annotation.JsonCreator;

import javafx.print.PageLayout;
import mindustry.content.Bullets;
import mindustry.content.Fx;
import mindustry.entities.Effects;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.type.Bullet;
import mindustry.entities.type.Player;
import mindustry.gen.Call;
import mindustry.net.Administration;
import org.mongojack.MongoCollection;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Query;
import theWorst.Config;
import theWorst.Tools;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.ResourceBundle;

import static org.springframework.data.mongodb.core.query.Criteria.where;

//@JsonIgnoreProperties(value = {"afk", "ip", "lastAction", "oldMeta", "online", "bundle"})
//@MongoCollection(name = "players")

@Document
public class PlayerD {
    //in game stats
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
    public long age = Time.millis();
    public long connected = Time.millis()+1;
    public long lastActive = 0;
    @Transient public long lastAction = Time.millis();
    @Transient public boolean afk = false;
    public String specialRank=null;
    public String country="unknown";
    @Transient public ArrayList<Pet> pets = new ArrayList<>();

    //user customization
    public String textColor = "white";
    public String discordLink = null;
    public HashSet<String> settings = new HashSet<>();
    public HashSet<String> mutes = new HashSet<>();

    //administration
    @Indexed public long serverId;
    @Id public String uuid;
    public String rank = "newcomer";
    public String ip;
    public String originalName = "";
    public long lastMessage = 0;



    @Transient PlayerD oldMeta;
    @Transient public ResourceBundle bundle = Tools.defaultBundle;

    //empty instance used only when player joins for the firs time
    protected PlayerD(){}

    public PlayerD(Player player){
        uuid=player.uuid;

        //this data can change
        ip=player.con.address;
        originalName=player.name;
        loadMeta(player);
    }

    public void onAction(Player player) {
        lastAction = Time.millis();
        if(afk){
            afk=false;
            Database.updateName(player,this);
            Tools.sendMessage("afk-is-not",originalName,Database.AFK);
        }
    }

    public boolean isOnline(){
        return lastActive<connected;
    }

    private void loadMeta(Player player) {
        PlayerD meta = Database.getMeta(uuid);
        //It takes about 3 seconds to figure out bundle for player, thread gets rid of the delay.
        new Thread(()-> {
            JSONObject data = Tools.getLocData(ip);
            if(data!=null){
                country = (String) data.get("country_name");
                if(country == null){
                    country=Tools.locale.getDisplayCountry();
                }
            }
            bundle=ResourceBundle.getBundle(Tools.bundlePath,Tools.getLocale(ip,data));
        }).start();
        //no data about player so this is new player
        if(meta == null){
            //greet a newcomer
            if(Config.welcomeMessage!=null){
                Tools.sendMessage(player, Config.welcomeMessage);
            }
            //enable all settings by default
            for(Setting s : Setting.values()){
                settings.add(s.name());
            }
            //its simple and produces easy to remember ids
            serverId = Database.data.findAll(PlayerD.class).size();

            Database.data.save(this);
            oldMeta= new PlayerD();
            //meta is null! cannot continue
            return;
        }
        oldMeta = meta;
        serverId = meta.serverId;
        country = meta.country;
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
        ///counters
        meta.buildingsBuilt += buildingsBuilt;
        meta.buildingsBroken += buildingsBroken;
        meta.enemiesKilled += enemiesKilled;
        meta.deaths += deaths;
        meta.gamesPlayed += gamesPlayed;
        meta.gamesWon += gamesWon;
        meta.factoryVotes += factoryVotes;
        meta.loadoutVotes += loadoutVotes;
        meta.messageCount += messageCount;
        meta.playTime += Time.timeSinceMillis(connected);
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
        //replace old one in database
        Database.updateMeta(meta);
    }

    @Override
    public String toString() {
        if (oldMeta == null) oldMeta = new PlayerD();
        //the current instance of player data is counting everything from zero and then just incrementing to database
        // when player disconnects. That is why we have to use old data instance to provide somewhat accurate information
        String activity = isOnline() ? "[green]ONLINE" : "[gray]OFFLINE FOR " + Tools.milsToTime(Time.timeSinceMillis(lastActive));
        return activity + "[]\n" +
                "[yellow]server ID:[]" + serverId + "\n" +
                "[gray]name:[] " + originalName + "\n" +
                "[gray]rank:[] " + rank + "\n" +
                "[gray]special rank:[] " + (specialRank == null ? "none" : specialRank) + "\n" +
                "[gray]playtime:[] " + Tools.milsToTime(playTime) + "\n" +
                "[gray]server age[]: " + Tools.milsToTime(Time.timeSinceMillis(age)) + "\n" +
                "[gray]messages sent[]:" + (messageCount + oldMeta.messageCount) + "\n" +
                "[gray]games played:[] " + (gamesPlayed + oldMeta.gamesPlayed) + "\n" +
                "[gray]games won:[] " + (gamesWon + oldMeta.gamesWon) + "\n" +
                "[gray]buildings built:[] " + (buildingsBuilt + oldMeta.buildingsBuilt) + "\n" +
                "[gray]buildings broken:[] " + (buildingsBroken + oldMeta.buildingsBroken) + "\n" +
                "[gray]successful loadout votes:[] " + (loadoutVotes + oldMeta.buildingsBroken) + "\n" +
                "[gray]successful factory votes:[] " + (factoryVotes + oldMeta.factoryVotes) + "\n" +
                "[gray]enemies killed:[] " + (enemiesKilled + oldMeta.enemiesKilled) + "\n" +
                "[gray]country:[] " + country + "\n" +
                "[gray]deaths:[] " + (deaths + oldMeta.deaths);
    }

    @PersistenceConstructor public PlayerD(long buildingsBuilt,
                   long buildingsBroken,
                   long enemiesKilled,
                   long deaths,
                   long gamesPlayed,
                   long gamesWon,
                   long factoryVotes,
                   long loadoutVotes,
                   long messageCount,
                   long playTime,
                   long age,
                   long connected,
                   long lastActive,
                   String specialRank,
                   String country,
                   String textColor,
                   String discordLink,
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
        this.messageCount = messageCount;
        this.playTime = playTime;
        this.age = age;
        this.connected = connected;
        this.lastActive = lastActive;
        this.specialRank = specialRank;
        this.country = country;
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
    }
}

