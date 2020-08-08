package theWorst.helpers;

import mindustry.entities.type.Player;
import mindustry.gen.Call;
import theWorst.Global;
import theWorst.database.Database;
import theWorst.database.PD;
import theWorst.database.Ranks;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;

import static theWorst.tools.Json.loadSimpleHashmap;
import static theWorst.tools.Json.saveJson;
import static theWorst.tools.Players.sendErrMessage;
import static theWorst.tools.Players.sendMessage;
import static theWorst.database.Database.getData;

public class Tester {
    private static final String testFile = Global.configDir + "test.json";
    public static HashMap<String,Test> tests = new HashMap<>();
    public static Administration.RecentMap recent = new Administration.RecentMap("test-can-egan"){
        @Override
        public long getPenalty() {
            return Global.limits.testPenalty;
        }
    };

    public static HashMap<String, String[]> loadQuestions(String locStr){
        String bundle = testFile + "_" + locStr;
        Call.sendMessage(bundle);
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
            PD pd = getData(player);
            questions = loadQuestions(pd.locString);
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
                    sendMessage(player,"test-success", Ranks.verified.getSuffix());
                    Database.setRank(getData(player).id, Ranks.verified);
                } else {
                    sendErrMessage(player,"test-fail","" + points,"" + size);
                    recent.add(player.uuid);
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
