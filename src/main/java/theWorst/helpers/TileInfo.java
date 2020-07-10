package theWorst.helpers;

import arc.struct.ObjectSet;
import mindustry.entities.type.Player;
import mindustry.world.Block;
import theWorst.database.Perm;
import theWorst.database.PlayerD;

import java.util.ArrayList;
import java.util.HashMap;

public class TileInfo{
    // deconstructedBy ambiguously holds possibly either someone who attempted to deconstruct
    // the current block or the person who deconstructed the previous block
    public Block previousBlock;
    public Block currentBlock;
    public int previousRotation;
    public int previousConfig;


    final int historySize = 4;
    HashMap<String , ArrayList<PlayerD>> data = new HashMap<>();
    int lock= Perm.normal.getValue();
    void add(String act, PlayerD pd) {
        ArrayList<PlayerD> arr = data.computeIfAbsent(act, k -> new ArrayList<>());
        if(arr.size() > 0 && arr.get(0).uuid.equals(pd.uuid)) {
            return;
        }
        arr.add(0 ,pd);
        if(arr.size() > historySize) {
            arr.remove(historySize);
        }
    }
}
