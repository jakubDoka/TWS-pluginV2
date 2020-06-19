package theWorst.helpers;

import arc.math.Mathf;
import mindustry.world.Block;
import mindustry.world.Tile;

import java.util.ArrayList;

import static mindustry.Vars.world;
import static mindustry.Vars.content;

public class Builder {
    public static Block getBlockByName(String name){
        for(Block b : content.blocks()){
            if(b.isFloor() || (b.solid && !b.breakable)) continue;
            if(b.name.equals(name)){
                return b;
            }
        }
        return null;
    }

    public static void dropMeteor(Block block, int x, int y, int size){
        if(size == -1) size = Mathf.random(10,30);
        ArrayList<Tile> possible = new ArrayList<Tile>(){{ add(world.tile(x,y)); }};
        while(size > 0) {
            for(Tile t : new ArrayList<>(possible)){
                possible.remove(t);
                if(t.overlay().id == block.id || size == 0) continue;
                t.setOverlay(block);
                if(t.block().solid) t.removeNet();
                size--;
                possible.add(world.tile(t.x, t.y-1));
                possible.add(world.tile(t.x, t.y+1));
                possible.add(world.tile(t.x-1, t.y));
                possible.add(world.tile(t.x+1, t.y));
            }
        }
    }
}
