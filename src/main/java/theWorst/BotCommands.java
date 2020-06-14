package theWorst;

import arc.files.Fi;
import arc.math.Mathf;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.entities.type.Player;
import mindustry.game.Team;
import mindustry.game.Teams;
import mindustry.io.MapIO;
import mindustry.maps.Map;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.modules.ItemModule;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import theWorst.database.Database;
import theWorst.database.PlayerD;
import theWorst.database.Rank;
import theWorst.database.Stat;
import theWorst.discord.Command;
import theWorst.discord.CommandContext;
import theWorst.discord.DiscordCommands;
import theWorst.helpers.MapManager;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static mindustry.Vars.maps;
import static mindustry.Vars.state;
import static theWorst.Bot.*;
import static theWorst.Tools.formMapEmbed;
import static theWorst.Tools.setRankViaCommand;

public class BotCommands {
    public BotCommands(DiscordCommands handler) {
        String[] defaultRole = new String[]{"admin"};
        handler.registerCommand(new Command("help") {
            {
                description = "Shows all commands and their description.";
            }
            @Override
            public void run(CommandContext ctx) {
                EmbedBuilder eb =new EmbedBuilder()
                        .setTitle("COMMANDS")
                        .setColor(Color.orange);
                StringBuilder sb=new StringBuilder();
                sb.append("*!commandName - restriction - <necessary> [optional] |.fileExtension| - description*\n");
                for(String s:handler.commands.keySet()){
                    sb.append(handler.commands.get(s).getInfo()).append("\n");
                }
                ctx.channel.sendMessage(eb.setDescription(sb.toString()));
            }
        });

       handler.registerCommand(new Command("link","<serverId>") {
            {
                description = "Links your discord with your server profile.";
            }
            @Override
            public void run(CommandContext ctx) {
                if(!Strings.canParsePostiveInt(ctx.args[0])){
                    ctx.reply("Server Id has to be integer.");
                    return;
                }
                Optional<User> optionalUser = ctx.author.asUser();
                if(!optionalUser.isPresent()){
                    ctx.reply("It appears that you are not a user.");
                    return;
                }
                User user = optionalUser.get();
                PlayerD pd = Database.getMetaById(Long.parseLong(ctx.args[0]));
                if(pd==null){
                    ctx.reply("Account not found.");
                    return;
                }
                if(pd.discordLink != null && pd.discordLink.equals(user.getIdAsString())){
                    ctx.reply("You already have this account linked.");
                    return;
                }
                String pin =String.valueOf(Mathf.random(1000,9999));
                user.sendMessage("Use /link "+pin+" command in game to confirm the linkage.");
                pendingLinks.put(pd.serverId, new LinkData(user.getName(),pin, user.getIdAsString()));
            }
        });

        handler.registerCommand(new Command("gamestate") {
            {
                description = "Shows information about current game state.";
            }
            @Override
            public void run(CommandContext ctx) {
                EmbedBuilder eb =new EmbedBuilder().setTitle("GAME STATE");
                if(Vars.state.is(GameState.State.playing)){
                    eb.addField("map", Vars.world.getMap().name())
                            .addField("mode", Vars.state.rules.mode().name())
                            .addInlineField("players",String.valueOf(Vars.playerGroup.size()))
                            .addInlineField("wave",String.valueOf(Vars.state.wave))
                            .addInlineField("enemies",String.valueOf(Vars.state.enemies))
                            .setImage(Tools.getMiniMapImg())
                            .setColor(Color.green);
                } else {
                    eb.setColor(Color.red).setDescription("Server is not hosting at the moment.");
                }
                ctx.channel.sendMessage(eb);
            }
        });

        handler.registerCommand(new Command("players") {
            {
                description = "Shows list of online players.";
            }
            @Override
            public void run(CommandContext ctx) {
                StringBuilder sb = new StringBuilder();
                for(Player p:Vars.playerGroup){
                    PlayerD pd = Database.getData(p);
                    sb.append(pd.originalName).append(" | ").append(pd.rank).append(" | ").append(pd.serverId).append("\n");
                }
                EmbedBuilder eb =new EmbedBuilder()
                        .setTitle("PLAYERS ONLINE")
                        .setColor(Color.green)
                        .setDescription(sb.toString());
                if(Vars.playerGroup.size()==0) eb.setDescription("No players online.");
                ctx.channel.sendMessage(eb);
            }
        });

        handler.registerCommand(new Command("resinfo") {
            {
                description = "Check the amount of resources in the core.";
            }
            public void run(CommandContext ctx) {
                if (!state.rules.waves) {
                    ctx.reply("Only available in survival mode!");
                    return;
                }
                // the normal player team is "sharded"
                Teams.TeamData data = state.teams.get(Team.sharded);
                if(data.cores.isEmpty()){
                    ctx.reply("No cores no resources");
                    return;
                }
                //-- Items are shared between cores
                CoreBlock.CoreEntity core = data.cores.first();
                ItemModule items = core.items;
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("CORE RESOURCES");
                items.forEach((item, amount) -> eb.addInlineField(item.name, String.valueOf(amount)));
                ctx.channel.sendMessage(eb);
            }
        });

        handler.registerCommand(new Command("downloadmap","<mapName/id>") {
            {
                description = "Preview and download a server map in a .msav file format.";
            }
            public void run(CommandContext ctx) {

                Map found = Tools.findMap(ctx.args[0]);

                if (found == null) {
                    ctx.reply("Map not found!");
                    return;
                }

                ctx.channel.sendMessage(formMapEmbed(found,"download",ctx),found.file.file());
            }
        });

        handler.registerCommand(new Command("maps") {
            {
                description = "Shows all server maps and ids.";
            }
            @Override
            public void run(CommandContext ctx) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("MAP LIST")
                        .setColor(Color.orange);
                StringBuilder b =new StringBuilder();
                int i=0;
                for(Map map:Vars.maps.customMaps()){
                    double rating = MapManager.getData(map).getRating();
                    b.append(i).append(" | ").append(map.name()).append(" | ").append(String.format("%.2f/10",rating)).append("\n");
                    i++;
                }
                embed.setDescription(b.toString());
                ctx.channel.sendMessage(embed);
            }
        });

