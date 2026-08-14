package towerdefence.combat;

import towerdefence.building.BuildingType;
import towerdefence.world.Tile;

import java.awt.*;
import java.util.List;

public class SniperTower extends CombatTower {
    public static final int MAX_AMMO = 36;
    public static final double ATTACK_RANGE = 8.0;
    public static final int DAMAGE = 55;
    public static final int FIRE_COOLDOWN_TICKS = 60;

    public SniperTower(Tile position) {
        super(135, position, new Color(83, 76, 145), BuildingType.SNIPER_TOWER,
                MAX_AMMO, DAMAGE, ATTACK_RANGE, FIRE_COOLDOWN_TICKS);
    }

    /** Снайпер приоритетно выбирает самого прочного робота в радиусе. */
    @Override
    protected Enemy findTarget(List<Enemy> enemies) {
        Enemy strongest = null;
        int highestHealth = -1;
        double nearestOnTie = Double.MAX_VALUE;
        if (enemies == null) return null;

        for (Enemy enemy : enemies) {
            if (!isTargetInRange(enemy)) continue;
            int health = enemy.getHealth();
            double distance = distanceSquared(enemy);
            if (health > highestHealth || (health == highestHealth && distance < nearestOnTie)) {
                strongest = enemy;
                highestHealth = health;
                nearestOnTie = distance;
            }
        }
        return strongest;
    }

    @Override
    protected Color getShotColor() { return new Color(190, 175, 255, 235); }

    @Override
    protected float getShotWidth() { return 3.3f; }

    @Override
    protected int getShotEffectDurationTicks() { return 8; }

    @Override
    public void render(Graphics g, int tileSize) {
        int x = position.getX() * tileSize;
        int y = position.getY() * tileSize;
        int centerX = x + tileSize / 2;
        int centerY = y + tileSize / 2;

        g.setColor(new Color(42, 38, 66));
        g.fillRect(x + 3, y + 5, tileSize - 6, tileSize - 8);
        g.setColor(color);
        g.fillRect(x + 8, y + 7, tileSize - 16, tileSize - 16);

        Graphics2D g2 = (Graphics2D) g.create();
        double angle = getAimAngle();
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(28, 25, 42));
        int barrelLength = (int) (tileSize * 0.72);
        int bx = centerX + (int) Math.round(Math.cos(angle) * barrelLength);
        int by = centerY + (int) Math.round(Math.sin(angle) * barrelLength);
        g2.drawLine(centerX, centerY, bx, by);
        g2.setColor(new Color(215, 205, 255));
        g2.fillOval(centerX - 3, centerY - 3, 6, 6);
        g2.dispose();

        renderSupplyAndAmmo(g, x, y, tileSize);
    }
}
