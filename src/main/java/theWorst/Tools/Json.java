package theWorst.Tools;

import arc.util.Log;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;

public class Json {
    public interface RunLoad{
        void run(JSONObject data);
    }

    public static void loadJson(String filename, RunLoad load, Runnable save) {
        try (FileReader fileReader = new FileReader(filename)) {
            JSONParser jsonParser = new JSONParser();
            Object obj = jsonParser.parse(fileReader);
            JSONObject saveData = (JSONObject) obj;
            load.run(saveData);
            fileReader.close();
            Log.info("Data from " + filename + " loaded.");
        } catch (FileNotFoundException ex) {
            Log.info("No " + filename + " found.Default one wos created.");
            save.run();
        } catch (ParseException ex) {
            Log.info("Json file " + filename + " is invalid.");
        } catch (IOException ex) {
            Log.info("Error when loading data from " + filename + ".");
        }
    }

    public static void saveJson(String filename, String save){
        //creates full path
        makeFullPath(filename);
        //path exists so save
        try (FileWriter file = new FileWriter(filename)) {
            file.write(save);
        } catch (IOException ex) {
            Log.info("Error when creating/updating "+filename+".");
            ex.printStackTrace();
        }
    }

    public static <T> T loadJackson(String filename, Class<T> type){
        ObjectMapper mapper = new ObjectMapper();
        File f = new File(filename);
        try {
            if (!f.exists()){
                return saveJackson(filename,type);
            }
            return mapper.readValue(f, type);
        } catch (IOException ex){
            Log.info("Json file " + filename + " is invalid.");
            return null;
        }
    }

    public static <T> T saveJackson(String filename, Class<T> type){
        ObjectMapper mapper = new ObjectMapper();
        File f = new File(filename);
        try {
            mapper.writeValue(f, type);
            return type.newInstance();
        } catch (IOException ex){
            Log.info("Error when creating/updating "+filename+".");
        } catch (IllegalAccessException | InstantiationException e) {
            Log.info("Please report this error.");
            e.printStackTrace();
            Log.info("Please report this error.");
        }
        return null;
    }

    public static void makeFullPath(String filename){
        StringBuilder path = new StringBuilder();
        String[] dirs = filename.split("/");
        for(int i = 0; i<dirs.length-1; i++){
            path.append(dirs[i]).append("/");
            new File(path.toString()).mkdir();
        }
    }
}
