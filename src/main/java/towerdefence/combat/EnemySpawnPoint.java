package towerdefence.combat;

import towerdefence.world.Tile;

import java.awt.*;

/**
 * Отдельный маркер точки появления противников.
 * Это не здание: он не занимает клетку для поиска пути, но запрещает строительство поверх себя.
 */
public class EnemySpawnPoint {
    private final Tile position;
    private int animationTick;

    public EnemySpawnPoint(Tile position) {
        if (position == null) {
            throw new IllegalArgumentException("Позиция точки спавна не может быть null");
        }
        this.position = position;
    }

    public Tile getPosition() {
        return position;
    }

    public void update() {
        animationTick++;
    }

    public void render(Graphics g, int tileSize) {
        int x = position.getX() * tileSize;
        int y = position.getY() * tileSize;
        int pulse = (animationTick / 5) % 6;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(110, 20, 35, 180));
        g2.fillOval(x + 3, y + 3, tileSize - 6, tileSize - 6);
        g2.setColor(new Color(245, 65, 85));
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(x + 5 - pulse / 2, y + 5 - pulse / 2,
                tileSize - 10 + pulse, tileSize - 10 + pulse);
        g2.drawLine(x + 9, y + tileSize / 2, x + tileSize - 9, y + tileSize / 2);
        g2.drawLine(x + tileSize / 2, y + 9, x + tileSize / 2, y + tileSize - 9);
        g2.dispose();
    }
}
