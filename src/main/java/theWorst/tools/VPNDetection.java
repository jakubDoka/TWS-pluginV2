package theWorst.tools;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import theWorst.Global;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class VPNDetection {

    public static String query(String ip, int timeout, String apy) throws IOException {
        StringBuilder response = new StringBuilder();
        URL website = new URL("http://v2.api.iphub.info/ip/" + ip);

        URLConnection connection = website.openConnection();
        connection.setConnectTimeout(timeout);
        connection.setRequestProperty("X-Key", apy);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String output;
            while ((output = in.readLine()) != null) {
                response.append(output);
            }
        }

        return response.toString();
    }

    public static boolean isVpnUser(String ip) {
        if(Global.config.vpnApi == null) return false;

        JSONObject data;
        try {
            data = Json.Parse(query(ip, Global.config.vpnTimeout, Global.config.vpnApi));
            if(data == null) return false;
        } catch (IOException e) {
            return false;
        }

        Long block = (Long) data.get("block");
        if(block == null) return false;

        return block == 0;
    }
}
