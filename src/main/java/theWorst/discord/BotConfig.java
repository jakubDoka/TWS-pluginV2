package theWorst.discord;

import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.permission.Role;
import org.json.simple.JSONObject;
import theWorst.Tools;

import java.util.HashMap;
import java.util.Optional;

import static theWorst.Bot.api;
import static theWorst.Bot.dir;
import static java.lang.System.out;

public class BotConfig {
    public String prefix = "%";
    public String token = null;

    public final HashMap<String,TextChannel> channels = new HashMap<>();
    public final HashMap<String,Role> roles = new HashMap<>();
    public Long serverId = null;

    private final static String datafile = dir+"botConfig.json";

    public BotConfig(){
            //loading json config
            Tools.loadJson(datafile,(data)-> {
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
                        serverId = optional.get().getServer().getId();
                    }
                }

            },this::defaultConfig);
    }

    private void defaultConfig(){
        Tools.saveJson(datafile,
                "{\n" +
                "\t\"comment\": \"IDs of channels and roles has to me in quotation marks!!\",\n" +
                "\t\"token\": \"Your bot token goes here\",\n" +
                "\t\"prefix\":\"key for commands goes here, it should be one character like ! or % so ppl can write !commandName to call command\",\n" +
                "\t\"channels\":{\n" +
                "\t\t\"commands\":\"channel for commands goes here\"\n" +
                "\t},\n" +
                "\t\"roles\":{\n" +
                "\t\t\"manager\":\"role for ppl that manage applications goes here, it gives permission to use some commands\",\n" +
                "\t\t\"candidate\":\"this role will be assigned to tested user after he passes the test\"\n" +
                "\t}" +
                "}"
        );
    }

    public TextChannel getChannel(String name) {
        return channels.get(name);
    }

    public Role getRole(String name) {
        return roles.get(name);
    }
}
