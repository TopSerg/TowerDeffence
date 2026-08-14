package towerdefence.unit;

import towerdefence.combat.Weapon;
import towerdefence.world.GameMap;
import towerdefence.world.Tile;

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
