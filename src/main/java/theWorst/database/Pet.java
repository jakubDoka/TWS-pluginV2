package theWorst.database;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Log;
import arc.util.Time;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import mindustry.content.Bullets;
import mindustry.content.Fx;
import mindustry.entities.Effects;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.type.Player;
import mindustry.gen.Call;
import theWorst.Tools;

import javax.print.attribute.standard.MediaSize;
import java.util.ArrayList;

import static theWorst.Tools.logInfo;

public class Pet {
    Vec2 pos = new Vec2(), vel = new Vec2();
    public float acceleration = .3f, maxSpeed = 7, reloadSpeed = .1f, bulletVel = 2f, bulletLive = 2f, attraction = 1f;
    public float accuracy = .1f;
    public int shot = 1;
    public String trailName = "fire", bulletName = "flakPlastic", colorCode = "ffffff";
    public String name = "fire-pet";
    float time = 0;
    boolean loaded = true;
    Effects.Effect trail = Fx.fire;
    BulletType bullet = Bullets.slagShot;
    Color color = Color.white;

    public Pet(){}

    @JsonCreator public Pet(
            @JsonProperty("acceleration") float acceleration,
            @JsonProperty("maxSpeed") float maxSpeed,
            @JsonProperty("reloadSpeed") float reloadSpeed,
            @JsonProperty("attraction") float attraction,
            @JsonProperty("accuracy") float accuracy,
            @JsonProperty("shot") int shot,
            @JsonProperty("trailName") String  trailName,
            @JsonProperty("bulletName") String  bulletName,
            @JsonProperty("colorCode") String  colorCode,
            @JsonProperty("name") String  name){
        this.acceleration = acceleration;
        this.maxSpeed = maxSpeed;
        this.reloadSpeed = reloadSpeed;
        this.attraction = attraction;
        this.name = name;
        if(name == null){
            this.name = "noName";
            logInfo("missing-name","pet");
        }
        this.accuracy = accuracy;
        this.shot = Mathf.clamp(shot, 1, 20);
        if(trailName != null){
            Effects.Effect resolved = (Effects.Effect) Tools.getPropertyByName(Fx.class,trailName,null);
            if(resolved != null){
                trail = resolved;
            } else {
                logInfo("pet-invalid-trail", name, trailName);
            }
        }
        if(bulletName == null) bullet = null;
        else{
            bullet = (BulletType) Tools.getPropertyByName(Bullets.class, bulletName,null);
            if(bullet == null) logInfo("pet-invalid-bullet", name, bulletName);
        }
        color = Color.valueOf(colorCode);
    }

    public Pet(Pet other){
        this.name = other.name;
        this.acceleration = other.acceleration;
        this.reloadSpeed = other.reloadSpeed;
        this.bullet = other.bullet;
        this.trail = other.trail;
        this.color = other.color;
        this.bulletLive = other.bulletLive;
        this.bulletVel = other.bulletVel;
        this.attraction = other.attraction;
        this.shot = other.shot;
        this.accuracy = other.accuracy;
    }


    public void update(Player player, ArrayList<Pet> others) {
        if (player.isDead()) return;
        pos.add(new Vec2(vel).scl(Time.delta()));
        vel.add(new Vec2(player.getX(),player.getY()).sub(pos).nor().scl(acceleration));
        for(Pet p : others){
            vel.add(new Vec2(p.pos).sub(pos).nor().scl(acceleration*attraction));
        }
        vel.clamp(0,maxSpeed);
        Call.onEffectReliable(trail, pos.x, pos.y, 0f, color);
        if(bullet == null) return;
        if (player.isShooting() && loaded) {
            loaded = false;
            shoot(player);
        } else {
            time += Time.delta();
            if(time > reloadSpeed){
                loaded = true;
                time = 0;
            }
        }
    }

    public void shoot(Player player){
        for(int i = 0; i<shot; i++){
            Call.createBullet(bullet, player.getTeam(), pos.x, pos.y,
                    player.rotation+ Mathf.random(-accuracy,accuracy), bulletVel, bulletLive);
        }
    }
}
