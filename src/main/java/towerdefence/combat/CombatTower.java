package towerdefence.combat;

import towerdefence.building.Building;
import towerdefence.building.BuildingType;
import towerdefence.world.Tile;

import java.awt.*;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Общая боевая логика башен, использующих патроны и устанавливаемые модули. */
public abstract class CombatTower extends Building {
    private static final float BASE_MAX_HEAT = 100.0f;
    private static final float BASE_COOLING_PER_TICK = 0.45f;
    private static final int OVERHEAT_LOCK_TICKS = 75;

    private final int maxAmmo;
    private final int damage;
    private final double attackRange;
    private final int baseFireCooldownTicks;
    private final EnumSet<TowerModuleType> installedModules = EnumSet.noneOf(TowerModuleType.class);

    protected int ammo;
    protected boolean supplied;
    protected Enemy currentTarget;
    private int fireCooldown;
    private int shotEffectTicks;
    private float shotTargetX;
    private float shotTargetY;
    private float heat;
    private boolean overheated;
    private int overheatLockTicks;

    protected CombatTower(int health, Tile position, Color color, BuildingType type,
                          int maxAmmo, int damage, double attackRange, int fireCooldownTicks) {
        super(health, 0, position, color);
        this.type = type;
        this.maxAmmo = maxAmmo;
        this.damage = damage;
        this.attackRange = attackRange;
        this.baseFireCooldownTicks = fireCooldownTicks;
    }

    public int addAmmo(int amount) {
        if (amount <= 0) return 0;
        int accepted = Math.min(amount, maxAmmo - ammo);
        ammo += accepted;
        return accepted;
    }

    public int getAmmo() { return ammo; }
    public int getMaxAmmo() { return maxAmmo; }
    public int getDamage() { return damage; }
    public double getAttackRange() { return attackRange; }
    public int getBaseFireCooldownTicks() { return baseFireCooldownTicks; }
    public int getFireCooldownTicks() { return getEffectiveFireCooldownTicks(); }
    public double getShotsPerSecond() { return 60.0 / Math.max(1, getEffectiveFireCooldownTicks()); }
    public boolean isSupplied() { return supplied; }
    public void setSupplied(boolean supplied) { this.supplied = supplied; }
    public Enemy getCurrentTarget() { return currentTarget; }
    public float getHeat() { return heat; }
    public float getMaxHeat() {
        float result = BASE_MAX_HEAT;
        for (TowerModuleType module : installedModules) result += module.getMaxHeatBonus();
        return result;
    }
    public float getCoolingPerTick() {
        float result = BASE_COOLING_PER_TICK;
        for (TowerModuleType module : installedModules) result += module.getCoolingBonus();
        return result;
    }
    public float getHeatPerShot() {
        float result = 0.0f;
        for (TowerModuleType module : installedModules) result += module.getHeatPerShot();
        return result;
    }
    public boolean isOverheated() { return overheated; }
    public int getOverheatLockTicks() { return overheatLockTicks; }
    public Set<TowerModuleType> getInstalledModules() {
        return Collections.unmodifiableSet(EnumSet.copyOf(installedModules));
    }

    public int getEffectiveFireCooldownTicks() {
        double multiplier = 1.0;
        for (TowerModuleType module : installedModules) multiplier *= module.getCooldownMultiplier();
        return Math.max(3, (int) Math.round(baseFireCooldownTicks * multiplier));
    }

    public boolean canInstallModule(TowerModuleType module) {
        if (module == null || installedModules.contains(module)) return false;
        TowerModuleType prerequisite = module.getPrerequisite();
        return prerequisite == null || installedModules.contains(prerequisite);
    }

    public String getModuleFailureReason(TowerModuleType module) {
        if (module == null) return "Модуль не выбран";
        if (installedModules.contains(module)) return "Модуль уже установлен";
        TowerModuleType prerequisite = module.getPrerequisite();
        if (prerequisite != null && !installedModules.contains(prerequisite)) {
            return "Сначала установите: " + prerequisite.getDisplayName();
        }
        return "Модуль сейчас установить нельзя";
    }

    public boolean installModule(TowerModuleType module) {
        if (!canInstallModule(module)) return false;
        installedModules.add(module);
        return true;
    }

    public boolean isTargetInRange(Enemy enemy) {
        if (enemy == null || !enemy.isAlive() || enemy.hasArrived() || position == null) return false;
        double dx = enemy.getRealX() - position.getX();
        double dy = enemy.getRealY() - position.getY();
        return dx * dx + dy * dy <= attackRange * attackRange;
    }

