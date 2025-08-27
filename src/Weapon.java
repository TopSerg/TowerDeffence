import javax.swing.*;
import java.awt.event.ActionListener;
import java.sql.Time;

public class Weapon {
    private int damage;
    private int shootingSpeed;
    private int reloadSpeed;
    private int bullets;
    private int amountReloads;
    private int bulletsLeft;
    private int reloadTimeLeft;
    private boolean isReloading;
    private int weigh;
    private int range;

    WeaponType type;

    Timer timer;

    public Weapon(WeaponType type){
        this.type = type;
        parseType();
    }

    public void update() {
        if (isReloading) {
            reloadTimeLeft--;
            if (reloadTimeLeft <= 0) {
                bulletsLeft = bullets;
                isReloading = false;
            }
        }
    }

    public boolean shoot(Entity target) {
        if (isReloading || bulletsLeft <= 0) {
            startReload();
            return false;
        }

        target.takeDamage(damage);
        bulletsLeft--;
        return true;
    }

    private void startReload() {
        if (!isReloading) {
            amountReloads--;
            isReloading = true;
            reloadTimeLeft = reloadSpeed;
        }
        reloadTimeLeft--;
        if (reloadTimeLeft == 0){
            isReloading = false;
        }
    }

    public void parseType(){
        switch (type){
            case AK:
                damage = 10;
                shootingSpeed = 5;
                reloadSpeed = 20;
                bullets = 30;
                amountReloads = 4;
                weigh = 5;
                range = 4;
                break;
            case PM:
                damage = 5;
                shootingSpeed = 15;
                reloadSpeed = 10;
                bullets = 10;
                amountReloads = 4;
                weigh = 1;
                range = 6;
                break;
            case MOUTH:
                damage = 20;
                shootingSpeed = 8;
                reloadSpeed = 0;
                bullets = 30;
                amountReloads = 4;
                weigh = 0;
                range = 1;
                break;
            default:
                damage = 0;
                shootingSpeed = 0;
                reloadSpeed = 0;
                bullets = 0;
                amountReloads = 0;
                weigh = 0;
                range = 0;
        }
    }

    public int getRange() {
        return range;
    }
}
