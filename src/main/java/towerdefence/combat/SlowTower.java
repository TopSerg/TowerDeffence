package towerdefence.combat;

import towerdefence.building.BuildingType;
import towerdefence.world.Tile;

import java.awt.*;
import java.util.List;

public class SlowTower extends CombatTower {
    public static final int MAX_AMMO = 80;
    public static final double ATTACK_RANGE = 5.5;
    public static final int DAMAGE = 4;
    public static final int FIRE_COOLDOWN_TICKS = 24;
    public static final float SLOW_MULTIPLIER = 0.55f;
    public static final int SLOW_DURATION_TICKS = 120;

    public SlowTower(Tile position) {
        super(150, position, new Color(58, 145, 158), BuildingType.SLOW_TOWER,
                MAX_AMMO, DAMAGE, ATTACK_RANGE, FIRE_COOLDOWN_TICKS);
    }

    /** Сначала замедляет ещё не обработанную цель, затем поддерживает эффект. */
    @Override
    protected Enemy findTarget(List<Enemy> enemies) {
        Enemy nearestFresh = null;
        Enemy nearestAny = null;
        double freshDistance = Double.MAX_VALUE;
        double anyDistance = Double.MAX_VALUE;
        if (enemies == null) return null;

        for (Enemy enemy : enemies) {
            if (!isTargetInRange(enemy)) continue;
            double distance = distanceSquared(enemy);
            if (distance < anyDistance) {
                anyDistance = distance;
                nearestAny = enemy;
            }
            if (!enemy.isSlowed() && distance < freshDistance) {
                freshDistance = distance;
                nearestFresh = enemy;
            }
        }
        return nearestFresh != null ? nearestFresh : nearestAny;
    }

    @Override
    protected boolean shouldRetarget(Enemy current, List<Enemy> enemies) {
        if (current == null || !current.isSlowed() || enemies == null) return false;
        for (Enemy enemy : enemies) {
            if (enemy != current && isTargetInRange(enemy) && !enemy.isSlowed()) return true;
        }
        return false;
    }

    @Override
    protected void hitTarget(Enemy target) {
        super.hitTarget(target);
        if (target.isAlive()) target.applySlow(SLOW_MULTIPLIER, SLOW_DURATION_TICKS);
    }

    @Override
    protected Color getShotColor() { return new Color(105, 235, 255, 230); }

    @Override
    protected float getShotWidth() { return 4.2f; }

    @Override
    protected int getShotEffectDurationTicks() { return 7; }

    @Override
    public void render(Graphics g, int tileSize) {
        int x = position.getX() * tileSize;
        int y = position.getY() * tileSize;
        int centerX = x + tileSize / 2;
        int centerY = y + tileSize / 2;

        g.setColor(new Color(34, 62, 68));
        g.fillRect(x + 4, y + 7, tileSize - 8, tileSize - 10);
        g.setColor(color);
        g.fillOval(x + 6, y + 5, tileSize - 12, tileSize - 12);
        g.setColor(new Color(165, 245, 250));
        g.drawOval(x + 10, y + 9, tileSize - 20, tileSize - 20);

        Graphics2D g2 = (Graphics2D) g.create();
        double angle = getAimAngle();
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(25, 48, 52));
        int barrelLength = tileSize / 2;
        g2.drawLine(centerX, centerY,
                centerX + (int) Math.round(Math.cos(angle) * barrelLength),
                centerY + (int) Math.round(Math.sin(angle) * barrelLength));
        g2.dispose();

        renderSupplyAndAmmo(g, x, y, tileSize);
    }
}
