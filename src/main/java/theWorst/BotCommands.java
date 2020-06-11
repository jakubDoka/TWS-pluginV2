package theWorst;

import arc.files.Fi;
import arc.math.Mathf;
import arc.struct.Array;
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
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import theWorst.database.Database;
import theWorst.database.PlayerD;
import theWorst.database.Rank;
import theWorst.discord.Command;
import theWorst.discord.CommandContext;
import theWorst.discord.DiscordCommands;

import java.awt.*;
import java.io.File;
import java.io.IOException;
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

        /*handler.registerCommand(new Command("downloadmap","<mapName/id>") {
            {
                description = "Preview and download a server map in a .msav file format.";
            }
            public void run(CommandContext ctx) {

                Map found = MapManager.findMap(ctx.args[0]);

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
                    double rating= MapManager.getMapRating(map);
                    b.append(i).append(" | ").append(map.name()).append(" | ").append(String.format("%.2f/10",rating)).append("\n");
                    i++;
                }
                embed.setDescription(b.toString());
                ctx.channel.sendMessage(embed);
            }
        });

        handler.registerCommand(new Command("search","<searchKey/chinese/russian/sort/online/rank> [sortType/rankName] [reverse]") {
            {
                description = "Search for player in server database.Be careful database is big so if i resolve huge " +
                        "search result i will send it to you in dm";
            }
            @Override
            public void run(CommandContext ctx) {
                Array<String> res = Tools.getSearchResult(ctx.args, null, ctx.channel);
                if (res == null) return;

                StringBuilder mb = new StringBuilder();
                int shown = 0;
                int begin = Math.max(0,res.size-20);
                for (int i = begin; i <res.size; i++) {
                    String line =Tools.cleanColors(res.get(i));
                    if(mb.length()+line.length()>maxMessageLength) break;
                    shown++;
                    mb.insert(0,Tools.cleanColors(res.get(i))+"\n");
                }
                if (res.isEmpty()) {
                    ctx.reply("No results found.");
                } else {
                    ctx.channel.sendMessage(mb.toString());
                    if(shown!=res.size){
                        ctx.reply("I em showing just "+shown+" out of "+res.size+".");
                    }
                }
            }
        });*/

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

        /*handler.registerCommand(new Command("postmap","|.msav|") {
            @Override
            public void run(CommandContext ctx) {
                Message message = ctx.event.getMessage();
                MessageAttachment a = message.getAttachments().get(0);

                String dir =Main.directory+"postedMaps/";
                new File(dir).mkdir();
                try {
                    String path = dir+a.getFileName();
                    Tools.downloadFile(a.downloadAsInputStream(),path);
                    Fi mapFile = new Fi(path);
                    Map posted = MapIO.createMap(mapFile,true);

                    EmbedBuilder eb = formMapEmbed(posted,"map post",ctx);

                    if(channels.containsKey("maps")){
                        channels.get("maps").sendMessage(eb,mapFile.file());
                        ctx.reply("Map posted.");
                    }else {
                        ctx.channel.sendMessage(eb,mapFile.file());
                    }
                } catch (IOException ex){
                    ctx.reply("I em unable to post your map.");
                }
            }
        });

        handler.registerCommand(new Command("restrict","<command> <role/remove>") {
            {
                description = "Sets role restriction for command.";
                role = admin;
            }
            @Override
            public void run(CommandContext ctx) {
                if(!handler.hasCommand(ctx.args[0]) ){
                    String match = Tools.findBestMatch(ctx.args[0],handler.commands.keySet());
                    ctx.reply("Sorry i don t know this command.");
                    if(match==null) return;
                    ctx.reply("Did you mean "+match+"?");
                    return;
                }
                if(ctx.args[1].equals("remove")){
                    handler.commands.get(ctx.args[0]).role=null;
                    ctx.reply(String.format("Restriction from %s wos removed.", ctx.args[0]));
                }
                if(!roles.containsKey(ctx.args[1])){
                    ctx.reply("It might be little confusing but role names match names in the config file.\n"
                            +roles.keySet().toString());
                    return;
                }
                handler.commands.get(ctx.args[0]).role=roles.get(ctx.args[1]);

                ctx.reply(String.format("Role of %s is now %s.", ctx.args[0], ctx.args[1]));
            }
        });

        handler.registerCommand(new Command("addmap","|.msav|") {
            {
                description = "Adds map to server.";
                role=admin;
            }
            @Override
            public void run(CommandContext ctx) {
                Message message = ctx.event.getMessage();
                MessageAttachment a = message.getAttachments().get(0);
                try {
                    String path="config/maps/"+a.getFileName();
                    Tools.downloadFile(a.downloadAsInputStream(),path);
                    Fi mapFile = new Fi(path);
                    Map added = MapIO.createMap(mapFile,true);

                    EmbedBuilder eb = formMapEmbed(added,"new map",ctx);

                    if(channels.containsKey("maps")){
                        channels.get("maps").sendMessage(eb,mapFile.file());
                        ctx.reply("Map added.");
                    }else {
                        ctx.channel.sendMessage(eb,mapFile.file());
                    }

                    maps.reload();
                } catch (IOException ex){
                    ctx.reply("I em unable to upload map.");
                    ex.printStackTrace();
                }

            }
        });

        handler.registerCommand(new Command("removemap","<name/id>") {
            {
                description = "Removes map from server.";
                role=admin;
            }
            @Override
            public void run(CommandContext ctx) {
                Map removed = MapManager.findMap(ctx.args[0]);

                if(removed==null){
                    ctx.reply("Map not found.");
                    return;
                }

                EmbedBuilder eb = formMapEmbed(removed,"removed map",ctx);
                CompletableFuture<Message> mess;
                if(channels.containsKey("maps")){
                    mess =  channels.get("maps").sendMessage(eb,removed.file.file());
                    ctx.reply("Map removed.");
                }else {
                    mess = ctx.channel.sendMessage(eb,removed.file.file());
                }
                Timer.schedule(new Timer.Task() {
                    @Override
                    public void run() {
                        if(mess.isDone()){
                            maps.removeMap(removed);
                            maps.reload();
                            this.cancel();
                        }
                    }
                },0,1);
            }
        });*/

        /*handler.registerCommand(new Command("emergency","[off]") {
            {
                description = "Initialises or terminates emergency, available just for admins.";
                role = admin;
            }
            @Override
            public void run(CommandContext ctx) {
                ActionManager.switchEmergency(ctx.args.length==1);
                if(ActionManager.isEmergency()){
                    ctx.reply("Emergency started.");
                } else {
                    ctx.reply("Emergency stopped.");
                }
            }
        });*/

        handler.registerCommand(new Command("setrank","<name/id> <rank> [reason...]") {
            {
                description = "Sets rank of the player, available just for admins.";
                role = new String[]{"admin"};
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
                    case invalidRank:
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
