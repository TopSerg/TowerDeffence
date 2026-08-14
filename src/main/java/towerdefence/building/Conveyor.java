package towerdefence.building;

import towerdefence.world.Direction;
import towerdefence.world.GameMap;
import towerdefence.world.Tile;

import java.awt.*;

public class Conveyor extends Building {
    private final Direction direction;
    private boolean active;
    private int animationTick;

    public Conveyor(Tile position, Direction direction) {
        super(80, 0, position, new Color(190, 145, 45));
        this.type = BuildingType.CONVEYOR;
        this.direction = direction;
    }

    public Direction getDirection() {
        return direction;
    }

    public Tile getOutputTile(GameMap map) {
        return map.getTile(
                position.getX() + direction.getDx(),
                position.getY() + direction.getDy()
        );
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean blocksMovement() {
        return false;
    }

    @Override
    public void render(Graphics g, int tileSize) {
        int x = position.getX() * tileSize;
        int y = position.getY() * tileSize;

        g.setColor(new Color(82, 67, 43));
        g.fillRect(x + 2, y + 4, tileSize - 4, tileSize - 8);

        g.setColor(active ? new Color(230, 183, 76) : color);
        g.fillRect(x + 4, y + 7, tileSize - 8, tileSize - 14);

        g.setColor(new Color(40, 35, 28));
        drawArrow(g, x, y, tileSize);

        if (active) {
            int phase = (animationTick / 3) % Math.max(1, tileSize - 10);
            int centerX = x + tileSize / 2;
            int centerY = y + tileSize / 2;
            int dotX = centerX + direction.getDx() * (phase - (tileSize - 10) / 2);
            int dotY = centerY + direction.getDy() * (phase - (tileSize - 10) / 2);
            g.setColor(new Color(245, 235, 190));
            g.fillOval(dotX - 3, dotY - 3, 6, 6);
        }
    }

    private void drawArrow(Graphics g, int x, int y, int tileSize) {
        int cx = x + tileSize / 2;
        int cy = y + tileSize / 2;
        int length = tileSize / 3;
        int endX = cx + direction.getDx() * length;
        int endY = cy + direction.getDy() * length;
        int startX = cx - direction.getDx() * length;
        int startY = cy - direction.getDy() * length;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(startX, startY, endX, endY);

        int side = Math.max(4, tileSize / 7);
        if (direction == Direction.RIGHT || direction == Direction.LEFT) {
            int sign = direction == Direction.RIGHT ? 1 : -1;
            g2.drawLine(endX, endY, endX - sign * side, endY - side);
            g2.drawLine(endX, endY, endX - sign * side, endY + side);
        } else {
            int sign = direction == Direction.DOWN ? 1 : -1;
            g2.drawLine(endX, endY, endX - side, endY - sign * side);
            g2.drawLine(endX, endY, endX + side, endY - sign * side);
        }
        g2.dispose();
    }

    @Override
    public void update() {
        animationTick++;
    }
}
