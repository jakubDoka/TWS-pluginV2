package theWorst.discord;

import org.javacord.api.entity.permission.Role;
import theWorst.Bot;

import java.util.Arrays;
import java.util.List;

import static theWorst.Bot.config;
public abstract class Command {
    public String name;

    public String[] role = null;

    public String description = "No description provided.";

    public String argStruct="noArgs";

    public String attachment = null;

    public int maxArgs=0,minArgs=0;

    public String getInfo(){
        return String.format("**%s**-for %s-**%s**-%s",
                config.prefix+name,
                role==null ? "everyone" : Arrays.toString(role) +"s",
                argStruct,description);
    }

    public Command(String name,String argStruct) {
        this.name=name;
        this.argStruct=argStruct;
        resolveArgStruct();
    }

    public Command(String name) {
        this.name=name;
    }

    /** determining how match arguments command suppose to have and and what type of attachment has to be provided.
        Called only once!!**/
    public void resolveArgStruct() {
        if(argStruct == null) return;
        String[] args = argStruct.split(" ");
        for(String arg:args){
            if(arg.startsWith("<")){
                maxArgs++;
                minArgs++;
            } else if(arg.startsWith("[")){
                maxArgs++;
                //just being more explicit
            } else if(arg.startsWith("|")){
                attachment= arg.replace("|","");
            }
            if(arg.contains("...")){
                maxArgs=1000;
                break;
            }
        }
    }

    public abstract void run(CommandContext ctx);

    public boolean hasPerm(CommandContext ctx) {
        if (ctx.event.isPrivateMessage()) return false;
        // role is null so everyone can use command
        if (role == null) return true;
        // i am simply not going to touch this
        List<Role> roles = ctx.event.getMessageAuthor().asUser().get().getRoles(ctx.event.getServer().get());
        for(String r : role){
            if(roles.contains(config.roles.get(r))) return true;
        }
        return false;
    }
}
