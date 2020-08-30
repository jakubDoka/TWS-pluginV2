package theWorst.tools;

import arc.math.Mathf;
import mindustry.entities.type.Player;

import java.util.ArrayList;

import static java.lang.Math.min;
import static theWorst.database.Database.getData;

public class Formatting {
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
        name = cleanColors(name);
        name = cleanEmotes(name);
        return name.replace(" ","_");
    }

    public static String format(String string, String ... args) {
        int idx = 0;
        int completed = 0;
        while (string.contains("{") && idx < args.length) {
            int first = string.indexOf("{", completed), last = string.indexOf("}", completed);
            if (first == -1 || last == -1) break;
            completed = last + 1;
            string = string.substring(0, first) + args[idx] + string.substring(completed);
            completed += args[idx].length();
            idx++;
        }
        return string;
    }

    public static String smoothColors(String message,String ... colors){
        Color[] resolved = new Color[colors.length];
        for(int i = 0; i<colors.length; i++){
            resolved[i] = new Color(colors[i]);
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
        while(total<length){
            total ++;
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

    public static String formPage(ArrayList<String > data, int page, String title, int pageSize) {
        StringBuilder b = new StringBuilder();
        int pageCount = (int) Math.ceil(data.size() / (float) pageSize);
        page = Mathf.clamp(page, 1, pageCount) - 1;
        int start = page * pageSize;
        int end = min(data.size(), (page + 1) * pageSize);
        b.append("[orange]--").append(title.toUpperCase()).append("(").append(page + 1).append("/");
        b.append(pageCount).append(")--[]\n\n");
        for (int i = start; i < end; i++) {
            b.append(data.get(i)).append("\n");
        }
        return b.toString();
    }

    public static boolean hasNoDigit(String arg) {
        for(int i = 0; i < arg.length(); i++){
            if(Character.isDigit(arg.charAt(i))) return false;
        }
        return true;
    }

    public enum MessageMode {
        normal("[[","]", "coral"),
        admin("||", "||", "blue"),
        direct("<", ">", "#ffdfba"),
        remote(">>", ">>","yellow");

        String start, end, color;
        MessageMode(String start, String end, String color) {
            this.start = start;
            this.end = end;
            this.color = color;
        }
    }
    public static String formatMessage(Player player, MessageMode mode) {
        return String.format("[%s]%s[#%s]%s[]%s:[] ", mode.color, mode.start, player.color, getData(player).name,mode.end);
    }

    public static class Color {
        public int r,g,b;
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

    public static String getSubnet(String address){
        return address.substring(0,address.lastIndexOf("."));
    }
}
