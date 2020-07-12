package theWorst.helpers;

import mindustry.entities.type.Player;
import org.json.simple.JSONArray;
import theWorst.Global;
import theWorst.database.Database;
import theWorst.database.PlayerD;
import theWorst.database.Rank;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import static theWorst.Tools.Json.*;
import static theWorst.Tools.Players.sendErrMessage;
import static theWorst.Tools.Players.sendMessage;

public class Tester {
    private static final String testFile = Global.configDir + "test.json";
    public static HashMap<String,Test> tests = new HashMap<>();
    public static Administration.RecentMap recent = new Administration.RecentMap(15*60,"test-can-egan");

    public static HashMap<String, String[]> loadQuestions(Locale loc){
        String bundle = testFile + "_" + loc.getLanguage() + "_" + loc.getCountry();
        File fi = new File(bundle);
        if(!fi.exists() || fi.isDirectory()) bundle = testFile;
        return loadSimpleHashmap(bundle, String[].class, Tester::createExample);
    }

    private static void createExample() {
            saveJson(testFile,"{\n" +
                    "\t\"some question?\":[\n" +
                    "\t\t\"option\",\n" +
                    "\t\t\"other option\",\n" +
                    "\t\t\"#right option starts with hashtag\"\n" +
                    "\t],\n" +
                    "\t\"some other question?\":[\n" +
                    "\t\t\"option\",\n" +
                    "\t\t\"other option\",\n" +
                    "\t\t\"#right option\"\n" +
                    "\t]\n" +
                    "}\n", "tests");
    }

    public static class Test {
        String question;
        String[] options;
        int progress;
        int points;
        HashMap<String, String[]> questions;

        public Test(Player player){
            PlayerD pd = Database.getData(player);
            questions = loadQuestions(pd.bundle.getLocale());
            if(questions.isEmpty()){
                sendErrMessage(player, "test-no-questions");
                tests.remove(player.uuid);
                return;
            }
            ask(player);
        }

        public void ask(Player player){
            int size = questions.size();
            if(progress >= size){
                if(points==size){
                    sendMessage(player,"test-success",Rank.verified.getName());
                    Database.setRank(Database.getData(player), Rank.verified, player);
                } else {
                    sendErrMessage(player,"test-fail","" + points,"" + size);
                    recent.add(player);
                }
                tests.remove(player.uuid);
                return;
            }
            StringBuilder sb = new StringBuilder();
            question = (String) questions.keySet().toArray()[progress];
            sb.append(question).append("\n");
            options = questions.get(question);
            for(int i = 0; i<options.length; i++){
                sb.append("[yellow]").append(i+1).append(")[gray]");
                sb.append(options[i].replace("#",""));
                sb.append("\n");
            }
            player.sendMessage(sb.toString());
        }

        public void processAnswer(Player player,int answer){
            if(answer>=options.length || answer<0){
                sendErrMessage(player,"test-invalid-answer","" + (answer + 1),"" + options.length);
                return;
            }
            if(options[answer].startsWith("#")){
                points+=1;
            }
            progress++;
            ask(player);
        }
    }


}
