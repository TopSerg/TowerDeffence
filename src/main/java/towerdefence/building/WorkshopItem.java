package towerdefence.building;

import towerdefence.resource.ResourceType;
import towerdefence.world.Direction;

/** Одна физическая единица ресурса, движущаяся по внутренней сетке Workshop. */
public final class WorkshopItem {
    private final ResourceType type;
    private int x;
    private int y;
    private Direction entrySide;

    public WorkshopItem(ResourceType type, int x, int y, Direction entrySide) {
        if (type == null) throw new IllegalArgumentException("Тип ресурса обязателен");
        this.type = type;
        this.x = x;
        this.y = y;
        this.entrySide = entrySide;
    }

    public ResourceType getType() { return type; }
    public int getX() { return x; }
    public int getY() { return y; }
    public Direction getEntrySide() { return entrySide; }

    public void moveTo(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void leaveGateway() { entrySide = null; }
}
