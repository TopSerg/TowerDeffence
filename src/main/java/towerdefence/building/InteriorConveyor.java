package towerdefence.building;

import towerdefence.world.Direction;

/** Один внутренний конвейер Workshop, занимающий ровно одну внутреннюю клетку. */
public final class InteriorConveyor {
    private final int x;
    private final int y;
    private Direction direction;
    private int damage;

    public InteriorConveyor(int x, int y, Direction direction) {
        this.x = x;
        this.y = y;
        this.direction = direction == null ? Direction.RIGHT : direction;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public Direction getDirection() { return direction; }
    public int getDamage() { return damage; }
    public boolean isOperational() { return damage < 100; }

    public void setDirection(Direction direction) {
        if (direction != null) this.direction = direction;
    }

    public void rotateClockwise() { direction = direction.rotateClockwise(); }
    public void damage(int amount) { if (amount > 0) damage = Math.min(100, damage + amount); }
    public void repair(int amount) { if (amount > 0) damage = Math.max(0, damage - amount); }
}
