package theWorst.helpers;

import arc.Events;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.entities.traits.BuilderTrait;
import mindustry.entities.type.BaseUnit;
import mindustry.entities.type.base.BuilderDrone;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;
import theWorst.database.PlayerD;

import java.lang.reflect.Array;
import java.util.ArrayList;

public abstract class Action {
    String by;
    Tile tile;
    int config;
    byte rotation;
    Block block;
    long age;
    static ArrayList<Break> buildQueue = new ArrayList<>();
    public static void register(){
        Events.on(EventType.BlockBuildEndEvent.class, e ->{
            for(Break b : buildQueue){
                if(e.tile.pos() != b.tile.pos()) continue;
                b.drone.kill();
                b.tile.configureAny(b.config);
            }
        });

        Events.on(EventType.BlockDestroyEvent.class, e ->{
            for(Break b : buildQueue){
                if(e.tile.pos() != b.tile.pos()) continue;
                b.drone.kill();
            }
        });
    }

    public abstract void Undo();

    public static class Break extends Action{
        BuilderDrone drone;
        @Override
        public void Undo() {
            if(tile != null && tile.block() == Blocks.air){
                drone = (BuilderDrone) UnitTypes.phantom.create(Team.sharded);
                drone.buildQueue().addFirst(new BuilderTrait.BuildRequest(tile.x, tile.y, rotation, block));
                drone.set(tile.getX(), tile.getY());
                drone.add();
                buildQueue.add(this);
            }
        }
    }

    public static class Build extends Action {
        @Override
        public void Undo() {
            if(tile != null && tile.block() == block){
                Call.onDeconstructFinish(tile, block, 0);
            }
        }
    }

    public static class Configure extends Action {
        int newConfig;
        @Override
        public void Undo() {
            if(tile != null && tile.block() == block && tile.entity != null && tile.entity.config() == newConfig){
                tile.configureAny(config);
            }
        }
    }

    public static class Rotate extends Action {
        byte newRotation;
        @Override
        public void Undo() {
            if(tile != null && tile.block() == block && tile.entity != null && tile.rotation() == newRotation){
                tile.rotation(rotation);
            }
        }
    }
}
