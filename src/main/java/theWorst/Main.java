package theWorst;


import arc.util.CommandHandler;
import arc.util.Log;
import mindustry.plugin.Plugin;
import theWorst.dataBase.Database;

public class Main extends Plugin {

    public static String milsToTime(long l) {
        return "";
    }

    public Main() {


    }


    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("dbConvert","<database> [client]", "Converts the old database to a new. Format for client: " +
                "<username>:<password>@<cluster-address>. For more information about connecting search on internet.", args -> {
            Database.Init(args[0], args.length == 2 ? args[1] : null);
            Database.loadData();
            Database.Convert();
            Log.info("Database successfully converted");
        });
    }

}
