package theWorst.Tools;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;
import theWorst.database.DataHandler;
import theWorst.database.Database;
import theWorst.database.PD;

import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;

public class Bundle {
    public static final String bundlePath = "bundles.bundle";
    public static final Locale locale = new Locale("en","US");
    public static final ResourceBundle defaultBundle = ResourceBundle.getBundle(bundlePath, locale);
    public static final PD locPlayer = new PD(){{bundle = defaultBundle;}};

    public static JSONObject getLocData(String ip){
        try {
            String json = Jsoup.connect("http://ipapi.co/"+ip+"/json").ignoreContentType(true).timeout(3000).execute().body();
            return (JSONObject) new JSONParser().parse(json);
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Locale getLocale(JSONObject data){
        if(data==null) return locale;
        String languages = (String) data.get("languages");
        if(languages==null) return locale;
        String[] resolvedL = languages.split(",");
        if(resolvedL.length==0) return locale;
        String[] resResolvedL = resolvedL[0].split("-");
        if(resResolvedL.length==0) return locale;
        return new Locale(resResolvedL[0],resResolvedL[1]);
    }

    public static void findBundleAndCountry(PD pd) {
        new Thread(()->{
            JSONObject data = getLocData(pd.player.con.address);
            if(data != null){
                Database.data.set(pd.player, "country", data.getOrDefault("country_name", "unknown"));
            }
            synchronized (pd){
                pd.bundle = ResourceBundle.getBundle(bundlePath, getLocale(data));
            }
        }).start();

    }
}
