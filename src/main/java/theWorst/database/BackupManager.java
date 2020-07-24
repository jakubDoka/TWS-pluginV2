package theWorst.database;

import arc.util.Time;
import com.mongodb.DBCollection;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.ArrayList;

public class BackupManager {
    static final long oldestValid = 1000*60*60;
    static final String databaseName = "serveBackups";
    static final MongoDatabase backups = MongoClients.create().getDatabase(databaseName);

    public static void addBackup(){
        String collectionName = String.valueOf(Time.millis());
        backups.createCollection(collectionName);
        MongoCollection<Document> collection = backups.getCollection(collectionName);
        for(Document d : Database.rawData.find()){
            collection.insertOne(d);
        }
    }

    public static boolean removeBackup(int idx){
        ArrayList<String> b = getIndexedBackups();
        if(idx>=b.size()){
            return false;
        }
        backups.getCollection(b.get(idx)).drop();
        return true;
    }

    public static boolean loadBackup(int idx){
        ArrayList<String> b = getIndexedBackups();
        if(idx>=b.size()){
            return false;
        }
        //have to erase everything, jus replacing can leave some outdated data behind
        Database.clear();
        for(Document d : backups.getCollection(b.get(idx)).find()){
            Database.rawData.insertOne(d);
        }
        return true;
    }

    public static ArrayList<String> getIndexedBackups(){
        ArrayList<String> res = new ArrayList<>();
        ArrayList<String> holder = new ArrayList<>();
        for(String s : backups.listCollectionNames()){
            holder.add(s);
        }
        while (!holder.isEmpty()){
            String best = "";
            long highest = 0;
            for(String s : holder){
                long val = Long.parseLong(s);
                if(val>highest){
                    best = s;
                    highest = val;
                }
            }
            res.add(best);
            holder.remove(best);
        }
        return res;
    }

    public static boolean noNewBackups() {
        for(String name : backups.listCollectionNames()){
            if(Time.timeSinceMillis(Long.parseLong(name))<oldestValid) return false;
        }
        return true;
    }
}