        handler.registerCommand(new Command("search","<searchKey/sort/rank> [sortType/rankName] [reverse]") {
            {
                description = "Shows first 20 results of search.";
            }
            @Override
            public void run(CommandContext ctx) {
                ArrayList<String> res = Tools.search(ctx.args);
                if (res == null) {
                    ctx.reply("Sorry i don't know this sort type. Choose from these : " + Arrays.toString(Stat.values()));
                    return;
                }

                StringBuilder mb = new StringBuilder();
                int size = res.size();
                int begin = Math.max(0,size-20);
                for (int i = begin; i <size; i++) {
                    mb.insert(0,Tools.cleanColors(res.get(i))+"\n");
                }
                if (res.isEmpty()) {
                    ctx.reply("No results found.");
                } else {
                    ctx.channel.sendMessage(mb.toString());
                    if(size>20){
                        ctx.reply("I em showing just 20 out of " + size + ".");
                    }
                }
            }
        });

        handler.registerCommand(new Command("info","<name/id>") {
            {
                description = "Shows info about player.";
            }
            @Override
            public void run(CommandContext ctx) {
                PlayerD pd = Database.getMetaById(Long.parseLong(ctx.args[0]));
                if(pd==null){
                    ctx.reply("No data found.");
                    return;
                }
                String data = Tools.cleanColors(pd.toString());
                ctx.channel.sendMessage(new EmbedBuilder().setDescription(data).setTitle("PLAYER INFO").setColor(Color.blue));
            }
        });

        handler.registerCommand(new Command("postmap","|.msav|") {
            @Override
            public void run(CommandContext ctx) {
                Message message = ctx.event.getMessage();
                MessageAttachment a = message.getAttachments().get(0);

                String path = Config.dir + "postedMaps/" + a.getFileName();
                Tools.makeFullPath(path);
                try {
                    Files.copy(a.downloadAsInputStream(), Paths.get(path), StandardCopyOption.REPLACE_EXISTING);
                    Fi mapFile = new Fi(path);
                    Map posted = MapIO.createMap(mapFile,true);

                    EmbedBuilder eb = formMapEmbed(posted,"map post",ctx);

                    if(config.channels.containsKey("maps")){
                        config.channels.get("maps").sendMessage(eb,mapFile.file());
                        ctx.reply("Map posted.");
                    }else {
                        ctx.channel.sendMessage(eb,mapFile.file());
                    }
                } catch (IOException ex){
                    ctx.reply("I em unable to post your map.");
                }
            }
        });

