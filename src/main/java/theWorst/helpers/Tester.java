package theWorst.helpers;

import mindustry.entities.type.Player;
import org.json.simple.JSONArray;
import theWorst.Config;
import theWorst.database.Database;
import theWorst.database.PlayerD;
import theWorst.database.Rank;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import static theWorst.Tools.Json.loadJson;
import static theWorst.Tools.Json.saveJson;
import static theWorst.Tools.Players.sendErrMessage;
import static theWorst.Tools.Players.sendMessage;

public class Tester {
    private static final String testFile = Config.configDir+"test.json";
    public static HashMap<String,Test> tests = new HashMap<>();
    public static Administration.RecentMap recent = new Administration.RecentMap(15*60,"test-can-egan");

    public static HashMap<String, ArrayList<String>> loadQuestions(Locale loc){
        HashMap<String, ArrayList<String>> questions = new HashMap<>();
        String bundle = testFile + "_" + loc.getLanguage() + "_" + loc.getCountry();
        File fi = new File(bundle);
        if(!fi.exists() || fi.isDirectory()) bundle = testFile;
        loadJson(bundle,(test)->{
            for(Object o:test.keySet()){
                JSONArray options=(JSONArray) test.get(o);
                ArrayList<String> opt=new ArrayList<>();
                for(Object op:options){
                    opt.add((String)op);
                }
                questions.put((String)o,opt);
            }
        },Tester::createExample);
        return questions;
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
                    "}\n");
    }

    public static class Test {
        String question;
        ArrayList<String> options;
        int progress;
        int points;
        HashMap<String, ArrayList<String>> questions;

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
            for(int i = 0; i<options.size(); i++){
                sb.append("[yellow]").append(i+1).append(")[gray]");
                sb.append(options.get(i).replace("#",""));
                sb.append("\n");
            }
            player.sendMessage(sb.toString());
        }

        public void processAnswer(Player player,int answer){
            if(answer>=options.size() || answer<0){
                sendErrMessage(player,"test-invalid-answer","" + (answer + 1),"" + options.size());
                return;
            }
            if(options.get(answer).startsWith("#")){
                points+=1;
            }
            progress++;
            ask(player);
        }
    }


}
