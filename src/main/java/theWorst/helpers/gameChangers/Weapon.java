package theWorst.helpers.gameChangers;

import arc.math.Mathf;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import mindustry.content.Bullets;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.type.Player;
import mindustry.gen.Call;
import mindustry.type.Item;

import static theWorst.Tools.Commands.logInfo;
import static theWorst.Tools.General.getPropertyByName;

public class Weapon {
    BulletType bullet;
    Item item;
    public String name = "noName";
    public float velMul = 1f, liveMul = 1f, fireRate = .1f, accuracy;
    public int bulletsPerShot = 1, consumes = 1, ammoEfficiency = 1;
    boolean valid = true;

    @JsonGetter
    public String getBullet() {
        return "flakGlass";
    }
    @JsonGetter public String getItem() {
        return "metaglass";
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
            @JsonProperty("ammoEfficiency") int ammoEfficiency ){
        this.bullet = (BulletType) getPropertyByName(Bullets.class, bullet, null);
        if(this.bullet == null){
            logInfo("weapon-invalid-bullet");
            valid = false;
        } else {
            valid = true;
        }
        this.item = Loadout.getItemByName(item);
        this.name = name;
        this.velMul = velMul;
        this.liveMul = liveMul;
        this.fireRate = fireRate;
        this.accuracy = accuracy;
        this.bulletsPerShot = bulletsPerShot;
        this.consumes = consumes;
        this.ammoEfficiency = ammoEfficiency;
    }

    public void shoot(Player player, float rotation, Position pos){
        for(int i = 0; i < bulletsPerShot; i++){
            Call.createBullet(bullet, player.getTeam(), pos.getX(), pos.getY(),
                    rotation + Mathf.random(-accuracy,accuracy), velMul, liveMul);
        }
    }
}
