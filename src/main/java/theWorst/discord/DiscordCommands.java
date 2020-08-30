package theWorst.discord;

import mindustry.gen.Call;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.ServerChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static theWorst.Bot.*;


public class DiscordCommands implements MessageCreateListener {
    public HashMap<String, Command> commands = new HashMap<>();

    public void registerCommand(Command c){
        commands.put(c.name,c);
    }

    public boolean hasCommand(String command){
        return commands.containsKey(command);
    }

    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        String message = event.getMessageContent();

        if(event.getMessageAuthor().isBotUser()) {
            if(event.isPrivateMessage()) {
                Call.sendMessage(event.getMessageContent());
            }
            return;
        }
        if(!message.startsWith(config.prefix)) return;
        Channel chan = config.channels.get("commands");
        if(chan != null && chan.getId() != event.getChannel().getId()){
            String msg = "This is not channel for commands.";
            Optional<ServerChannel> optionalServerChannel = chan.asServerChannel();
            if(optionalServerChannel.isPresent()) msg+= " Go to "+optionalServerChannel.get().getName()+".";
            event.getChannel().sendMessage(msg);
            return;
        }
        int nameLength = message.indexOf(" ");
        if(nameLength<0){
            runCommand(message.replace(config.prefix,""),new CommandContext(event,new String[0],null));
            return;
        }
        String theMessage = message.substring(nameLength+1);
        String[] args = theMessage.split(" ");
        String name = message.substring(config.prefix.length(),nameLength);
        runCommand(name,new CommandContext(event,args,theMessage));
    }

    /**Validates command**/
    private void runCommand(String name, CommandContext ctx) {
        Command command=commands.get(name);

        if(command==null){
            ctx.reply("Sorry i don t know this command. Try "+config.prefix+"help.");
            return;
        }
        if(!command.hasPerm(ctx)){
            EmbedBuilder msg= new EmbedBuilder()
                    .setColor(Color.red)
                    .setTitle("ACCESS DENIED!")
                    .setDescription("You don't have high enough permission to use this command.");
            ctx.channel.sendMessage(msg);
            return;
        }
        Message message=ctx.event.getMessage();
        List<MessageAttachment> mas = message.getAttachments();
        boolean tooFew = ctx.args.length<command.minArgs,tooMatch=ctx.args.length>command.maxArgs;
        boolean correctFiles = command.attachment==null || (mas.size() == 1 && mas.get(0).getFileName().endsWith(command.attachment));
        if(tooFew || tooMatch || !correctFiles){
            EmbedBuilder eb= new EmbedBuilder()
                    .setColor(Color.red)
                    .setDescription("Valid format : " + config.prefix + name + " " + command.argStruct );
            if(tooFew) eb.setTitle("TOO FEW ARGUMENTS!" );
            else if(tooMatch) eb.setTitle( "TOO MATCH ARGUMENTS!");
            else eb.setTitle("INCORRECT ATTACHMENT!");
            ctx.channel.sendMessage(eb);
            return;
        }
        command.run(ctx);
    }
}
