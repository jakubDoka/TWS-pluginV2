package theWorst.helpers;

import arc.math.Mathf;
import mindustry.entities.type.Player;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.world.Block;
import mindustry.world.Tile;

import java.util.ArrayList;

import static mindustry.Vars.world;
import static mindustry.Vars.content;

public class Terraformer {

    public static Block getBlockByName(String name, boolean filter){
        for(Block b : content.blocks()){
            if(filter && (b.isFloor() || b.isOverlay() || (b.solid && !b.breakable))) continue;
            if(b.name.equals(name)){
                return b;
            }
        }
        return null;
    }

    public static boolean buildBlock(Team team, Block block, Tile tile){
        Call.onConstructFinish(tile, block, 0, (byte)0, team, true);
        return tile.block() == block;
    }

    public static void dropMeteor(Block block, Tile tile, int size){
        if(size == -1) size = Mathf.random(10,30);
        ArrayList<Tile> possible = new ArrayList<Tile>(){{ add(tile); }};
        while(size > 0) {
            for(Tile t : new ArrayList<>(possible)){
                possible.remove(t);

                if(block != null && size > 0){
                    t.setOverlay(block);
                }
                if(t.block().solid) t.removeNet();
                if(size <= 0) return;
                size--;
                possible.add(world.tile(t.x, t.y-1));
                possible.add(world.tile(t.x, t.y+1));
                possible.add(world.tile(t.x-1, t.y));
                possible.add(world.tile(t.x+1, t.y));
            }
        }
    }
}
