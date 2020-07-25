package theWorst.dataBase;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashSet;

@Document(collection = "PlayerData")
public class PlayerD {
    //in game stats
    public long buildingsBuilt;
    public long buildingsBroken;
    public long enemiesKilled;
    public long deaths;
    public long gamesPlayed;
    public long gamesWon;
    public long factoryVotes;
    public long loadoutVotes;
    public long messageCount;
    public long playTime;
    public long age;
    public long connected;
    public long lastActive;
    public String specialRank;

    //user customization
    public String textColor;
    @Indexed public String discordLink;
    public HashSet<String> settings;
    public HashSet<String> mutes;
    public String donationLevel;

    //administration
    @Id public long id;
    @Indexed public String uuid;
    public String rank;
    @Indexed public String ip;
    public String name;
    public long lastMessage;





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
                   String textColor,
                   String discordLink,
                   String donationLevel,
                   HashSet<String> settings,
                   HashSet<String> mutes,
                   long id,
                   String uuid,
                   String rank,
                   String ip,
                   String name,
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
        this.textColor = textColor;
        this.discordLink = discordLink;
        this.settings = settings;
        this.mutes = mutes;
        this.id = id;
        this.uuid = uuid;
        this.rank = rank;
        this.ip = ip;
        this.name = name;
        this.lastMessage = lastMessage;
        this.donationLevel = donationLevel;
    }
}

