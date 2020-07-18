package theWorst;

import arc.Events;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import org.javacord.api.DiscordApi;
import theWorst.dataBase.Database;
import theWorst.dataBase.PlayerD;
import theWorst.dataBase.Setting;
import theWorst.discord.BotConfig;
import theWorst.discord.Command;
import theWorst.discord.DiscordCommands;

import java.util.HashMap;

import static mindustry.Vars.playerGroup;
import static mindustry.Vars.world;
import static theWorst.Tools.Commands.isCommandRelated;
import static theWorst.Tools.Formatting.*;
import static theWorst.Tools.Json.loadSimpleHashmap;
import static theWorst.Tools.Json.saveSimple;
import static theWorst.Tools.Maps.hasMapAttached;

public class Bot {
    public static String dir = Global.configDir + "bot/";
    static final String restrictionFile = dir + "restrictions.json";
    public static BotConfig config;
    public static DiscordApi api = null;
    public static final HashMap<Long,LinkData> pendingLinks = new HashMap<>();
    private static final DiscordCommands handler = new DiscordCommands();
    private static final BotCommands commands = new BotCommands(handler);

    public static class LinkData{
        public String name,pin,id;
        LinkData(String name,String pin,String id){
            this.name=name;
            this.pin=pin;
            this.id=id;
        }
    }

    public Bot(){
        Events.on(EventType.PlayerChatEvent.class,e->{
            if(isCommandRelated(e.message)) return;
            sendToLinkedChat("**"+cleanName(e.player.name)+"** : "+e.message.substring(e.message.indexOf("]")+1));
        });

        Events.on(EventType.PlayEvent.class, e-> sendToLinkedChat("===*Playing on " + world.getMap().name() + "*==="));


        Events.on(EventType.PlayerChatEvent.class,e->{
            if(api == null || !config.channels.containsKey("commandLog")) return;
            if(!isCommandRelated(e.message)) return;
            PlayerD pd = Database.getData(e.player);
            config.channels.get("commandLog").sendMessage(String.format("**%s** - %s (%d): %s",
                    cleanColors(pd.originalName), pd.rank, pd.serverId, e.message));
        });
        loadRestrictions();
        connect();
    }

    public static void sendToLinkedChat(String message){
        if(api == null || !config.channels.containsKey("linked")) return;
        config.channels.get("linked").sendMessage(message);
    }

    public static void onRankChange(String name, long serverId, String prev, String now, String by, String reason) {
        if(!config.channels.containsKey("log")) return;
        if(reason == null){
            reason = "Not provided.";
        }else if(reason.equals("auto") && config.roles.containsKey("admin")) {
            reason = "Oxygen account detected!!" + config.roles.get("admin").getMentionTag();
        }
        config.channels.get("log").sendMessage(String.format("**%s** (%d) **%s** -> **%s** \n**by:** %s \n**reason:** %s",
                cleanColors(name),serverId,prev,now,by,reason));
    }

    public static void connect(){
        disconnect();
        config = new BotConfig();
        if(api==null) return;
        api.addMessageCreateListener(handler);

        api.addMessageCreateListener((event)->{
            if(!config.channels.containsKey("linked") || event.getChannel() != config.channels.get("linked")) return;
            String content = cleanEmotes(event.getMessageContent());
            if(event.getMessageAuthor().isBotUser() || content.startsWith(config.prefix)) return;
            //if there wos only emote in message it got removed and we don't want to show blank message
            if(content.replace(" ","").isEmpty()) return;
            for(Player p : playerGroup) {
                if(Database.hasEnabled(p, Setting.chat)) {
                    p.sendMessage("[coral][[[royal]"+event.getMessageAuthor().getName()+"[]]:[sky]"+content);
                }
            }
        });

        api.addMessageCreateListener((event)->{
            if(event.getMessageAuthor().isBotUser()) return;
            if(hasMapAttached(event.getMessage()) && !handler.hasCommand(event.getMessageContent().replace(config.prefix,""))){
                event.getChannel().sendMessage("If you want to post map use !postmap command!");
                event.getMessage().delete();
            }
        });
    }

    public static void loadRestrictions() {
       HashMap<String, String[] > restricts = loadSimpleHashmap(restrictionFile, String[].class, Bot::DefaultRestrictions);
       if(restricts == null) return;
       for(String s : restricts.keySet()){
           handler.commands.get(s).role = restricts.get(s);
       }
    }

    public static void DefaultRestrictions() {
        HashMap<String, String[]> data = new HashMap<>();
        for (Command c : handler.commands.values()) {
            if(c.role != null) {
                data.put(c.name, c.role);
            } else {
                data.put(c.name, new String[0]);
            }
        }
        saveSimple(restrictionFile, data, "command restrictions");
    }

    public static void disconnect(){
        if(api == null) return ;
        api.disconnect();
    }
}
