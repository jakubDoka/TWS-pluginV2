package theWorst.Tools;

import arc.util.Strings;
import mindustry.entities.type.Player;
import mindustry.gen.Call;
import theWorst.database.Database;
import theWorst.database.Perm;
import theWorst.database.PlayerD;
import theWorst.database.Setting;

import java.util.Locale;

import static mindustry.Vars.playerGroup;
import static theWorst.Tools.Bundle.*;
import static theWorst.Tools.Formatting.format;
import static theWorst.Tools.Formatting.cleanName;

public class Players {
    private static final String prefix = "[coral][[[scarlet]Server[]]:[#cbcbcb]";

    public static String getPlayerList(){
        StringBuilder builder = new StringBuilder();
        builder.append("[orange]Players: \n");
        for(Player p : playerGroup.all()){
            if(p.isAdmin || p.con == null || Database.hasPerm(p, Perm.higher)) continue;
            builder.append("[lightgray]").append(p.name);
            builder.append("[accent] (ID:").append(Database.getData(p).serverId).append(")\n");
        }
        return builder.toString();
    }

    public static Player findPlayer(String arg) {
        if(Strings.canParseInt(arg)){
            int id = Strings.parseInt(arg);
            return  playerGroup.find(p -> Database.getData(p).serverId == id);
        }
        for(Player p:playerGroup){
            String pName = cleanName(p.name);
            if(pName.equalsIgnoreCase(arg)){
                return p;
            }
        }
        return null;
    }

    public static void sendMessage(Player player,String key,String ... args){
        PlayerD pd = Database.getData(player);
        player.sendMessage(prefix + format(getTranslation(pd,key),args));
    }

    public static void sendErrMessage(Player player,String key,String ... args){
        PlayerD pd = Database.getData(player);
        player.sendMessage(prefix + "[#bc5757]" + format(getTranslation(pd,key),args));
    }

    public static void sendInfoPopup(Player player, String kay, String ... args) {
        PlayerD pd = Database.getData(player);
        Call.onInfoMessage(player.con,format(getTranslation(pd,kay),args));
    }

    public static void kick(Player player, String kay, String ... args ){
        PlayerD pd = Database.getData(player);
        player.con.kick(format(getTranslation(pd,kay),args));
    }


    public static String getTranslation(PlayerD pd, String key){
        if(pd != null && pd.bundle != null && pd.bundle.containsKey(key) && pd.settings.contains(Setting.translate.name())){
            return pd.bundle.getString(key);
        }
        if(!defaultBundle.containsKey(key)) return "error: bundle " + key + "is missing. Please report it.";
        return defaultBundle.getString(key);
    }

    public static boolean cnaTranslate(PlayerD pd, String key){
        return pd.bundle.containsKey(key) || defaultBundle.containsKey(key);
    }

    //because players may have different bundle
    public static void sendMessage(String key, String ... args){
        for(Player p : playerGroup){
            sendMessage(p, key, args);
        }
    }

    public static void sendChatMessage(Player sender, String message){
        for(Player p : playerGroup){
            //filtering messages
            if(!Database.hasEnabled(p,Setting.chat) || Database.getData(p).mutes.contains(sender.uuid)) continue;
            p.sendMessage("[coral][[[#"+sender.color+"]"+sender.name+"[]]:[]"+message);
        }
    }

    public static String getCountryCode(Locale loc){
        return loc.getLanguage() + "_" + loc.getCountry();
    }

}
