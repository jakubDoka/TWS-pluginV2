package theWorst;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.Array;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Tmp;
import mindustry.content.Blocks;
import mindustry.entities.type.Player;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.maps.Map;
import mindustry.net.Administration;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.Floor;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;
import theWorst.database.*;
import theWorst.discord.ColorMap;
import theWorst.discord.CommandContext;
import theWorst.discord.MapParser;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;

import static java.lang.Math.min;
import static mindustry.Vars.*;
import static mindustry.Vars.world;

public class Tools {
    public static final String bundlePath = "bundles.bundle";
    private static final String prefix = "[coral][[[scarlet]Server[]]:[#cbcbcb]";
    public static final Locale locale = new Locale(System.getProperty("user.language"),System.getProperty("user.country"));
    public static final ResourceBundle defaultBundle = ResourceBundle.getBundle(bundlePath,new Locale("en","US"));
    public static final PlayerD locPlayer = new PlayerD(){{bundle=ResourceBundle.getBundle(bundlePath,locale);}};
    private static final ColorMap colorMap = new ColorMap();
    private static final MapParser mapParser = new MapParser();

    //geo data
    public static JSONObject getLocData(String ip){
        try {
            String json = Jsoup.connect("http://ipapi.co/"+ip+"/json").ignoreContentType(true).timeout(3000).execute().body();
            return (JSONObject) new JSONParser().parse(json);
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Locale getLocale(String ip,JSONObject data){
        if(data == null) data = getLocData(ip);
        if(data==null) return locale;
        String languages = (String) data.get("languages");
        if(languages==null) return locale;
        String[] resolvedL = languages.split(",");
        if(resolvedL.length==0) return locale;
        String[] resResolvedL = resolvedL[0].split("-");
        if(resResolvedL.length==0) return locale;
        return new Locale(resResolvedL[0],resResolvedL[1]);
    }



    //player related
    public static String getPlayerList(){
        StringBuilder builder = new StringBuilder();
        builder.append("[orange]Players: \n");
        for(Player p : playerGroup.all()){
            if(p.isAdmin || p.con == null || Database.hasPerm(p, Perm.higher)) continue;
            builder.append("[lightgray]").append(p.name);
            builder.append("[accent] (ID:").append(Database.getData(p).serverId).append(")\n");
        }
        return builder.toString();
    }

    public static Player findPlayer(String arg) {
        if(Strings.canParseInt(arg)){
            int id = Strings.parseInt(arg);
            return  playerGroup.find(p -> Database.getData(p).serverId == id);
        }
        for(Player p:playerGroup){
            String pName=Tools.cleanName(p.name);
            if(pName.equalsIgnoreCase(arg)){
                return p;
            }
        }
        return null;
    }

    public static void sendMessage(Player player,String key,String ... args){
        PlayerD pd = Database.getData(player);
        player.sendMessage(prefix + format(getTranslation(pd,key),args));
    }

    public static void sendErrMessage(Player player,String key,String ... args){
        PlayerD pd = Database.getData(player);
        player.sendMessage(prefix + "[#bc5757]" + format(getTranslation(pd,key),args));
    }

    public static void sendInfoPopup(Player player, String kay, String ... args) {
        PlayerD pd = Database.getData(player);
        Call.onInfoMessage(player.con,format(getTranslation(pd,kay),args));
    }

    public static void kick(Player player, String kay, String ... args ){
        PlayerD pd = Database.getData(player);
        player.con.kick(format(getTranslation(pd,kay),args));
    }


    public static String getTranslation(PlayerD pd,String key){
        if(pd.bundle != null && pd.bundle.containsKey(key) && pd.settings.contains(Setting.translate.name())){
            return pd.bundle.getString(key);
        }
        if(!defaultBundle.containsKey(key)) return "error: bundle " + key + "is missing. Please report it.";
        return defaultBundle.getString(key);
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
            if(!Database.hasEnabled(p,Setting.chat) || Database.getData(p).mutes.contains(sender.uuid)) continue;
            p.sendMessage("[coral][[[#"+sender.color+"]"+sender.name+"[]]:[]"+message);
        }
    }

    public static boolean isGriefer(Player player){
        return Database.getData(player).rank.equals(Rank.griefer.name());
    }

    //string formatting
    public static String milsToTime(long mils){
        long sec = mils / 1000;
        long min = sec / 60;
        long hour = min / 60;
        long days = hour / 24;
        return String.format("%d:%02d:%02d:%02d", days%365 ,hour%24 ,min%60 ,sec%60 );
    }

    public static String secToTime(int sec){
        return String.format("%02d:%02d",sec/60,sec%60);
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
        name=name.replace(" ","_");
        return name;
    }

    public static String format(String string, String ... args){
        int idx = 0;
        while (string.contains("{") && idx<args.length){
            int first=string.indexOf("{"),last=string.indexOf("}");
            if(first==-1 || last==-1) break;
            string=string.substring(0,first)+args[idx]+string.substring(last+1);
            idx++;
        }
        return string;
    }

    public static String smoothColors(String message,String ... colors){
        Color[] resolved = new Color[colors.length];
        for(int i = 0; i<colors.length; i++){
            resolved[i]=new Color(colors[i]);
        }
        int interpolations = resolved.length-1;
        int[] stepAmount = new int[interpolations];
        int length = message.length();
        int total = 0;
        for(int i = 0; i<interpolations;i++){
            int amount = length/interpolations;
            stepAmount[i] = amount;
            total+=amount;
        }
        if(total<length){
            stepAmount[0]+=1;
        }
        StringBuilder res = new StringBuilder();
        int idx = 0;
        for(int i = 0; i<resolved.length-1; i++){
            Color f = resolved[i];
            Color s = resolved[i+1];
            int steps = stepAmount[i];
            if (steps == 0) break;
            int rd = (s.r-f.r)/steps;
            int gd = (s.g-f.g)/steps;
            int bd = (s.b-f.b)/steps;
            for(int j = 0; j<steps;j++){
                res.append("[#").append(new Color(f.r + rd * j, f.g + gd * j, f.b + bd * j)).append("]");
                res.append(message.charAt(idx));
                idx++;
            }
        }
        return res.toString();
    }

    public static String formPage(Array<String > data,int page,String title,int pageSize) {
        StringBuilder b = new StringBuilder();
        int pageCount = (int) Math.ceil(data.size / (float) pageSize);
        page = Mathf.clamp(page, 1, pageCount) - 1;
        int start = page * pageSize;
        int end = min(data.size, (page + 1) * pageSize);
        b.append("[orange]--").append(title.toUpperCase()).append("(").append(page + 1).append("/");
        b.append(pageCount).append(")--[]\n\n");
        for (int i = start; i < end; i++) {
            b.append(data.get(i)).append("\n");
        }
        return b.toString();
    }

    //json file interaction
    public interface RunLoad{
        void run(JSONObject data);
    }

    public static void loadJson(String filename, RunLoad load, Runnable save){
        try (FileReader fileReader = new FileReader(filename)) {
            JSONParser jsonParser = new JSONParser();
            Object obj = jsonParser.parse(fileReader);
            JSONObject saveData = (JSONObject) obj;
            load.run(saveData);
            fileReader.close();
            Log.info("Data from "+filename+" loaded.");
        } catch (FileNotFoundException ex) {
            Log.info("No "+filename+" found.Default one wos created.");
            save.run();
        } catch (ParseException ex) {
            Log.info("Json file "+filename+" is invalid.");
        } catch (IOException ex) {
            Log.info("Error when loading data from " + filename + ".");
        }
    }

    public static void saveJson(String filename, String save){
        //creates full path
        StringBuilder path = new StringBuilder();
        String[] dirs = filename.split("/");
        for(int i = 0; i<dirs.length-1; i++){
            path.append(dirs[i]).append("/");
            new File(path.toString()).mkdir();
        }
        //path exists so save
        try (FileWriter file = new FileWriter(filename)) {
            file.write(save);
        } catch (IOException ex) {
            Log.info("Error when creating/updating "+filename+".");
            ex.printStackTrace();
        }
    }

    //map related
    public static int colorFor(Floor floor, Block wall, Block ore, Team team){
        if(wall.synthetic()){
            return team.color.rgba();
        }
        Integer wallCol = colorMap.get(wall.name);
        Integer floorCol = colorMap.get(floor.name);
        return wall.solid ? wallCol==null ? 0:wallCol : ore == Blocks.air ? floorCol==null ? 0:floorCol : ore.color.rgba();
    }

    public static EmbedBuilder formMapEmbed(Map map, String reason, CommandContext ctx) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("**"+reason.toUpperCase()+"** "+map.name())
                .setAuthor(map.author())
                .setDescription(map.description()+"\n**Posted by "+ctx.author.getName()+"**");
        try{
            InputStream in = new FileInputStream(map.file.file());
            BufferedImage img = mapParser.parseMap(in).image;
            eb.setImage(img);
        } catch (IOException ex){
            ctx.reply("I em unable to post map with image.");
        }

        return eb;
    }

