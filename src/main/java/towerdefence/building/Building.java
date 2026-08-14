package towerdefence.building;

import towerdefence.world.Entity;
import towerdefence.world.Tile;

import java.awt.*;

public abstract class Building extends Entity {
    protected BuildingType type;

    public Building(int health, float speed, Tile startPosition, Color color) {
        super(health, speed, startPosition, color);
    }

    @Override
    public void render(Graphics g, int tileSize) {
        g.setColor(color);
        g.fillRect(position.getX() * tileSize, position.getY() * tileSize, tileSize, tileSize);
    }

    public BuildingType getType() { return type; }

    public int getFootprintWidth() { return 1; }
    public int getFootprintHeight() { return 1; }

    public boolean occupies(Tile tile) {
        return tile != null && position != null
                && tile.getX() >= position.getX() && tile.getX() < position.getX() + getFootprintWidth()
                && tile.getY() >= position.getY() && tile.getY() < position.getY() + getFootprintHeight();
    }

    /** Позволяет позже вводить не блокирующие объекты карты. */
    public boolean blocksMovement() { return true; }

    @Override
    public abstract void update();
}
