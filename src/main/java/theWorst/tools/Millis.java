package theWorst.tools;

public class Millis {
    public static long now() {
        return System.currentTimeMillis();
    }
    public static long since(long time){
        return now() - time;
    }
}
