package theWorst.helpers.gameChangers;

import arc.graphics.Color;
import arc.math.geom.Vec2;

import arc.util.Time;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import mindustry.content.Fx;
import mindustry.entities.Effects;
import mindustry.entities.Predict;
import mindustry.entities.Units;
import mindustry.entities.traits.TargetTrait;
import mindustry.entities.type.Player;
import mindustry.gen.Call;

import java.io.IOException;
import java.util.ArrayList;

import static theWorst.tools.General.getPropertyByName;

public class Pet {
    boolean loaded = true;
    public float acceleration = .3f, maxSpeed = 7, attraction = 1f;
    public String trailName = "fire";
    public String name = "fire-pet";
    float time = 0;

    Vec2 pos = new Vec2(), vel = new Vec2();
    @JsonIgnore public Effects.Effect trail = Fx.fire;
    TargetTrait target = null;
    Weapon weapon = null;

    public Pet(){}

    @JsonCreator public Pet(
            @JsonProperty("acceleration") float acceleration,
            @JsonProperty("maxSpeed") float maxSpeed,
            @JsonProperty("attraction") float attraction,
            @JsonProperty("trailName") String  trailName,
            @JsonProperty("name") String  name,
            @JsonProperty("weapon") String weapon) throws IOException {
        this.acceleration = acceleration;
        this.maxSpeed = maxSpeed;
        this.attraction = attraction;
        this.name = name;
        if(name == null){
            this.name = "noName";
        }
        if(trailName != null){
            trail = (Effects.Effect) getPropertyByName(Fx.class,trailName,null);
        }
        this.weapon = ShootingBooster.weapons.get(weapon);
    }

    public String getWeapon() {
        return "copperGun";
    }

    public Pet(Pet other){
        this.name = other.name;
        this.acceleration = other.acceleration;
        this.trail = other.trail;
        this.attraction = other.attraction;
        this.weapon = other.weapon;
    }


    public void update(Player player, ArrayList<Pet> others) {
        if (player.isDead()) return;
        pos.add(new Vec2(vel).scl(Time.delta()));
        vel.add(new Vec2(player.getX(),player.getY()).sub(pos).nor().scl(acceleration));
        for(Pet p : others){
            vel.add(new Vec2(p.pos).sub(pos).nor().scl(acceleration*attraction));
        }
        vel.clamp(0,maxSpeed);
        Call.onEffectReliable(trail, pos.x, pos.y, vel.angle() + 180, Color.white);
        if(weapon == null) return;
        float range = weapon.getRange();
        if(Units.invalidateTarget(target,player.getTeam(), pos.x, pos.y, range)){
            target = Units.closestEnemy(player.getTeam(), pos.x, pos.y, range, u -> !u.isDead());
        }
        if (loaded){
            loaded = false;
            if( player.isShooting()){
                weapon.shoot(player, pos, player.pointerX, player.pointerY);
            } else if(target != null){
                Vec2 shootTo = Predict.intercept(pos.x, pos.y,target.getX(), target.getY(),
                        target.getTargetVelocityX(), target.getTargetVelocityY(), weapon.bullet.speed * weapon.velMul);
                weapon.shoot(player, pos, shootTo.x, shootTo.y);
            }
        } else {
            time += Time.delta();
            if(time > weapon.fireRate){
                loaded = true;
                time = 0;
            }
        }
    }


}
