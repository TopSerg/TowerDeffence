package towerdefence.building;

import towerdefence.world.Direction;

/** Один внутренний конвейер Workshop, занимающий ровно одну внутреннюю клетку. */
public final class InteriorConveyor {
    private final int x;
    private final int y;
    private Direction direction;

    public InteriorConveyor(int x, int y, Direction direction) {
        this.x = x;
        this.y = y;
        this.direction = direction == null ? Direction.RIGHT : direction;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public Direction getDirection() { return direction; }

    public void setDirection(Direction direction) {
        if (direction != null) this.direction = direction;
    }

    public void rotateClockwise() {
        direction = direction.rotateClockwise();
    }
}
