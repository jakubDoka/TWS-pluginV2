package theWorst.discord;

import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.permission.Role;
import org.json.simple.JSONObject;

import java.util.HashMap;
import java.util.Optional;

import static theWorst.Bot.api;
import static theWorst.Bot.dir;
import static java.lang.System.out;
import static theWorst.Tools.Json.loadJson;
import static theWorst.Tools.Json.saveJson;

public class BotConfig {
    public String prefix = "%";
    public String token = null;

    public final HashMap<String,TextChannel> channels = new HashMap<>();
    public final HashMap<String,Role> roles = new HashMap<>();
    public Long serverId = null;

    private final static String datafile = dir + "config.json";

    public BotConfig(){
            //loading json config
            loadJson(datafile,(data)-> {
                if(data.containsKey("prefix")) prefix = (String) data.get("prefix");
                if(data.containsKey("token")) token = (String) data.get("token");
                //connecting to discord
                try {
                    api = new DiscordApiBuilder().setToken(token).login().join();
                } catch (Exception ex){
                    out.println("Unable to connect to discord. Probably because of invalid token.");
                    return;
                }

                if(data.containsKey("channels")){
                    //map channels
                    JSONObject channels = (JSONObject) data.get("channels");
                    for (Object o : channels.keySet()) {
                        String key = (String) o;
                        Optional<TextChannel> optional = api.getTextChannelById((String) channels.get(key));
                        if (!optional.isPresent()) {
                            out.println(key + " channel not found.");
                            continue;
                        }
                        this.channels.put(key, optional.get());
                    }
                }

                if(data.containsKey("roles")){
                    //map roles
                    JSONObject roles = (JSONObject) data.get("roles");
                    for (Object o : roles.keySet()) {
                        String key = (String) o;
                        Optional<Role> optional = api.getRoleById((String) roles.get(key));
                        if (!optional.isPresent()) {
                            out.println(key + " role not found.");
                            continue;
                        }
                        this.roles.put(key, optional.get());
                        if(serverId == null) serverId = optional.get().getServer().getId();
                    }
                }

            },this::defaultConfig);
    }

    private void defaultConfig(){
        saveJson(datafile,
               "{\n" +
                       "\t\"channels\":{\n" +
                       "\t\t\"maps\":\"channel for publishing maps\",\n" +
                       "\t\t\"commandLog\":\"shows used commands\",\n" +
                       "\t\t\"log\":\"shows rank changes\",\n" +
                       "\t\t\"commands\":\"chat for commands\",\n" +
                       "\t\t\"linked\":\"in game linked chat\"\n" +
                       "\t},\n" +
                       "\t\"prefix\":\"!\",\n" +
                       "\t\"roles\":{\n" +
                       "\t\t\"admin\":\"admin role, role will be notified when something important happens\"\n" +
                       "\t},\n" +
                       "\t\"token\":\"token of bot goes here\"\n" +
                       "}"
        , "bot");
    }
}
