package towerdefence.world;

import towerdefence.building.Building;
import towerdefence.resource.Resource;
import towerdefence.unit.Unit;

public class Tile {
    private final int x;
    private final int y;
    private TileType type;
    private Resource resource;
    private Building building;
    private Unit unit;
    private boolean isPassable;

    public Tile(int x, int y, TileType type, Resource resource, Building building, boolean isPassable) {
        this.x = x;
        this.y = y;
        this.type = type;
        this.resource = resource;
        this.building = building;
        this.isPassable = isPassable;
    }

    public boolean hasUnit() {
        return unit != null;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public boolean hasResource() {
        return resource != null;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public Resource getResource() {
        return resource;
    }

    public boolean hasBuilding() {
        return building != null;
    }

    public void setBuilding(Building building) {
        this.building = building;
    }

    public Building getBuilding() {
        return building;
    }

    public TileType getType() {
        return type;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setType(TileType type) {
        this.type = type;
    }

    public Unit getUnit() {
        return unit;
    }

    public boolean isPassable() {
        return isPassable;
    }

    public void setPassable(boolean passable) {
        isPassable = passable;
    }
}
