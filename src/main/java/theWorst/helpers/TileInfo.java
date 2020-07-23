package theWorst.helpers;

import mindustry.world.Block;
import theWorst.database.PD;
import theWorst.database.Perm;

import java.util.ArrayList;
import java.util.HashMap;

public class TileInfo{
    // deconstructedBy ambiguously holds possibly either someone who attempted to deconstruct
    // the current block or the person who deconstructed the previous block
    final int historySize = 4;
    HashMap<String , ArrayList<PD>> data = new HashMap<>();
    int lock = Perm.normal.value;
    void add(String act, PD pd) {
        ArrayList<PD> arr = data.computeIfAbsent(act, k -> new ArrayList<>());
        if(arr.size() > 0 && arr.get(0).player.uuid.equals(pd.player.uuid)) {
            return;
        }
        arr.add(0 ,pd);
        if(arr.size() > historySize) {
            arr.remove(historySize);
        }
    }
}