    public void updateCombat(List<Enemy> enemies) {
        if (!isAlive) return;

        if (!isTargetInRange(currentTarget) || shouldRetarget(currentTarget, enemies)) {
            currentTarget = findTarget(enemies);
        }

        if (currentTarget == null || ammo <= 0 || fireCooldown > 0 || overheated) return;

        ammo--;
        fireCooldown = getEffectiveFireCooldownTicks();
        shotEffectTicks = getShotEffectDurationTicks();
        shotTargetX = currentTarget.getRealX();
        shotTargetY = currentTarget.getRealY();
        hitTarget(currentTarget);

        float heatPerShot = getHeatPerShot();
        if (heatPerShot > 0.0f) {
            heat = Math.min(getMaxHeat(), heat + heatPerShot);
            if (heat >= getMaxHeat() - 0.001f) {
                overheated = true;
                overheatLockTicks = OVERHEAT_LOCK_TICKS;
            }
        }

        if (!currentTarget.isAlive()) currentTarget = null;
    }

    protected boolean shouldRetarget(Enemy current, List<Enemy> enemies) {
        return false;
    }

    protected Enemy findTarget(List<Enemy> enemies) {
        Enemy nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        if (enemies == null) return null;

        for (Enemy enemy : enemies) {
            if (!isTargetInRange(enemy)) continue;
            double distanceSquared = distanceSquared(enemy);
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = enemy;
            }
        }
        return nearest;
    }

    protected double distanceSquared(Enemy enemy) {
        double dx = enemy.getRealX() - position.getX();
        double dy = enemy.getRealY() - position.getY();
        return dx * dx + dy * dy;
    }

    protected void hitTarget(Enemy target) {
        target.takeDamage(damage);
    }

    protected int getShotEffectDurationTicks() { return 4; }
    protected Color getShotColor() { return new Color(255, 228, 105, 220); }
    protected float getShotWidth() { return 2.5f; }

    public void renderShotEffect(Graphics g, int tileSize) {
        if (shotEffectTicks <= 0 || position == null) return;

        int startX = position.getX() * tileSize + tileSize / 2;
        int startY = position.getY() * tileSize + tileSize / 2;
        int endX = Math.round((shotTargetX + 0.5f) * tileSize);
        int endY = Math.round((shotTargetY + 0.5f) * tileSize);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(getShotColor());
        g2.setStroke(new BasicStroke(getShotWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(startX, startY, endX, endY);
        g2.setColor(new Color(255, 255, 255, 220));
        g2.fillOval(endX - 3, endY - 3, 7, 7);
        g2.dispose();
    }

    protected double getAimAngle() {
        if (currentTarget == null || !currentTarget.isAlive()) return -Math.PI / 2.0;
        return Math.atan2(currentTarget.getRealY() - position.getY(),
                currentTarget.getRealX() - position.getX());
    }

    protected void renderSupplyAndAmmo(Graphics g, int x, int y, int tileSize) {
        g.setColor(supplied ? new Color(76, 220, 120) : new Color(230, 80, 70));
        g.fillOval(x + tileSize - 9, y + 3, 6, 6);

        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(9, tileSize / 4)));
        String ammoText = Integer.toString(ammo);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(ammoText, x + (tileSize - metrics.stringWidth(ammoText)) / 2, y + tileSize - 3);

        if (!installedModules.isEmpty()) {
            int barX = x + 4;
            int barY = y + 2;
            int barWidth = tileSize - 12;
            int heatWidth = Math.max(0, Math.min(barWidth,
                    Math.round(barWidth * heat / Math.max(1.0f, getMaxHeat()))));
            g.setColor(new Color(45, 45, 50, 220));
            g.fillRect(barX, barY, barWidth, 3);
            g.setColor(overheated ? new Color(255, 65, 45) : new Color(255, 150, 55));
            g.fillRect(barX, barY, heatWidth, 3);

            int moduleX = x + 3;
            int moduleY = y + 7;
            for (TowerModuleType module : TowerModuleType.values()) {
                if (!installedModules.contains(module)) continue;
                g.setColor(module == TowerModuleType.COOLING_I
                        ? new Color(80, 210, 235)
                        : new Color(245, 210, 75));
                g.fillRect(moduleX, moduleY, 4, 4);
                moduleX += 5;
            }
        }
    }

    @Override
    public void update() {
        if (fireCooldown > 0) fireCooldown--;
        if (shotEffectTicks > 0) shotEffectTicks--;
        if (currentTarget != null && !currentTarget.isAlive()) currentTarget = null;

        if (heat > 0.0f) heat = Math.max(0.0f, heat - getCoolingPerTick());
        if (overheatLockTicks > 0) overheatLockTicks--;
        if (overheated && overheatLockTicks <= 0 && heat <= getMaxHeat() * 0.35f) {
            overheated = false;
        }
    }
}
