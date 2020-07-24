package theWorst.dataBase;

import arc.util.Log;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import org.bson.*;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;



public class Database {
    public static MongoClient client = MongoClients.create();
    static MongoOperations Data = new MongoTemplate(client, "mindustryServer");
    static HashMap<String, PlayerData> data = new HashMap<>();

    public static SpecialRank getSpecialRank(PlayerData d){
        return new SpecialRank();
    }

    public static void Init(String database, String host) {
        if (host != null) {
            Log.info("Connecting to Mongodb...");
            ConnectionString connectionString = new ConnectionString("mongodb+srv://" + host + "/" + database + "?w=majority");
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .retryWrites(true)
                    .build();
            client = MongoClients.create(settings);
            Log.info("Connected.");
        }
        Data = new MongoTemplate(client, database);
    }

    public static void loadData(){
        try {
            FileInputStream fileIn = new FileInputStream("config/mods/The_Worst/database.ser");
            ObjectInputStream in = new ObjectInputStream(fileIn);
            Object obj=in.readObject();
            if(obj instanceof HashMap) data = (HashMap<String, PlayerData>) obj;
            in.close();
            fileIn.close();
            Log.info("Database loaded.");
        } catch (ClassNotFoundException c) {
            Log.info("class not found");
            c.printStackTrace();
        } catch (FileNotFoundException f){
            Log.info("database no found, creating new");
        }catch (IOException i){
            Log.info("Database is incompatible with current version.");
            i.printStackTrace();
        }
    }

    public static void Convert() {
        for(String s : data.keySet()){
            PlayerData p = data.get(s);
            PlayerD pd = new PlayerD(p.buildingsBuilt,p.buildingsBroken,
                    p.enemiesKilled,p.deaths,p.gamesPlayed,p.gamesWon,p.factoryVotes
            ,p.loadoutVotes,p.messageCount,p.playTime,p.born,p.connected,
                    p.lastActive,p.specialRank,p.textColor,p.discordLink,null,
                    p.settings,new HashSet<String>(), p.serverId,s,p.trueRank.name(),
                    p.ip,p.originalName,p.lastMessage);

            Database.Data.save(pd);
        }
    }

    static class SpecialRank {
        String getSuffix() {
            return "f";
        }
    }
}
