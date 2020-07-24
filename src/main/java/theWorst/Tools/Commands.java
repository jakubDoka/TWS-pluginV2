package theWorst.Tools;

import arc.util.Log;
import arc.util.Strings;
import mindustry.entities.type.Player;
import theWorst.Bot;
import theWorst.database.*;
import theWorst.helpers.Administration;

import static theWorst.Tools.Bundle.locPlayer;
import static theWorst.Tools.Formatting.cleanColors;
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

        DataHandler.Doc doc = Database.findData(target);
        if (doc == null) return Res.notFound;
        String name = doc.getName();
        Rank current = doc.getRank(RankType.rank);
        String by = "terminal";
        if (player != null) {
            by = player.name;
            if (current.isAdmin) return Res.notPermitted;
        }
        Rank rk = Ranks.buildIn.get(rank);
        Rank sr = Ranks.special.get(rank);
        if(rk != null) {
            //this means that this function is called from InGameCommands or BotCommands
            if (player != null && rk.isAdmin) return Res.notPermitted;

            Bot.onRankChange(name, doc.getId(), current.name, rk.name, by, reason);
            logInfo("rank-change", name, rk.name);
            sendMessage("rank-change", name, rk.getSuffix());
            Database.setRank(doc.getId(), rk);
            PD pd = Database.online.get(doc.getUuid());
            if(pd != null) {
                pd.removeRank(pd.rank);
                pd.addRank(rk);
                pd.rank = rk;
            }
        } else {
            if (rank.equals("restart")) {
                Database.data.removeRank(doc.getId(), RankType.specialRank);
                sendMessage("rank-restart", name);
                logInfo("rank-restart", name);
            }
            else if (sr == null) return Res.invalid;
            else {
                Database.data.setRank(doc.getUuid(), sr, RankType.specialRank);
                logInfo("rank-change", name, sr.name);
                sendMessage("rank-change", name, sr.getSuffix());
            }
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
