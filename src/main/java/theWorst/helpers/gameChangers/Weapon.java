package theWorst.helpers.gameChangers;

import arc.math.Mathf;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import mindustry.content.Bullets;
import mindustry.content.Items;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.traits.ShooterTrait;
import mindustry.entities.type.Player;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.type.Item;

import java.io.IOException;

import static theWorst.tools.General.getPropertyByName;

public class Weapon {
    BulletType bullet = Bullets.standardCopper;
    Item item = Items.copper;
    public String name = "copperGun";
    public float velMul = 1f, liveMul = 1f, fireRate = 5f, accuracy = 4;
    public int bulletsPerShot = 2, consumes = 1, ammoMultiplier = 10;

    @JsonGetter public String getBullet() {
        return "standardCopper";
    }
    @JsonGetter public String getItem() {
        return item.name;
    }

    Weapon(){}

    @JsonCreator
    public Weapon(
            @JsonProperty("name") String name,
            @JsonProperty("bullet") String bullet,
            @JsonProperty("item") String item,
            @JsonProperty("velMul") float velMul,
            @JsonProperty("liveMul") float liveMul,
            @JsonProperty("fireRate") float fireRate,
            @JsonProperty("accuracy") float accuracy,
            @JsonProperty("bulletsPerShot") int bulletsPerShot,
            @JsonProperty("consumes") int consumes,
            @JsonProperty("ammoMultiplier") int ammoMultiplier) throws IOException {
        this.bullet = (BulletType) getPropertyByName(Bullets.class, bullet, null);
        if(this.bullet == null){
            throw new IOException("Invalid bullet type in weapon with name " + name + ".");
        }
        if(!Loadout.getItemByName(item).isEmpty()) {
            this.item = Loadout.getItemByName(item).get(0);
        } else {
            this.item = null;
        }

        this.name = name;
        this.velMul = velMul;
        this.liveMul = liveMul;
        this.fireRate = fireRate;
        this.accuracy = accuracy;
        this.bulletsPerShot = bulletsPerShot;
        this.consumes = consumes;
        this.ammoMultiplier = ammoMultiplier;
    }

    @JsonIgnore public float getRange(){
        return bullet.speed * velMul * bullet.lifetime * liveMul;
    }

    public void shoot(Player player, Position pos, float dx, float dy){
        float angle = pos.angleTo(dx, dy);
        Team team = player.getTeam();
        float x = pos.getX(), y = pos.getY();
        for(int i = 0; i < bulletsPerShot; i++){
            Call.createBullet(bullet, team, x, y, angle + Mathf.random(-accuracy,accuracy), velMul,
                    getLiveTime(pos, dx, dy, velMul, liveMul));
        }
    }

    public void playerShoot(Player player){
        float angle = player.angleTo(player.pointerX, player.pointerY);
        Team team = player.getTeam();
        BulletType pb = player.mech.weapon.bullet;
        float x = player.getX(), y = player.getY();
        float sMul = pb.speed / bullet.speed, lMul = pb.lifetime / bullet.lifetime;
        lMul = getLiveTime(player, player.pointerX, player.pointerY,sMul, lMul);
        Vec2 playerVel = ((ShooterTrait)player).velocity();
        Vec2 resultVel = new Vec2(1, 0).rotate(angle).scl(sMul * bullet.speed).add(playerVel);
        angle = resultVel.angle();
        sMul = resultVel.len() / bullet.speed;
        for(int i = 0;i < bulletsPerShot; i++){
            Call.createBullet(bullet, team, x, y, angle + Mathf.random(-accuracy, accuracy), sMul, lMul);
        }
    }

    public float getLiveTime(Position origin,float dx,float dy, float originalV, float originalL) {
        if(bullet.collides) return originalL;
        float val = origin.dst(dx, dy) / (bullet.speed * originalV * bullet.lifetime);
        return Mathf.clamp(val, 0, originalL);
    }
}
