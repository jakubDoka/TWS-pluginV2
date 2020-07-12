package theWorst.Tools;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.HashMap;

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

    public static void saveJson(String filename, String whatFor, String save){
        makeFullPath(filename);
        try (FileWriter file = new FileWriter(filename)) {
            file.write(save);
            logInfo("files-default-file-created", whatFor, filename);
        } catch (IOException ex) {
            logInfo("unable-to-create", whatFor, filename);
            ex.printStackTrace();
        }
    }

    public static <T> T loadJackson(String filename, Class<T> type, String whatFor){
        ObjectMapper mapper = new ObjectMapper();
        File f = new File(filename);
        try {
            if (!f.exists()){
                return saveJackson(filename, whatFor, type);
            }
            T val = mapper.readValue(f, type);
            logInfo("files-data-loaded", filename);
            return val;
        } catch (IOException ex){
            logInfo("files-invalid", filename);
            return null;
        }
    }

    public static <T> T saveJackson(String filename, String whatFor, Class<T> type){
        ObjectMapper mapper = new ObjectMapper();
        makeFullPath(filename);
        File f = new File(filename);
        try {
            T obj = type.newInstance();
            mapper.writeValue(f, obj);
            logInfo("files-default-file-created", whatFor, filename);
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

    public static <V> HashMap<String, V> loadSimpleHashmap(String filename, Class<V> val, Runnable save) {
        ObjectMapper mapper = new ObjectMapper();
        JavaType jt = mapper.getTypeFactory().constructMapLikeType(HashMap.class, String.class, val);
        File fi = new File(filename);
        if(!fi.exists()) {
            save.run();
            return null;
        }
        try {
            HashMap<String, V> res = mapper.readValue(new File(filename), jt);
            logInfo("files-data-loaded", filename);
            return res;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static  String[] loadSimpleCollection(String filename, Runnable save) {
        ObjectMapper mapper = new ObjectMapper();
        File fi = new File(filename);
        if(!fi.exists()) {
            save.run();
            return null;
        }
        try {
            String[] res = mapper.readValue(new File(filename), String[].class);
            logInfo("files-data-loaded", filename);
            return res;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void saveSimple(String filename, Object obj, String what){
        makeFullPath(filename);
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(new File(filename), obj);
            if(what != null){
                logInfo("files-default-file-created", what, filename);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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
