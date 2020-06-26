package theWorst.Tools;

import mindustry.game.Team;
import mindustry.world.blocks.storage.CoreBlock;
import theWorst.database.PlayerD;
import theWorst.database.Rank;

import static mindustry.Vars.state;

public class General {
    public static <T extends Enum<T>> boolean enumContains(T[] aValues,String value) {
        for(T v : aValues){
            if(v.name().equals(value)) return true;
        }
        return false;
    }

    public static Rank getRank(PlayerD pd){
        return Rank.valueOf(pd.rank);
    }

    public static CoreBlock.CoreEntity getCore(){
        if(state.teams.cores(Team.sharded).isEmpty()) return null;
        return state.teams.cores(Team.sharded).first();
    }
}
