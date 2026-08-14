package towerdefence.building;

import towerdefence.world.Tile;

import java.awt.*;

public class Wall extends Building {
    public static final int MAX_HEALTH = 360;

    public Wall(Tile position) {
        super(MAX_HEALTH, 0, position, new Color(126, 130, 138));
        this.type = BuildingType.WALL;
    }

    public int getMaxHealth() {
        return MAX_HEALTH;
    }

    @Override
    public void render(Graphics g, int tileSize) {
        int x = position.getX() * tileSize;
        int y = position.getY() * tileSize;

        g.setColor(new Color(72, 75, 82));
        g.fillRect(x + 2, y + 3, tileSize - 4, tileSize - 6);
        g.setColor(color);
        g.fillRect(x + 4, y + 5, tileSize - 8, tileSize - 10);

        g.setColor(new Color(84, 88, 95));
        int half = tileSize / 2;
        g.drawLine(x + 4, y + half, x + tileSize - 4, y + half);
        g.drawLine(x + half, y + 5, x + half, y + half);
        g.drawLine(x + tileSize / 3, y + half, x + tileSize / 3, y + tileSize - 5);

        if (health < MAX_HEALTH) {
            int width = tileSize - 8;
            int healthWidth = Math.max(0, width * health / MAX_HEALTH);
            g.setColor(new Color(55, 20, 20));
            g.fillRect(x + 4, y + 1, width, 4);
            g.setColor(new Color(90, 220, 105));
            g.fillRect(x + 4, y + 1, healthWidth, 4);
        }
    }

    @Override
    public void update() {
        // Стена не имеет активной логики.
    }
}
