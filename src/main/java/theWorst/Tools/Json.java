package theWorst.Tools;

import arc.util.Log;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;

import static theWorst.Tools.Commands.logInfo;

public class Json {
    public interface RunLoad{
        void run(JSONObject data) throws IOException;
    }

    public static void loadJson(String filename, RunLoad load, Runnable save) {
        try (FileReader fileReader = new FileReader(filename)) {
            JSONParser jsonParser = new JSONParser();
            Object obj = jsonParser.parse(fileReader);
            JSONObject saveData = (JSONObject) obj;
            load.run(saveData);
            fileReader.close();
            logInfo("files-data-loaded", filename);
        } catch (FileNotFoundException ex) {
            save.run();
        } catch (ParseException ex) {
            logInfo("files-invalid", filename);
        } catch (IOException ex) {
            logInfo("files-invalid", filename);
            ex.printStackTrace();
        }
    }

    public static void saveJson(String filename, String save){
        //creates full path
        makeFullPath(filename);
        //path exists so save
        try (FileWriter file = new FileWriter(filename)) {
            file.write(save);
        } catch (IOException ex) {
            logInfo("unable-to-create", filename);
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
            T val = mapper.readValue(f, type);
            logInfo("files-data-loaded", filename);
            return val;
        } catch (IOException ex){
            logInfo("files-invalid", filename);
            return null;
        }
    }

    public static <T> T saveJackson(String filename, Class<T> type){
        ObjectMapper mapper = new ObjectMapper();
        makeFullPath(filename);
        File f = new File(filename);
        try {
            T obj = type.newInstance();
            mapper.writeValue(f, obj);
            return obj;
        } catch (IOException ex){
            logInfo("unable-to-create", filename);
        } catch (IllegalAccessException | InstantiationException e) {
            logInfo("report");
            e.printStackTrace();
            logInfo("report");
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
