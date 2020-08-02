package theWorst.tools;

import arc.util.Strings;
import mindustry.entities.type.Player;
import mindustry.gen.Call;
import theWorst.database.Database;
import theWorst.database.PD;
import theWorst.database.Perm;
import theWorst.database.Setting;

import java.util.Locale;

import static mindustry.Vars.playerGroup;
import static theWorst.database.Database.getData;
import static theWorst.tools.Bundle.*;
import static theWorst.tools.Formatting.*;

public class Players {
    private static final String prefix = "[coral][[[scarlet]Server[]]:[#cbcbcb]";

    public static String getPlayerList(){
        StringBuilder builder = new StringBuilder();
        builder.append("[orange]Players: \n");
        for(Player p : playerGroup.all()){
            PD pd = getData(p);
            if(p.isAdmin || p.con == null || pd.hasPermLevel(Perm.higher)) continue;
            builder.append("[lightgray]").append(p.name);
            builder.append("[accent] (ID:").append(pd.id).append(")\n");
        }
        return builder.toString();
    }

    public static Player findPlayer(String arg) {
        if(Strings.canParseInt(arg)){
            int id = Strings.parseInt(arg);
            return  playerGroup.find(p -> getData(p).id == id);
        }
        for(Player p:playerGroup){
            if(getData(p).name.equalsIgnoreCase(arg)){
                return p;
            }
        }
        return null;
    }

    public static void sendMessage(Player player,String key,String ... args){
        PD pd = getData(player);
        player.sendMessage(prefix + format(getTranslation(pd,key),args));
    }

    public static void sendErrMessage(Player player,String key,String ... args){
        PD pd = getData(player);
        player.sendMessage(prefix + "[#bc5757]" + format(getTranslation(pd,key),args));
    }

    public static void sendInfoPopup(Player player, String kay, String ... args) {
        PD pd = getData(player);
        Call.onInfoMessage(player.con,format(getTranslation(pd,kay),args));
    }

    public static void kick(Player player, String kay, int duration, String ... args ){
        PD pd = getData(player);
        player.con.kick(format(getTranslation(pd,kay),args), duration);
    }


    public static String getTranslation(PD pd, String key){
        if(pd != null && pd.bundle != null && pd.bundle.containsKey(key)){
            return pd.bundle.getString(key);
        }
        if(!defaultBundle.containsKey(key)) return "error: bundle " + key + " is missing. Please report it.";
        return defaultBundle.getString(key);
    }

    public static boolean cnaTranslate(PD pd, String key){
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
            if(!Database.hasEnabled(p,Setting.chat) || Database.hasMuted(p, sender)) continue;
            p.sendMessage(formatMessage(sender, MessageMode.normal)+message);
        }
    }

    public static void sendMessageToAdmins(Player sender, String message, String ... args) {
        for(Player p : playerGroup) {
            if(Database.hasMuted(p, sender)) continue;
            p.sendMessage(formatMessage(sender, MessageMode.admin)+ format(getTranslation(getData(p), message), args));
        }
    }

    public static String getCountryCode(Locale loc){
        return loc.getLanguage() + "_" + loc.getCountry();
    }



}
