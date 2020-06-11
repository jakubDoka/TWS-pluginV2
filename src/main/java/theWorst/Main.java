package theWorst;

import arc.Core;
import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.maps.generators.Generator;
import mindustry.maps.generators.MapGenerator;
import mindustry.plugin.Plugin;
import mindustry.type.Zone;
import mindustry.world.Tile;
import theWorst.database.BackupManager;
import theWorst.database.Database;
import theWorst.database.Rank;
import theWorst.helpers.Administration;
import theWorst.helpers.Destroyable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import static arc.util.Log.info;
import static mindustry.Vars.*;
import static theWorst.Tools.*;

public class Main extends Plugin {
    static Administration administration =  new Administration();
    static ArrayList<Destroyable> destroyable = new ArrayList<>();

    public Main() {
        new Database();
        new Bot();
    }

    public void addDestroyable(Destroyable destroyable){
        Main.destroyable.add(destroyable);
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("dbdrop","Do not touch this if you don't want to erase database.",args->{
            if(state.is(GameState.State.playing)){
                logInfo("dbdrop-refuse-because-playing");
                return;
            }
            Database.clean();
            logInfo("dbdrop-erased");
                });
        handler.register("dbbackup","<add/remove/load/show> [idx]",
                "Shows backups and their indexes or adds, removes or loads the backup by index.",args->{
            if(args.length==1){
                switch (args[0]){
                    case "show":
                        int idx = 0;
                        logInfo("backup");
                        for(String s : BackupManager.getIndexedBackups()){
                            Log.info(String.format("%d : %s", idx, new Date(Long.parseLong(s)).toString()));
                        }
                        return;
                    case "add":
                        BackupManager.addBackup();
                        logInfo("backup-add");
                        return;
                }
            } else {
                if(!Strings.canParsePostiveInt(args[1])){
                    logInfo("refuse-because-not-integer","3");
                    return;
                }
                switch (args[0]){
                    case "load":
                        if(BackupManager.loadBackup(Integer.parseInt(args[1]))){
                            logInfo("backup-load",args[1]);
                        } else {
                            logInfo("backup-out-of-range");
                        }
                        return;
                    case "remove":
                        if(BackupManager.removeBackup(Integer.parseInt(args[1]))){
                            logInfo("backup-remove",args[1]);
                        } else {
                            logInfo("backup-out-of-range");
                        }
                        return;
                }
            }
            logInfo("invalid-mode");
        });

        handler.register("setrank", "<uuid/name/id> <rank/restart> [reason...]",
                "Sets rank of the player.", args -> {
            switch (setRankViaCommand(null,args[0],args[1],args.length==3 ? args[2] : null)){
                case notFound:
                    logInfo("player-not-found");
                    break;
                case invalidRank:
                    logInfo("rank-not-found");
                    logInfo("rank-s",Arrays.toString(Rank.values()));
                    logInfo("rank-s-custom",Database.ranks.keySet().toString());
            }
        });

        handler.register("exit", "Exit the server application.", arg -> {
            info("Shutting down server.");
            net.dispose();
            Bot.disconnect();
            Core.app.exit();
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        //just to split it a little it
        new InGameCommands(handler);
    }
}
