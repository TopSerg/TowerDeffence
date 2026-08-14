package towerdefence.building;

import towerdefence.world.Direction;
import towerdefence.world.Tile;

import java.awt.*;
import java.util.Collections;
import java.util.List;

/** Пристройка 1x1 к Workshop. Создаёт внутренний gateway на соответствующей стороне. */
public class FactoryPort extends Building {
    private final boolean input;
    private Direction attachedSide;
    private Workshop workshop;

    public FactoryPort(Tile position, boolean input) {
        super(120, 0, position, input ? new Color(68, 145, 178) : new Color(178, 120, 68));
        this.input = input;
        this.type = input ? BuildingType.FACTORY_INPUT_PORT : BuildingType.FACTORY_OUTPUT_PORT;
    }

    public boolean isInput() { return input; }
    public Direction getAttachedSide() { return attachedSide; }
    public Workshop getWorkshop() { return workshop; }

    public boolean attachTo(Workshop workshop, Direction side) {
        if (workshop == null || side == null) return false;
        if (this.workshop != null && this.workshop != workshop) this.workshop.unregisterPort(this);
        this.workshop = workshop;
        this.attachedSide = side;
        workshop.registerPort(this);
        return true;
    }

    public List<Point> getGatewayCells() {
        return workshop == null ? Collections.emptyList() : workshop.getGatewayCells(this);
    }

    public void detach() {
        if (workshop != null) workshop.unregisterPort(this);
        workshop = null;
        attachedSide = null;
    }

    @Override
    public void render(Graphics g, int tileSize) {
        if (position == null) return;
        int x = position.getX() * tileSize;
        int y = position.getY() * tileSize;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(input ? new Color(46, 92, 125) : new Color(125, 82, 46));
        g2.fillRoundRect(x + 4, y + 4, tileSize - 8, tileSize - 8, 7, 7);
        g2.setColor(color);
        g2.fillRoundRect(x + 7, y + 7, tileSize - 14, tileSize - 14, 6, 6);
        g2.setColor(Color.WHITE);
        int cx = x + tileSize / 2;
        int cy = y + tileSize / 2;
        int len = tileSize / 4;
        if (attachedSide == null) {
            g2.drawString(input ? "IN" : "OUT", x + 4, y + tileSize - 5);
        } else {
            int dx = attachedSide.getDx();
            int dy = attachedSide.getDy();
            if (!input) { dx = -dx; dy = -dy; }
            g2.drawLine(cx - dx * len, cy - dy * len, cx + dx * len, cy + dy * len);
            g2.drawLine(cx + dx * len, cy + dy * len, cx + dx * len - dy * 4, cy + dy * len - dx * 4);
            g2.drawLine(cx + dx * len, cy + dy * len, cx + dx * len + dy * 4, cy + dy * len + dx * 4);
        }
        g2.dispose();
    }

    @Override
    public void update() { }
}
