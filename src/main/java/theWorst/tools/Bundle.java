package theWorst.tools;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;
import theWorst.database.Database;
import theWorst.database.PD;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public class Bundle {
    public static final String bundlePath = "bundles.bundle";
    public static final Locale locale = new Locale("en","US");
    public static final ResourceBundle defaultBundle = ResourceBundle.getBundle(bundlePath,locale, new UTF8Control());
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

    public static Locale getLocale(String locString){
        String[] resResolvedL = locString.split("-");
        if(resResolvedL.length < 2) return locale;
        return new Locale(resResolvedL[0],resResolvedL[1]);
    }

    public static String getBundleString(JSONObject data) {
        String def = "en_US";
        if(data==null) return def;
        String languages = (String) data.get("languages");
        if(languages==null) return def;
        String[] resolvedL = languages.split(",");
        if(resolvedL.length==0) return def;
        return resolvedL[0];
    }

    public static void findBundleAndCountry(PD pd) {
        new Thread(()->{
            JSONObject data = getLocData(pd.player.con.address);
            if(data != null){
                Database.data.set(pd.id, "country", data.getOrDefault("country_name", "unknown"));
            }
            String locStr = getBundleString(data);
            synchronized (pd){
                pd.locString = locStr;
                pd.bundle = ResourceBundle.getBundle(bundlePath, getLocale(locStr), new UTF8Control());
            }
        }).start();

    }

    public static class UTF8Control extends ResourceBundle.Control {


        public ResourceBundle newBundle
                (String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
                throws IllegalAccessException, InstantiationException, IOException
        {
            // The below is a copy of the default implementation.
            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            ResourceBundle bundle = null;
            InputStream stream = null;
            if (reload) {
                URL url = loader.getResource(resourceName);
                if (url != null) {
                    URLConnection connection = url.openConnection();
                    if (connection != null) {
                        connection.setUseCaches(false);
                        stream = connection.getInputStream();
                    }
                }
            } else {
                stream = loader.getResourceAsStream(resourceName);
            }
            if (stream != null) {
                try {
                    // Only this line is changed to make it to read properties files as UTF-8.
                    bundle = new PropertyResourceBundle(new InputStreamReader(stream, StandardCharsets.UTF_8));
                } finally {
                    stream.close();
                }
            }
            return bundle;
        }
    }
}


