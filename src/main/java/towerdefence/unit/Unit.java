package towerdefence.unit;

import towerdefence.resource.Inventory;
import towerdefence.resource.Resource;
import towerdefence.world.Entity;
import towerdefence.world.GameMap;
import towerdefence.world.Pathfinder;
import towerdefence.world.Tile;

import java.awt.*;
import java.util.List;

public class Unit extends Entity {
    protected final UnitType type;
    protected final Inventory inventory;
    protected final GameMap map;

    private Tile targetTile;
    private List<Tile> waypoints;
    private int currentWaypointIndex;
    private Tile intermediateTarget;
    protected float realX;
    protected float realY;

    public Unit(Tile position, UnitType type, Color color, GameMap map) {
        this(position, type, color, map, type == UnitType.WORKER ? Worker.INVENTORY_CAPACITY : 12);
    }

    protected Unit(Tile position, UnitType type, Color color, GameMap map, int inventoryCapacity) {
        super(10, 0.1f, position, color);
        if (position == null || map == null || type == null) {
            throw new IllegalArgumentException("Юниту нужны позиция, тип и карта");
        }
        this.map = map;
        this.type = type;
        this.inventory = new Inventory(inventoryCapacity);
        this.realX = position.getX();
        this.realY = position.getY();
    }

    @Override
    public void render(Graphics g, int tileSize) {
        g.setColor(color);
        g.fillOval((int) (realX * tileSize) + 2, (int) (realY * tileSize) + 2,
                tileSize - 4, tileSize - 4);
        renderPath(g, tileSize);
    }

    protected void renderPath(Graphics g, int tileSize) {
        if (waypoints == null || waypoints.isEmpty()) return;
        g.setColor(new Color(0, 255, 0, 100));
        Tile previous = position;
        for (int i = currentWaypointIndex; i < waypoints.size(); i++) {
            Tile next = waypoints.get(i);
            if (previous != null) {
                g.drawLine(previous.getX() * tileSize + tileSize / 2,
                        previous.getY() * tileSize + tileSize / 2,
                        next.getX() * tileSize + tileSize / 2,
                        next.getY() * tileSize + tileSize / 2);
            }
            previous = next;
        }
    }

    /** Задаёт путь. Возвращает false, если цель недостижима. */
    public boolean setTarget(Tile target) {
        if (target == null) return false;
        if (target == position) {
            clearMovement();
            return true;
        }
        List<Tile> path = new Pathfinder(map).findPath(position, target);
        if (path == null || path.isEmpty()) return false;
        this.waypoints = path;
        this.currentWaypointIndex = path.get(0) == position ? 1 : 0;
        this.targetTile = target;
        if (currentWaypointIndex >= path.size()) {
            clearMovement();
            return true;
        }
        this.intermediateTarget = path.get(currentWaypointIndex);
        return true;
    }

    @Override
    public void update() {
        updateMovement();
    }

    protected void updateMovement() {
        if (intermediateTarget == null) return;
        if ((!intermediateTarget.isPassable() || intermediateTarget.hasBuilding())
                && intermediateTarget != targetTile) {
            recalculatePath();
            return;
        }
        if (intermediateTarget.hasUnit() && intermediateTarget.getUnit() != this) {
            recalculatePath();
            return;
        }

        float dx = intermediateTarget.getX() - realX;
        float dy = intermediateTarget.getY() - realY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance > speed) {
            realX += dx / distance * speed;
            realY += dy / distance * speed;
            return;
        }

        move(intermediateTarget);
        currentWaypointIndex++;
        if (waypoints != null && currentWaypointIndex < waypoints.size()) {
            intermediateTarget = waypoints.get(currentWaypointIndex);
        } else {
            clearMovement();
        }
    }

    private void recalculatePath() {
        Tile destination = targetTile;
        clearMovement();
        if (destination != null) setTarget(destination);
    }

    protected void clearMovement() {
        waypoints = null;
        intermediateTarget = null;
        targetTile = null;
        currentWaypointIndex = 0;
    }

    public void move(Tile newTile) {
        if (newTile == null) return;
        if (position != null && position.getUnit() == this) position.setUnit(null);
        position = newTile;
        newTile.setUnit(this);
        realX = position.getX();
        realY = position.getY();
    }

    public Inventory getInventory() { return inventory; }
    public UnitType getUnitType() { return type; }
    public boolean isMoving() { return intermediateTarget != null; }
    public Tile getMovementTarget() { return targetTile; }
    public float getRealX() { return realX; }
    public float getRealY() { return realY; }
    public void baseUpdate() { }
    public void collectResource(Resource resource) { }
}
