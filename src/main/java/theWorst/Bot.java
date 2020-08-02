package theWorst;

import arc.Events;
import arc.util.Timer;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import theWorst.database.*;
import theWorst.discord.BotConfig;
import theWorst.discord.Command;
import theWorst.discord.CommandContext;
import theWorst.discord.DiscordCommands;

import java.awt.Color;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static mindustry.Vars.playerGroup;
import static mindustry.Vars.world;
import static theWorst.tools.Commands.isCommandRelated;
import static theWorst.tools.Formatting.*;
import static theWorst.tools.Json.loadSimpleHashmap;
import static theWorst.tools.Json.saveSimple;
import static theWorst.tools.Maps.hasMapAttached;
import static theWorst.tools.Players.sendMessage;

public class Bot {
    public static String dir = Global.configDir + "bot/";
    static final String restrictionFile = dir + "restrictions.json";
    public static BotConfig config;
    public static DiscordApi api = null;
    public static final HashMap<Long,LinkData> pendingLinks = new HashMap<>();
    private static final DiscordCommands handler = new DiscordCommands();



    public static class LinkData{
        public String name,pin,id;
        LinkData(String name,String pin,String id){
            this.name=name;
            this.pin=pin;
            this.id=id;
        }
    }

    public static void init(){
        new BotCommands(handler);
        Events.on(EventType.PlayerChatEvent.class,e->{
            if(isCommandRelated(e.message)) return;
            sendToLinkedChat("**"+cleanName(e.player.name)+"** : "+e.message.substring(e.message.indexOf("]")+1));
        });

        Events.on(EventType.PlayEvent.class, e-> sendToLinkedChat("===*Playing on " + world.getMap().name() + "*==="));


        Events.on(EventType.PlayerChatEvent.class,e->{
            if(api == null || !config.channels.containsKey("commandLog")) return;
            if(!isCommandRelated(e.message) || e.message.startsWith("/protect")) return;
            PD pd = Database.getData(e.player);
            config.channels.get("commandLog").sendMessage(String.format("**%s** - %s (%d): %s",
                    pd.name, pd.rank.name, pd.id, e.message));
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

    public static void connectUser(PD pd, Doc doc) {

        if (Bot.api == null || Bot.config.serverId == null || pd.isGriefer()) {
            sendMessage("player-connected",pd.player.name,String.valueOf(pd.id));
            return;
        }

        Runnable conMess = () ->{
            sendMessage("player-connected",pd.player.name,String.valueOf(pd.id));
            Bot.sendToLinkedChat(String.format("**%s** (ID:**%d**) hes connected.", cleanColors(pd.player.name), pd.id));
        };

        if (Bot.pendingLinks.containsKey(pd.id)){
            sendMessage(pd.player,"discord-pending-link",Bot.pendingLinks.get(pd.id).name);
        }
        String link = doc.getLink();
        if (link == null) {
            conMess.run();
            return ;
        }
        CompletableFuture<User> optionalUser = Bot.api.getUserById(link);
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                processUser(pd, conMess, optionalUser, this);
            }}, 0, .1f);
    }

    static void processUser(PD pd, Runnable message, CompletableFuture<User> fUser, Timer.Task task){
        if (!fUser.isDone()) return;
        task.cancel();
        User user;
        try {
            user = fUser.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            message.run();
            return;
        }

        Optional<Server> server = Bot.api.getServerById(Bot.config.serverId);
        if (!server.isPresent()) {
            message.run();
            return;
        }

        Rank current = pd.rank;
        Rank crDl = null;
        for (Role r : user.getRoles(server.get())) {
            String roleName = r.getName();
            Rank dl = Ranks.donation.get(roleName);
            Rank rk = Ranks.buildIn.get(roleName);

            if (rk != null && pd.rank.value < rk.value) {
                current = rk;
            } else if (dl != null) {
                if (crDl == null || dl.value > crDl.value) {
                    crDl = dl;
                }
                pd.addRank(dl);
            }
        }

        synchronized (pd) {
            Database.setRank(pd.id, current);
            pd.dRank = crDl;
            pd.updateName();
        }

        message.run();
    }

    public static void report(Doc doc, CommandContext ctx, long targetId, String reason){
        Doc target = Database.data.getDoc(targetId);
        TextChannel channel = config.channels.get("report");
        if(channel == null) return;
        EmbedBuilder eb = new EmbedBuilder().setTitle("Griefer Report").setColor(Color.pink);
        if(doc != null) {
            eb.setAuthor(doc.getName() + "(id: " + doc.getId() + ")");
        } else {
            eb.setAuthor(ctx.author);
            List<MessageAttachment> attachments = ctx.event.getMessageAttachments();
            if(!attachments.isEmpty() ) {
                try {
                    eb.setImage(attachments.get(0).downloadAsInputStream());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        eb.setDescription("reported: **" + target.getName() + "(id:" + targetId + ")" + "**\nreason: " + reason);
        channel.sendMessage(eb);
    }
}
