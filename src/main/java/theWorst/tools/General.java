package theWorst.tools;

import mindustry.entities.type.Player;
import mindustry.game.Team;
import mindustry.world.blocks.storage.CoreBlock;
import org.apache.commons.codec.digest.DigestUtils;
import theWorst.Global;

import java.lang.reflect.Field;

import static mindustry.Vars.playerGroup;
import static mindustry.Vars.state;

public class General {
    public static <T extends Enum<T>> boolean enumContains(T[] aValues,String value) {
        for(T v : aValues){
            if(v.name().equals(value)) return true;
        }
        return false;
    }


    public static CoreBlock.CoreEntity getCore(){
        if(state.teams.cores(Team.sharded).isEmpty()) return null;
        return state.teams.cores(Team.sharded).first();
    }

    public static <T> Object getPropertyByName(Class<T> tClass, String propertyName, Object object) {
        try {
            Field field = tClass.getDeclaredField(propertyName);
            return field.get(object);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            return null;
        }
    }

    /*public static <T> String[] getPropertyNameList(Class<T> tClass){
        Field[] fields = tClass.getDeclaredFields();
        String[] res = new String[fields.length];
        for(int i = 0; i < res.length; i++){
            res[i] = fields[i].getName();
        }
        return res;
    }*/

    public static long hash(String password) {
        password += Global.config.salt;
        long res = 0;
        for(int i = 0; i < password.length(); i++) {
            res = res + (long)Math.pow(password.charAt(i), 2);
        }
        return res;
    }

    public static String hash2(String password) {
        return DigestUtils.sha256Hex(password + Global.config.salt);
    }

    public static boolean isAdminOnline() {
        for(Player p : playerGroup) {
            if(p.isAdmin) return true;
        }
        return false;
    }
}
