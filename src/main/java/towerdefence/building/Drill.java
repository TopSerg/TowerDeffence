package towerdefence.building;

import towerdefence.resource.Resource;
import towerdefence.resource.ResourceType;
import towerdefence.world.Tile;

import java.awt.*;

public class Drill extends Building {
    public static final int MAX_HEALTH = 130;
    public static final int BUFFER_CAPACITY = 20;
    private static final int EXTRACTION_INTERVAL_TICKS = 42;

    private final ResourceType resourceType;
    private int buffer;
    private int extractionCooldown;
    private boolean outputConnected;

    public Drill(Tile position) {
        super(MAX_HEALTH, 0, position, new Color(92, 150, 160));
        if (position == null || !position.hasResource()) {
            throw new IllegalArgumentException("Бур должен быть установлен на месторождение");
        }
        this.type = BuildingType.DRILL;
        this.resourceType = position.getResource().getType();
    }

    @Override
    public void update() {
        if (!isAlive || position == null || buffer >= BUFFER_CAPACITY) return;
        Resource deposit = position.getResource();
        if (deposit == null || deposit.getType() != resourceType || deposit.isDepleted()) return;

        if (extractionCooldown > 0) {
            extractionCooldown--;
            return;
        }

        int extracted = deposit.extract(1);
        if (extracted > 0) {
            buffer += extracted;
            extractionCooldown = EXTRACTION_INTERVAL_TICKS;
        }
        if (deposit.isDepleted()) position.setResource(null);
    }

    public int takeFromBuffer(int amount) {
        if (amount <= 0 || buffer <= 0) return 0;
        int taken = Math.min(amount, buffer);
        buffer -= taken;
        return taken;
    }

    public void returnToBuffer(int amount) {
        if (amount > 0) buffer = Math.min(BUFFER_CAPACITY, buffer + amount);
    }

    public int getBuffer() { return buffer; }
    public int getBufferCapacity() { return BUFFER_CAPACITY; }
    public ResourceType getResourceType() { return resourceType; }
    public boolean isOutputConnected() { return outputConnected; }
    public void setOutputConnected(boolean outputConnected) { this.outputConnected = outputConnected; }

    public int getRemainingDeposit() {
        Resource deposit = position == null ? null : position.getResource();
        return deposit == null ? 0 : deposit.getAmount();
    }

    @Override
    public void render(Graphics g, int tileSize) {
        int x = position.getX() * tileSize;
        int y = position.getY() * tileSize;
        int cx = x + tileSize / 2;
        int cy = y + tileSize / 2;

        g.setColor(new Color(45, 65, 68));
        g.fillRect(x + 3, y + 3, tileSize - 6, tileSize - 6);
        g.setColor(color);
        g.fillOval(x + 6, y + 6, tileSize - 12, tileSize - 12);
        g.setColor(new Color(35, 45, 48));
        g.fillOval(cx - 5, cy - 5, 10, 10);
        g.drawLine(cx, cy, cx, y + 2);
        g.drawLine(cx, cy, x + tileSize - 2, cy);
        g.drawLine(cx, cy, cx, y + tileSize - 2);
        g.drawLine(cx, cy, x + 2, cy);

        g.setColor(outputConnected ? new Color(80, 230, 125) : new Color(235, 85, 75));
        g.fillOval(x + tileSize - 9, y + 3, 6, 6);

        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, tileSize / 5)));
        g.drawString(Integer.toString(buffer), x + 3, y + tileSize - 3);
    }
}
