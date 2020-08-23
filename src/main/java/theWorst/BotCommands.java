package theWorst;

import arc.files.Fi;
import arc.math.Mathf;
import arc.util.Strings;
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
import theWorst.database.*;
import theWorst.discord.Command;
import theWorst.discord.CommandContext;
import theWorst.discord.DiscordCommands;
import theWorst.helpers.maps.MDoc;
import theWorst.helpers.maps.MapManager;

import org.bson.Document;
import theWorst.helpers.maps.Stat;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static mindustry.Vars.*;
import static theWorst.Bot.*;
import static theWorst.tools.Bundle.locPlayer;
import static theWorst.tools.Commands.*;
import static theWorst.tools.Formatting.cleanColors;
import static theWorst.tools.Formatting.milsToTime;
import static theWorst.tools.Json.makeFullPath;
import static theWorst.tools.Maps.*;

public class BotCommands {

    public BotCommands(DiscordCommands handler) {
        String[] defaultRole = new String[]{"admin"};

        handler.registerCommand(new Command("help") {
            {
                description = "Shows all commands and their description.";
            }

            @Override
            public void run(CommandContext ctx) {
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("COMMANDS")
                        .setColor(Color.orange);
                StringBuilder sb = new StringBuilder();
                sb.append("*!commandName - restriction - <necessary> [optional] |.fileExtension| - description*\n");
                for (String s : handler.commands.keySet()) {
                    sb.append(handler.commands.get(s).getInfo()).append("\n");
                }
                ctx.channel.sendMessage(eb.setDescription(sb.toString()));
            }
        });

        handler.registerCommand(new Command("link", "<serverId/disconnect>") {
            {
                description = "Links your discord with your server profile.";
            }

            @Override
            public void run(CommandContext ctx) {
                Optional<User> optionalUser = ctx.author.asUser();
                if (!optionalUser.isPresent()) {
                    ctx.reply("It appears that you are not a user.");
                    return;
                }
                User user = optionalUser.get();
                Doc found = Database.data.getDocBiLink(user.getIdAsString());
                if (ctx.args[0].equals("disconnect")) {

                    if (found == null) {
                        ctx.reply("You don t have any linked account to disconnect.");
                    } else {
                        ctx.reply("Account with user name **" + cleanColors(found.getName()) +
                                "** wos successfully disconnected.");
                        Database.data.remove(found.getId(), "link");
                    }
                    return;
                }

                if (!Strings.canParsePostiveInt(ctx.args[0])) {
                    ctx.reply("Server Id has to be integer.");
                    return;
                }

                if (found != null) {
                    ctx.reply("Your account is already linked to **" + cleanColors(found.getName()) +
                            "**. If you want to change the linked account use **" + config.prefix + "link disconnect**.");
                    return;
                }

                found = Database.data.getDoc(Long.parseLong(ctx.args[0]));

                if (found == null) {
                    ctx.reply("Account not found.");
                    return;
                }

                String pin = String.valueOf(Mathf.random(1000, 9999));
                user.sendMessage("Use /link " + pin + " command in game to confirm the linkage.");
                pendingLinks.put(found.getId(), new LinkData(user.getName(), pin, user.getIdAsString()));
            }
        });

        handler.registerCommand(new Command("gamestate") {
            {
                description = "Shows information about current game state.";
            }

            @Override
            public void run(CommandContext ctx) {
                EmbedBuilder eb = new EmbedBuilder().setTitle("GAME STATE");
                if (state.is(GameState.State.playing)) {
                    eb.addField("map", world.getMap().name())
                            .addField("mode", state.rules.mode().name())
                            .addInlineField("players", String.valueOf(playerGroup.size()))
                            .addInlineField("wave", String.valueOf(state.wave))
                            .addInlineField("enemies", String.valueOf(state.enemies))
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
                for (Player p : playerGroup) {
                    PD pd = Database.getData(p);
                    sb.append(pd.name).append(" | ").append(pd.rank.name).append(" | ").append(pd.id).append("\n");
                }
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("PLAYERS ONLINE")
                        .setColor(Color.green)
                        .setDescription(sb.toString());
                if (playerGroup.size() == 0) eb.setDescription("No players online.");
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
                if (data.cores.isEmpty()) {
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

        handler.registerCommand(new Command("downloadmap", "<mapName/id>") {
            {
                description = "Preview and download a server map in a .msav file format.";
            }

            public void run(CommandContext ctx) {

                Map found = findMap(ctx.args[0]);

                if (found == null) {
                    ctx.reply("Map not found!");
                    return;
                }

                ctx.channel.sendMessage(formMapEmbed(found, "download", ctx), found.file.file());
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
                StringBuilder b = new StringBuilder();
                int i = 0;
                for (Map map : maps.customMaps()) {
                    double rating = new MDoc(map).getRating();
                    b.append(i).append(" | ").append(map.name()).append(" | ").append(String.format("%.2f/10", rating)).append("\n");
                    i++;
                }
                embed.setDescription(b.toString());
                ctx.channel.sendMessage(embed);
            }
        });

        handler.registerCommand(new Command("search", "<searchKey/sort/rank/specialrank/donationlevel/online> [sortType/rankName] [reverse]") {
            {
                description = "Shows first 20 results of search.";
            }

            @Override
            public void run(CommandContext ctx) {
                ArrayList<String> res = Database.search(ctx.args, 20, locPlayer);
                StringBuilder mb = new StringBuilder();
                for (String s : res) {
                    mb.append(s).append("\n");
                }
                if (res.isEmpty()) {
                    ctx.reply("No results found.");
                } else {
                    ctx.channel.sendMessage(cleanColors(mb.toString()));
                }
            }
        });

        handler.registerCommand(new Command("info", "<name/id> [stats]") {
            {
                description = "Shows info about player.";
            }

            @Override
            public void run(CommandContext ctx) {
                Doc doc = Database.data.getDoc(Long.parseLong(ctx.args[0]));
                if (doc == null) {
                    ctx.reply("No data found.");
                    return;
                }
                String data = ctx.args.length == 1 ? cleanColors(doc.toString(null)) : cleanColors(doc.statsToString(null));
                ctx.channel.sendMessage(new EmbedBuilder().setDescription(data).setTitle("PLAYER INFO").setColor(Color.blue));
            }
        });

        handler.registerCommand(new Command("postmap", "|.msav|") {
            @Override
            public void run(CommandContext ctx) {
                Message message = ctx.event.getMessage();
                MessageAttachment a = message.getAttachments().get(0);

                String path = Global.dir + "postedMaps/" + a.getFileName();
                makeFullPath(path);
                try {
                    Files.copy(a.downloadAsInputStream(), Paths.get(path), StandardCopyOption.REPLACE_EXISTING);
                    Fi mapFile = new Fi(path);
                    Map posted = MapIO.createMap(mapFile, true);

                    EmbedBuilder eb = formMapEmbed(posted, "map post", ctx);

                    if (config.channels.containsKey("maps")) {
                        config.channels.get("maps").sendMessage(eb, mapFile.file());
                        ctx.reply("Map posted.");
                    } else {
                        ctx.channel.sendMessage(eb, mapFile.file());
                    }
                } catch (IOException ex) {
                    ctx.reply("I em unable to post your map.");
                }
            }
        });

        handler.registerCommand(new Command("addmap", "|.msav|") {
            {
                description = "Adds map to server.";
                role = defaultRole;
            }

            @Override
            public void run(CommandContext ctx) {
                Bot.addMap(ctx, true);
            }
        });

        handler.registerCommand(new Command("removemap", "<name/id>") {
            {
                description = "Removes map from server.";
                role = defaultRole;
            }

            @Override
            public void run(CommandContext ctx) {
                Bot.removeMap(ctx, true);
            }
        });

        //todo test
        handler.registerCommand(new Command("updatemap", "<id> |.msav|") {
            {
                description = "Updates map version without restarting its data.";
                role = defaultRole;
            }
            @Override
            public void run(CommandContext ctx) {
                Map removed = Bot.removeMap(ctx, false);
                if(removed == null) return;
                Document doc = new MDoc(removed).data;
                MapManager.onMapRemoval(removed);
                Map added = Bot.addMap(ctx, false);
                if(added == null) return;
                doc.append("_id", MDoc.getID(added));
                doc.append(Stat.firstAirWave.name(), MDoc.getFirstAyrWave(added));
                MapManager.data.insertOne(doc);
            }
        });

        handler.registerCommand(new Command("emergency", "[time/permanent/off]") {
            {
                description = "Emergency control.";
                role = defaultRole;
            }

            @Override
            public void run(CommandContext ctx) {
                switch (setEmergency(ctx.args)) {
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

        handler.registerCommand(new Command("setrank", "<name/id> <rank> [reason...]") {
            {
                description = "Sets rank of the player, available just for admins.";
                role = defaultRole;
            }

            @Override
            public void run(CommandContext ctx) {
                Player player = new Player();
                player.name = ctx.author.getName();
                switch (setRank(player, ctx.args[0], ctx.args[1], ctx.args.length == 3 ? ctx.args[2] : null)) {
                    case notFound:
                        ctx.reply("Player not found.");
                        break;
                    case notPermitted:
                        ctx.reply("Changing or assigning admin rank can be done only thorough terminal.");
                        break;
                    case invalid:
                        ctx.reply("Rank not found.\nRanks:" + Ranks.buildIn.keySet() + "\n" +
                                "Custom ranks:" + Ranks.special.keySet());
                        break;
                    case success:
                        ctx.reply("Rank successfully changed.");
                }
            }
        });

        handler.registerCommand(new Command("report", "<id> <reason...> |optional screenshot|") {//todo test
            {
                description = "Simply reports the player to admins.";
            }
            @Override
            public void run(CommandContext ctx) {
                switch (ReportPlayer(null, ctx, ctx.ExtractMessage(1), ctx.args[0], ctx.author.getId())){
                    case notFound:
                        ctx.reply("Player not found");
                        break;
                    case invalidNotInteger:
                        ctx.reply("First argument has to be integer.");
                    case invalid:
                        ctx.reply("No one would see your report anyway.");
                        break;
                    case notPermitted:
                        ctx.reply("You still have penalty from last time.Penalty duration is: " + milsToTime(Global.limits.reportPenalty));
                        break;
                    case adminReport:
                        ctx.reply("You cannot report admin.");
                        break;
                    case grieferReport:
                        ctx.reply("This player is already marked griefer.");
                        break;
                    case success:
                        ctx.reply("Player reported.");
                }
            }
        });
    }
}

