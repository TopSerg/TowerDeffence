import java.awt.*;

public class Solider extends Unit {
    Weapon currentWeapon;

    public Solider(Tile position, UnitType type, Color color, GameMap map) {
        super(position, type, color, map);
    }

    @Override
    public void baseUpdate() {
        currentWeapon.shoot(this);
    }
}
