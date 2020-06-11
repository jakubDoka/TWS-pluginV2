package theWorst.discord;

import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.event.message.MessageCreateEvent;

public class CommandContext {
    public final TextChannel channel;
    public final MessageAuthor author;
    public final MessageCreateEvent event;
    public final String[] args;
    public final String message;

    public CommandContext(MessageCreateEvent event, String[] args, String message) {
        this.event=event;
        this.args=args;
        this.message=message;
        this.author=event.getMessageAuthor();
        this.channel=event.getChannel();
    }

    public void reply(String message) {
        MessageBuilder mb = new MessageBuilder();
        mb.append(message);
        mb.send(channel);
    }
}
