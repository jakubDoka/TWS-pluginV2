package theWorst;

import arc.Core;
import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import mindustry.content.Bullets;
import mindustry.content.Fx;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.plugin.Plugin;
import theWorst.database.*;
import theWorst.helpers.Administration;
import theWorst.helpers.Destroyable;
import theWorst.helpers.Hud;
import theWorst.helpers.MapManager;
import theWorst.helpers.gameChangers.Factory;
import theWorst.helpers.gameChangers.Loadout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;

import static arc.util.Log.info;
import static mindustry.Vars.*;
import static mindustry.Vars.maps;
import static theWorst.Tools.Commands.*;

public class Main extends Plugin {
    static Administration administration =  new Administration();
    static ArrayList<Destroyable> destroyable = new ArrayList<>();
    static Hud hud = new Hud();
    static InGameCommands inGameCommands = new InGameCommands();
    public Main() {
        Config.load();
        new Database();
        new MapManager();
        new Bot();
        Events.on(EventType.WorldLoadEvent.class,e-> destroyable.forEach(Destroyable::destroy));

        Events.on(EventType.Trigger.update, () -> CompletableFuture.runAsync(() -> playerGroup.all().forEach(player -> {
            PlayerD pd = Database.getData(player);
            if (!pd.pets.isEmpty()) {
                for(Pet p : pd.pets){
                    p.update(player, pd.pets);
                }
            }
        })));

        Events.on(EventType.ServerLoadEvent.class, e->{
            MapManager.cleanMaps();
        });
    }

    public void addDestroyable(Destroyable destroyable){
        Main.destroyable.add(destroyable);
    }

    //todo make event command that takes json as argument
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

        handler.removeCommand("reloadmaps");
        handler.register("reloadmaps", "Reload all maps from disk.", arg -> {
            int beforeMaps = maps.all().size;
            maps.reload();
            MapManager.cleanMaps();
            if(maps.all().size > beforeMaps){
                info("&lc{0}&ly new map(s) found and reloaded.", maps.all().size - beforeMaps);
            }else{
                info("&lyMaps reloaded.");
            }
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

        handler.register("unkick", "<ID/uuid>", "Erases kick status of player player.", args -> {
            PlayerD pd = Database.findData(args[0]);
            if (pd == null) {
                logInfo("player-not-found");
                return;
            }
            netServer.admins.getInfo(pd.uuid).lastKicked = Time.millis();
            logInfo("unkick",pd.originalName);
        });

        handler.register("mapstats","Shows all maps with statistics.",args-> Log.info(MapManager.statistics()));

        handler.register("wconfig","<target/help>", "Applies the factory configuration,settings and " +
                "loads test questions.", args -> {
            switch (args[0]){
                case "help":
                    logInfo("show-modes","ranks, pets, general, discord, discordrolerestrict, loadout, factory");
                    return;
                case "ranks":
                    Database.loadRanks();
                    return;
                case "pets":
                    Database.loadPets();
                    return;
                case "general":
                    Config.load();
                    Database.reload();
                    return;
                case "discord":
                    Bot.connect();
                    return;
                case "discordrolerestrict":
                    Bot.loadRestrictions();
                    return;
                case "loadout":
                    Loadout.loadConfig();
                    return;
                case "factory":
                    Factory.loadConfig();
                    return;
                default:
                    logInfo("invalid-mode");
            }
        });

        handler.register("wload","<target/help>", "Applies the factory configuration,settings and " +
                "loads test questions.", args -> {
            switch (args[0]){
                case "help":
                    logInfo("show-modes","subnet,loadout,factory");
                    return;
                case "factory":
                    InGameCommands.factory.loadUnits();
                    return;
                case "subnet":
                    Database.loadSubnet();
                    return;
                case "loadout":
                    InGameCommands.loadout.loadRes();
                    return;
                default:
                    logInfo("invalid-mode");
            }
        });

        handler.register("worsthelp","<target/help>", "Applies the factory configuration,settings and " +
                "loads test questions.", args -> {
            HashSet<String> banned = new HashSet<String>(){{
                add("dynamicExplosion");
                add("dropItem");
                add("block");
                add("shieldBreak");
                add("incendTrail");
                add("missileTrail");
            }};
            StringBuilder sb;
            StringBuilder sb2;
            switch (args[0]){
                case "help":
                    logInfo("show-modes", "ranks,pets");
                    return;
                case "ranks":
                    sb = new StringBuilder();
                    for(Perm s : Perm.values()){
                        if(s.description == null) continue;
                        sb.append(s.name()).append(" - ").append(s.description).append("\n");
                    }
                    sb2 = new StringBuilder();
                    for(Stat s : Stat.values()){
                        sb2.append(s.name()).append(" ");
                    }
                    logInfo("worsthelp-ranks",sb.toString(),sb2.toString());
                    return;
                case "pets":
                    sb = new StringBuilder();
                    for(String s : getPropertyNameList(Fx.class)){
                        if(banned.contains(s)) continue;
                        sb.append(s).append(" ");
                    }
                    sb2 = new StringBuilder();
                    for(String s : getPropertyNameList(Bullets.class)){
                        sb2.append(s).append(" ");
                    }
                    logInfo("worsthelp-pets",sb.toString(),sb2.toString());
                    return;
                default:
                    logInfo("invalid-mode");
            }
        });


        handler.register("setrank", "<uuid/name/id> <rank/restart> [reason...]",
                "Sets rank of the player.", args -> {
            switch (setRankViaCommand(null,args[0],args[1],args.length==3 ? args[2] : null)){
                case notFound:
                    logInfo("player-not-found");
                    break;
                case invalid:
                    logInfo("rank-not-found");
                    logInfo("rank-s",Arrays.toString(Rank.values()));
                    logInfo("rank-s-custom",Database.ranks.keySet().toString());
            }
        });

        handler.register("emergency","<time/permanent/stop>","Emergency control.",args->{
            switch (setEmergencyViaCommand(args)) {
                case success:
                    logInfo("emergency-started");
                    break;
                case stopSuccess:
                    logInfo("emergency-stopped");
                    break;
                case invalid:
                    logInfo("emergency-ongoing");
                    break;
                case invalidStop:
                    logInfo("emergency-cannot-stop");
                    break;
                case invalidNotInteger:
                    logInfo("refuse-not-integer","1");
                    break;
                case permanentSuccess:
                    logInfo("emergency-permanent-started");
            }
        });

        handler.removeCommand("exit");
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
        inGameCommands.register(handler);
    }
}
