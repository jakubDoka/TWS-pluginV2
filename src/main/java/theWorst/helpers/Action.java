package theWorst.helpers;

import arc.Events;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.entities.traits.BuilderTrait;
import mindustry.entities.type.base.BuilderDrone;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.BuildBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public abstract class Action {
    static HashMap<Integer, Action> buildBreaks = new HashMap<>();
    static HashSet<String> ignored = new HashSet<String>(){{
        add("build1");
        add("build2");
        add("build3");
        add("build4");
    }};
    String by;
    Tile tile;
    int config;
    byte rotation;
    Block block;
    long age;
    static ArrayList<Break> buildQueue = new ArrayList<>();

    static void addBuildBreak(ArrayList<Action> acts, Action act){
        if(ignored.contains(act.block.name)) return;
        int pos = act.tile.pos();
        Action old = buildBreaks.get(pos);
        if(old == null) {
            buildBreaks.put(pos, act);
            acts.add(0, act);
            return;
        }
        if(old.by.equals(act.by) && old.block == act.block) return;
        old.tile = null;
        buildBreaks.put(pos, act);
        acts.add(0, act);
    }

    public static void register(){
        Events.on(EventType.BlockBuildEndEvent.class, e ->{
            int pos = e.tile.pos();
            for(Break b : buildQueue){
                if (b == null) {
                    buildQueue.remove(null);
                    continue;
                }
                if (b.tile == null) {
                    buildQueue.remove(b);
                    continue;
                }
                if(pos != b.tile.pos()) continue;
                b.drone.kill();
                b.tile.configureAny(b.config);
            }
            if(e.player == null) return;
            Action old = buildBreaks.get(pos);
            if(old == null) return;
            buildBreaks.remove(pos);

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
            if(tile == null) return;
            boolean canFinish = false;
            if(tile.entity instanceof BuildBlock.BuildEntity){
                float progress = ((BuildBlock.BuildEntity) tile.entity).progress;
                canFinish = progress > 0f && progress < 1f;
            }
            if(tile.block() == Blocks.air || canFinish){
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
