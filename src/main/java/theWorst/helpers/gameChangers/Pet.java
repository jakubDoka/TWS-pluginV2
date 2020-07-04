package theWorst.helpers.gameChangers;

import arc.graphics.Color;
import arc.math.geom.Vec2;
import arc.util.Time;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import mindustry.content.Fx;
import mindustry.entities.Effects;
import mindustry.entities.Predict;
import mindustry.entities.Units;
import mindustry.entities.traits.TargetTrait;
import mindustry.entities.type.Player;
import mindustry.gen.Call;

import java.util.ArrayList;

import static theWorst.Tools.Commands.logInfo;
import static theWorst.Tools.General.getPropertyByName;

public class Pet {
    boolean loaded = true;
    public float acceleration = .3f, maxSpeed = 7, attraction = 1f;
    public String trailName = "fire";
    public String name = "fire-pet";
    float time = 0;

    Vec2 pos = new Vec2(), vel = new Vec2();
    Color color = Color.white;
    Effects.Effect trail = Fx.fire;
    TargetTrait target = null;
    Weapon weapon = null;

    public Pet(){}

    @JsonCreator public Pet(
            @JsonProperty("acceleration") float acceleration,
            @JsonProperty("maxSpeed") float maxSpeed,
            @JsonProperty("attraction") float attraction,
            @JsonProperty("trailName") String  trailName,
            @JsonProperty("color") String  color,
            @JsonProperty("name") String  name,
            @JsonProperty("weapon") String weapon){
        this.acceleration = acceleration;
        this.maxSpeed = maxSpeed;
        this.attraction = attraction;
        this.name = name;
        if(name == null){
            this.name = "noName";
            logInfo("missing-name","pet");
        }
        if(trailName != null){
            Effects.Effect resolved = (Effects.Effect) getPropertyByName(Fx.class,trailName,null);
            if(resolved != null){
                trail = resolved;
            } else {
                logInfo("pet-invalid-trail", name, trailName);
            }
        }
        this.color = Color.valueOf(color);
        this.weapon = ShootingBooster.weapons.get(weapon);
    }

    @JsonGetter public String getColor() {
        return color.toString();
    }

    public Pet(Pet other){
        this.name = other.name;
        this.acceleration = other.acceleration;
        this.trail = other.trail;
        this.color = other.color;
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
        Call.onEffectReliable(trail, pos.x, pos.y, vel.angle() + 180, color);
        if(weapon == null) return;
        float range = weapon.getRange();
        if(Units.invalidateTarget(target,player.getTeam(), pos.x, pos.y, range)){
            target = Units.closestEnemy(player.getTeam(), pos.x, pos.y, range, u -> !u.isDead());
        }
        if (loaded){
            loaded = false;
            if( player.isShooting()){
                weapon.shoot(player,pos.angleTo(player.pointerX, player.pointerY), pos);
            } else if(target != null){
                Vec2 shootTo = Predict.intercept(pos.x, pos.y,target.getX(), target.getY(),
                        target.getTargetVelocityX(), target.getTargetVelocityY(), weapon.bullet.speed * weapon.velMul);
                float angle = shootTo.sub(pos).angle();
                weapon.shoot(player, angle, pos);
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
