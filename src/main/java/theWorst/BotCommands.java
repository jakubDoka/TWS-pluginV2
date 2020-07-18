package theWorst;

import arc.files.Fi;
import arc.math.Mathf;
import arc.util.Strings;
import arc.util.Timer;
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
import theWorst.dataBase.Database;
import theWorst.dataBase.PlayerD;
import theWorst.dataBase.Rank;
import theWorst.dataBase.Stat;
import theWorst.discord.Command;
import theWorst.discord.CommandContext;
import theWorst.discord.DiscordCommands;
import theWorst.helpers.MapManager;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static mindustry.Vars.*;
import static theWorst.Bot.*;
import static theWorst.Tools.Commands.*;
import static theWorst.Tools.Formatting.cleanColors;
import static theWorst.Tools.Json.makeFullPath;
import static theWorst.Tools.Maps.*;

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

       handler.registerCommand(new Command("link","<serverId/disconnect>") {
            {
                description = "Links your discord with your server profile.";
            }
            @Override
            public void run(CommandContext ctx) {
                Optional<User> optionalUser = ctx.author.asUser();
                if(!optionalUser.isPresent()){
                    ctx.reply("It appears that you are not a user.");
                    return;
                }
                User user = optionalUser.get();
                PlayerD found = Database.query("discordLink", user.getIdAsString());
                if(ctx.args[0].equals("disconnect")) {

                    if( found == null){
                        ctx.reply("You don t have any linked account to disconnect.");
                    } else {
                        ctx.reply("Account with user name **" + cleanColors(found.originalName) +
                                "** wos successfully disconnected.");
                        found.discordLink = null;
                        Database.updateMeta(found);
                    }
                    return;
                }
                if(!Strings.canParsePostiveInt(ctx.args[0])){
                    ctx.reply("Server Id has to be integer.");
                    return;
                }

                if (found != null) {
                    ctx.reply("Your account is already linked to **" + cleanColors(found.originalName) +
                            "**. If you want to change the linked account use **" + config.prefix + "link disconnect**.");
                    return;
                }
                PlayerD pd = Database.getMetaById(Long.parseLong(ctx.args[0]));
                if(pd==null){
                    ctx.reply("Account not found.");
                    return;
                }
                if(pd.discordLink != null && pd.discordLink.equals(user.getIdAsString())){
                    ctx.reply("You already have this account linked.");
                }
                String pin = String.valueOf(Mathf.random(1000,9999));
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
                if(state.is(GameState.State.playing)){
                    eb.addField("map", world.getMap().name())
                            .addField("mode", state.rules.mode().name())
                            .addInlineField("players",String.valueOf(playerGroup.size()))
                            .addInlineField("wave",String.valueOf(state.wave))
                            .addInlineField("enemies",String.valueOf(state.enemies))
                            .setImage(getMiniMapImg())
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
                for(Player p:playerGroup){
                    PlayerD pd = Database.getData(p);
                    sb.append(pd.originalName).append(" | ").append(pd.rank).append(" | ").append(pd.serverId).append("\n");
                }
                EmbedBuilder eb =new EmbedBuilder()
                        .setTitle("PLAYERS ONLINE")
                        .setColor(Color.green)
                        .setDescription(sb.toString());
                if(playerGroup.size()==0) eb.setDescription("No players online.");
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

                Map found = findMap(ctx.args[0]);

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
                for(Map map:maps.customMaps()){
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
                ArrayList<String> res = Database.search(ctx.args, 20);

                StringBuilder mb = new StringBuilder();
                for(String s : res) {
                    mb.append(s).append("\n");
                }
                if (res.isEmpty()) {
                    ctx.reply("No results found.");
                } else {
                    ctx.channel.sendMessage(mb.toString());
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
                String data = cleanColors(pd.toString(null));
                ctx.channel.sendMessage(new EmbedBuilder().setDescription(data).setTitle("PLAYER INFO").setColor(Color.blue));
            }
        });

        handler.registerCommand(new Command("postmap","|.msav|") {
            @Override
            public void run(CommandContext ctx) {
                Message message = ctx.event.getMessage();
                MessageAttachment a = message.getAttachments().get(0);

                String path = Global.dir + "postedMaps/" + a.getFileName();
                makeFullPath(path);
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
                Map removed = findMap(ctx.args[0]);

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
                switch (setEmergencyViaCommand(ctx.args)) {
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
