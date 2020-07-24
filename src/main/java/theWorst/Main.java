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

    public static String clean(String string, String  begin, String  end){
        int fromBegin = 0,fromEnd = 0;
        while (string.contains(begin)){
            int first=string.indexOf(begin,fromBegin),last=string.indexOf(end,fromEnd);
            if(first==-1 || last==-1) break;
            if(first>last){
                fromBegin=first+1;
                fromEnd=last+1;
            }
            string=string.substring(0,first)+string.substring(last+1);
        }
        return string;
    }

    public static String cleanEmotes(String string){
        return clean(string,"<",">");
    }

    public static String cleanColors(String string){
        return clean(string,"[","]");
    }

    public static String cleanName(String name){
        name=cleanColors(name);
        name=cleanEmotes(name);
        return name.replace(" ","_");
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
