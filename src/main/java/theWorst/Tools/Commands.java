package theWorst.Tools;

import arc.util.Log;
import arc.util.Strings;
import mindustry.entities.type.Player;
import org.bson.Document;
import theWorst.Bot;
import theWorst.database.*;
import theWorst.helpers.Administration;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static theWorst.Tools.Bundle.locPlayer;
import static theWorst.Tools.Formatting.cleanColors;
import static theWorst.Tools.General.enumContains;
import static theWorst.Tools.General.getRank;
import static theWorst.Tools.Players.*;
import static theWorst.Tools.Formatting.format;

public class Commands {
    public static boolean wrongArgAmount(Player player, String[] args, int req){
        if(args.length!=req){
            if(player != null){
                sendErrMessage(player,"args-wrong-amount",String.valueOf(req),String.valueOf(args.length));
            } else {
                logInfo("args-wrong-amount",String.valueOf(req),String.valueOf(args.length));
            }
            return true;
        }
        return false;
    }

    public static void logInfo(String key, String ... args) {
        Log.info(format(cleanColors(getTranslation(locPlayer,key)),args));
    }

    public static boolean isCommandRelated(String message){
        return message.startsWith("/") || message.equalsIgnoreCase("y") || message.equalsIgnoreCase("n");
    }

    //beautiful spaghetti in deed
    public static Res setRankViaCommand(Player player, String target, String rank, String reason) {

        PlayerD pd = Database.findData(target);

        if (pd == null) return Res.notFound;

        String by = "terminal";
        if (player != null) {
            by = player.name;
            if (getRank(pd).isAdmin) return Res.notPermitted;
        }
        if(enumContains(Rank.values(),rank)) {
            Rank r = Rank.valueOf(rank);
            //this means that this function is called from InGameCommands or BotCommands
            if (player != null && r.isAdmin) return Res.notPermitted;
            if (reason == null) reason = "Reason not provided.";
            Rank prevRank = getRank(pd);
            Database.setRank(pd, r, null);
            Bot.onRankChange(cleanColors(pd.originalName), pd.serverId, prevRank.name(), r.name(), by, reason);
            logInfo("rank-change", pd.originalName, pd.rank);
            sendMessage("rank-change", pd.originalName, getRank(pd).getName());
        } else {
            if (rank.equals("restart")) {
                pd.specialRank = null;
                sendMessage("rank-restart", pd.originalName);
                logInfo("rank-restart", pd.originalName);
            }
            else if (!Database.ranks.containsKey(rank)) return Res.invalid;
            else {
                pd.specialRank = rank;
                SpecialRank sr = Database.getSpecialRank(pd);
                if (sr != null) {
                    logInfo("rank-change", pd.originalName, pd.specialRank);
                    sendMessage("rank-change", pd.originalName, sr.getSuffix());
                }
            }
        }
        if(!pd.isOnline()) {
            Database.updateMeta(pd);
        }
        return Res.success;
    }

    //i need to do same thing on three places so this is necessary
    public static Res setEmergencyViaCommand(String[] args){
        if(args.length==0){
            if(Administration.emergency.isActive()){
                return Res.invalid;
            }
            Administration.emergency = new Administration.Emergency(3);
            return Res.success;
        }
        if(args[0].equals("permanent")){
            if(theWorst.helpers.Administration.emergency.isActive()){
                return Res.invalid;
            }
            Administration.emergency = new Administration.Emergency(-1);

            return Res.permanentSuccess;
        }
        if(args[0].equals("stop")){
            if(!theWorst.helpers.Administration.emergency.isActive()){
                return Res.invalidStop;
            }
            Administration.emergency = new Administration.Emergency(0);
            return Res.stopSuccess;
        }
        if(!Strings.canParsePostiveInt(args[0])){
            return Res.invalidNotInteger;
        }
        Administration.emergency = new Administration.Emergency(Integer.parseInt(args[0]));
        return Res.success;
    }

    public static String pdToLine(PlayerD pd){
        return "[yellow]" + pd.serverId + "[] | [gray]" + pd.originalName + "[] | " + getRank(pd).getName();
    }

    public static ArrayList<String> search(String[] args){
        List<PlayerD> all = Database.getAllMeta();
        ArrayList<String> res = new ArrayList<>();
        switch (args[0]){
            case "sort":
                if(!enumContains(Stat.values(),args[1])){
                    return null;
                }
                HashMap<Document, PlayerD> holder = new HashMap<>();
                for (Document d : Database.getAllRawMeta()) {
                    holder.put(d, Database.getMeta((String) d.get("_id")));
                }
                while (!holder.isEmpty()) {
                    long best = 0;
                    Document bestD = null;
                    for (Document d : holder.keySet()) {
                        Long val = (Long) d.get(args[1]);
                        if(val == null){
                            Log.info("error: missing property " + args[1]);
                            return null;
                        }
                        if (val >= best) {
                            best = val;
                            bestD = d;
                        }
                    }
                    res.add(pdToLine(holder.get(bestD)));
                    holder.remove(bestD);
                }
                break;
            case "rank":
                for(PlayerD pd : all){
                    if(pd.rank.equals(args[1])) res.add(pdToLine(pd));
                }
                break;
            default:
                for(PlayerD pd : all){
                    if(cleanColors(pd.originalName).startsWith(args[0])) res.add(pdToLine(pd));
                }
                break;
        }
        if(args.length == 3){
            int size = res.size();
            ArrayList<String> reversed = new ArrayList<>();
            for(int i = 0; i < size; i++){
                reversed.add(res.get(size - 1 - i));
            }
            return reversed;
        }
        return res;
    }

    public enum Res{
        notFound,
        notPermitted,
        invalid,
        invalidNotInteger,
        success,
        permanentSuccess,
        stopSuccess,
        invalidStop
    }
}