    public static boolean hasMapAttached(Message message){
        return message.getAttachments().size() == 1 && message.getAttachments().get(0).getFileName().endsWith(".msav");
    }

    public static BufferedImage getMiniMapImg() {
        BufferedImage img = new BufferedImage(world.width(), world.height(),BufferedImage.TYPE_INT_ARGB);
        for(int x = 0; x < img.getWidth(); x++){
            for(int y = 0; y < img.getHeight(); y++){
                Tile tile = world.tile(x,y);
                int color = colorFor(tile.floor(), tile.block(), tile.overlay(), tile.getTeam());
                img.setRGB(x, img.getHeight() - 1 - y, Tmp.c1.set(color).argb8888());
            }
        }
        return img;
    }

    public static Map findMap(String object) {
        Array<Map> mapList = maps.all();
        if (!Strings.canParsePostiveInt(object)) {
            return maps.all().find(m -> m.name().equalsIgnoreCase(object.replace('_', ' '))
                    || m.name().equalsIgnoreCase(object));
        }
        int idx = Integer.parseInt(object);
        if (idx < mapList.size) {
            return maps.all().get(idx);
        }
        return null;
    }

    //command tools
    public static boolean wrongArgAmount(Player player, String[] args, int req){
        if(args.length!=req){
            if(player != null){
                sendErrMessage(player,"args-wrong-amount",String.valueOf(args.length),String.valueOf(req));
            } else {
                logInfo("args-wrong-amount",String.valueOf(args.length),String.valueOf(req));
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
            if (Tools.getRank(pd).isAdmin) return Res.notPermitted;
        }
        if(enumContains(Rank.values(),rank)) {
            Rank r = Rank.valueOf(rank);
            //this means that this function is called from InGameCommands or BotCommands
            if (player != null && r.isAdmin) return Res.notPermitted;
            if (reason == null) reason = "Reason not provided.";
            Rank prevRank = Tools.getRank(pd);
            Database.setRank(pd, r, null);
            Bot.onRankChange(pd.originalName, pd.serverId, prevRank.name(), r.name(), by, reason);
            logInfo("rank-change", pd.originalName, pd.rank);
            sendMessage("rank-change", pd.originalName, Tools.getRank(pd).getName());
        } else {
            if (rank.equals("restart")) {
                pd.specialRank = null;
                sendMessage("rank-restart", pd.originalName);
                Log.info("rank-restart", pd.originalName);
            }
            else if (!Database.ranks.containsKey(rank)) return Res.invalidRank;
            else {
                pd.specialRank = rank;
                SpecialRank sr = Database.getSpecialRank(pd);
                if (sr != null) {
                    logInfo("rank-change", pd.originalName, pd.specialRank);
                    sendMessage("rank-change", pd.originalName, sr.getSuffix());
                }
            }
        }
        return Res.success;
    }

    public enum Res{
        notFound,
        notPermitted,
        invalidRank,
        success
    }

    //enum tools
    public static <T extends Enum<T>> boolean enumContains(T[] aValues,String value) {
        for(T v : aValues){
            if(v.name().equals(value)) return true;
        }
        return false;
    }

    public static Rank getRank(PlayerD pd){
        return Rank.valueOf(pd.rank);
    }

    public static class Color {
        int r,g,b;
        public Color(int r,int g,int b){
            this.r=r;
            this.g=g;
            this.b=b;
        }
        public Color(String hex){
            r = Integer.parseInt(hex.substring(0,2),16);
            g = Integer.parseInt(hex.substring(2,4),16);
            b = Integer.parseInt(hex.substring(4,6),16);
        }
        @Override
        public String toString() {
            String[] parts = new String[]{Integer.toHexString(r), Integer.toHexString(g), Integer.toHexString(b)};
            for(int i = 0; i<parts.length; i++){
                if(parts[i].length()==1){
                    parts[i] = "0"+parts[i];
                }
            }

            return parts[0] + parts[1] + parts[2];
        }
    }
}
