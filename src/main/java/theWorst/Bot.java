package theWorst;

import arc.Events;
import arc.util.Log;
import arc.util.Timer;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import theWorst.database.Database;
import theWorst.database.PlayerD;
import theWorst.database.Rank;
import theWorst.database.Setting;
import theWorst.discord.BotConfig;
import theWorst.discord.DiscordCommands;

import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static mindustry.Vars.player;
import static mindustry.Vars.playerGroup;
import static theWorst.Tools.hasMapAttached;

public class Bot {
    public static String dir = Config.configDir + "bot/";
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
            if(api == null || Tools.isCommandRelated(e.message)  || !config.channels.containsKey("linked")) return;
            config.channels.get("linked").sendMessage("**"+Tools.cleanName(e.player.name)+"** : "+e.message.substring(e.message.indexOf("]")+1));
        });

        Events.on(EventType.PlayerChatEvent.class,e->{
            if(api == null || !config.channels.containsKey("commandLog")) return;
            if(!Tools.isCommandRelated(e.message)) return;
            PlayerD pd = Database.getData(e.player);
            config.channels.get("commandLog").sendMessage(String.format("**%s** - %s (%d): %s",
                    pd.originalName,pd.rank,pd.serverId,e.message));
        });
        connect();
    }

    public static void onRankChange(String name, long serverId, String prev, String now, String by, String reason) {
        if(!config.channels.containsKey("log")) return;
        config.channels.get("log").sendMessage(String.format("**%s** (%d) **%s** -> **%s** \n**by:** %s \n**reason:** %s",
                name,serverId,prev,now,by,reason));
    }

    public static void connect(){
        disconnect();
        config = new BotConfig();
        if(api==null) return;
        api.addMessageCreateListener(handler);

        api.addMessageCreateListener((event)->{
            if(!config.channels.containsKey("linked") || event.getChannel() != config.channels.get("linked")) return;
            String content=Tools.cleanEmotes(event.getMessageContent());
            if(event.getMessageAuthor().isBotUser() || content.startsWith(config.prefix)) return;
            //if there wos only emote in message it got removed and we don't want to show blank message
            boolean blank = true;
            for(int i=0;i<content.length();i++){
                if(content.charAt(i)!=' ') {
                    blank = false;
                    break;
                }
            }
            if(blank) return;
            for(Player p : playerGroup) {
                if(Database.hasEnabled(player, Setting.chat)) {
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

    public static boolean disconnect(){
        if(api == null) return false;
        api.disconnect();
        return true;
    }
}