        handler.registerCommand(new Command("restrict","<command> <roles>") {
            {
                description = "Sets role restriction for command. For more roles use role/role/role...";
                role = defaultRole;
            }
            @Override
            public void run(CommandContext ctx) {
                if(!handler.hasCommand(ctx.args[0]) ){
                    ctx.reply("Sorry i don t know this command. Unable to change restrictions.");
                    return;
                }
                String[] roles = ctx.args[1].split("/");
                for(String r : roles){
                    if(!config.roles.containsKey(ctx.args[1])){
                        ctx.reply("It might be little confusing but role names match names in the config file.\n"
                                +config.roles.keySet().toString());
                        ctx.reply("I don't know the "+r+".");
                        return;
                    }
                }

                handler.commands.get(ctx.args[0]).role = roles;

                ctx.reply(String.format("Role of %s is now %s.", ctx.args[0], ctx.args[1]));
            }
        });

        handler.registerCommand(new Command("addmap","|.msav|") {
            {
                description = "Adds map to server.";
                role = defaultRole;
            }
            @Override
            public void run(CommandContext ctx) {
                Message message = ctx.event.getMessage();
                MessageAttachment a = message.getAttachments().get(0);
                try {
                    String path="config/maps/"+a.getFileName();
                    Files.copy(a.downloadAsInputStream(), Paths.get(path), StandardCopyOption.REPLACE_EXISTING);
                    Fi mapFile = new Fi(path);
                    Map added = MapIO.createMap(mapFile,true);

                    EmbedBuilder eb = formMapEmbed(added,"new map",ctx);

                    if(config.channels.containsKey("maps")){
                        config.channels.get("maps").sendMessage(eb,mapFile.file());
                        ctx.reply("Map added.");
                    }else {
                        ctx.channel.sendMessage(eb,mapFile.file());
                    }
                    MapManager.onMapAddition(added);
                } catch (IOException ex){
                    ctx.reply("I em unable to upload map.");
                    ex.printStackTrace();
                }

            }
        });

        handler.registerCommand(new Command("removemap","<name/id>") {
            {
                description = "Removes map from server.";
                role = defaultRole;
            }
            @Override
            public void run(CommandContext ctx) {
                Map removed = Tools.findMap(ctx.args[0]);

                if(removed==null){
                    ctx.reply("Map not found.");
                    return;
                }

                EmbedBuilder eb = formMapEmbed(removed,"removed map",ctx);
                CompletableFuture<Message> mess;
                if(config.channels.containsKey("maps")){
                    mess = config.channels.get("maps").sendMessage(eb,removed.file.file());
                    ctx.reply("Map removed.");
                }else {
                    mess = ctx.channel.sendMessage(eb,removed.file.file());
                }
                Timer.schedule(new Timer.Task() {
                    @Override
                    public void run() {
                        if(mess.isDone()){
                            MapManager.onMapRemoval(removed);
                            this.cancel();
                        }
                    }
                },0,1);
            }
        });

        handler.registerCommand(new Command("emergency","[time/permanent/off]") {
            {
                description = "Emergency control.";
                role = defaultRole;
            }
            @Override
            public void run(CommandContext ctx) {
                switch (Tools.setEmergencyViaCommand(ctx.args)) {
                    case success:
                        ctx.reply("Emergency started.");
                        break;
                    case stopSuccess:
                        ctx.reply("Emergency stopped.");
                        break;
                    case invalid:
                        ctx.reply("There is already ongoing emergency.");
                        break;
                    case invalidStop:
                        ctx.reply("No emergency to stop.");
                        break;
                    case invalidNotInteger:
                        ctx.reply("Time has to be integer. Its in minutes");
                        break;
                    case permanentSuccess:
                        ctx.reply("Permanent emergency started.");
                }
            }
        });

        handler.registerCommand(new Command("setrank","<name/id> <rank> [reason...]") {
            {
                description = "Sets rank of the player, available just for admins.";
                role = defaultRole;
            }
            @Override
            public void run(CommandContext ctx) {
                Player player = new Player();
                player.name=ctx.author.getName();
                switch (setRankViaCommand(player,ctx.args[0],ctx.args[1],ctx.args.length==3 ? ctx.args[2] : null)){
                    case notFound:
                        ctx.reply("Player not found.");
                        break;
                    case notPermitted:
                        ctx.reply("Changing or assigning admin rank can be done only thorough terminal.");
                        break;
                    case invalid:
                        ctx.reply("Rank not found.\nRanks:" + Arrays.toString(Rank.values())+"\n" +
                                "Custom ranks:"+Database.ranks.keySet());
                        break;
                    case success:
                        ctx.reply("Rank successfully changed.");
                }
            }
        });
    }
}
