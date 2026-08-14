package towerdefence.world;

public enum Direction {
    UP(0, -1, "Вверх"),
    RIGHT(1, 0, "Вправо"),
    DOWN(0, 1, "Вниз"),
    LEFT(-1, 0, "Влево");

    private final int dx;
    private final int dy;
    private final String displayName;

    Direction(int dx, int dy, String displayName) {
        this.dx = dx;
        this.dy = dy;
        this.displayName = displayName;
    }

    public int getDx() {
        return dx;
    }

    public int getDy() {
        return dy;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Direction rotateClockwise() {
        Direction[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
