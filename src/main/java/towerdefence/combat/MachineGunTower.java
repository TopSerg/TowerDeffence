package towerdefence.combat;

import towerdefence.building.BuildingType;
import towerdefence.world.Tile;

import java.awt.*;

public class MachineGunTower extends CombatTower {
    public static final int MAX_AMMO = 100;
    public static final double ATTACK_RANGE = 4.5;
    public static final int DAMAGE = 8;
    public static final int FIRE_COOLDOWN_TICKS = 8;

    public MachineGunTower(Tile position) {
        super(160, position, new Color(72, 92, 120), BuildingType.MACHINE_GUN_TOWER,
                MAX_AMMO, DAMAGE, ATTACK_RANGE, FIRE_COOLDOWN_TICKS);
    }

    @Override
    public void render(Graphics g, int tileSize) {
        int x = position.getX() * tileSize;
        int y = position.getY() * tileSize;
        int centerX = x + tileSize / 2;
        int centerY = y + tileSize / 2;

        g.setColor(new Color(45, 52, 61));
        g.fillRect(x + 3, y + 8, tileSize - 6, tileSize - 11);
        g.setColor(color);
        g.fillOval(x + 7, y + 4, tileSize - 14, tileSize - 14);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(32, 35, 40));
        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double angle = getAimAngle();
        int barrelLength = tileSize / 2;
        g2.drawLine(centerX, centerY,
                centerX + (int) Math.round(Math.cos(angle) * barrelLength),
                centerY + (int) Math.round(Math.sin(angle) * barrelLength));
        g2.dispose();

        renderSupplyAndAmmo(g, x, y, tileSize);
    }
}
