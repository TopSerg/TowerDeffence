package towerdefence.combat;

import towerdefence.building.Building;
import towerdefence.building.House;
import towerdefence.world.Entity;
import towerdefence.world.GameMap;
import towerdefence.world.Pathfinder;
import towerdefence.world.Tile;

import java.awt.*;
import java.util.List;

/** Противник динамически ищет кратчайший путь к главному зданию. */
public class Enemy extends Entity {
    private static final int PATH_RECALC_INTERVAL = 30;
    private static final int ATTACK_COOLDOWN_TICKS = 42;

    private final GameMap map;
    private final House mainBuilding;
    private final Pathfinder pathfinder;
    private final int maxHealth;
    private final int attackDamage;
    private final EnemyType enemyType;

    private List<Tile> path;
    private int currentPathIndex;
    private int pathRecalcTicks;
    private int attackCooldown;
    private int attackEffectTicks;
    private float realX;
    private float realY;
    private Building attackTarget;
    private boolean hasAttackedMainBuilding;
    private int slowTicks;
    private float slowMultiplier = 1.0f;

    public Enemy(int health, float speed, int attackDamage, GameMap map,
                 Tile spawnTile, House mainBuilding, EnemyType enemyType) {
        super(health, speed, requireSpawn(spawnTile),
                enemyType == null ? EnemyType.NORMAL.getColor() : enemyType.getColor());
        if (map == null || mainBuilding == null) {
            throw new IllegalArgumentException("Enemy требует карту и главное здание");
        }
        this.map = map;
        this.mainBuilding = mainBuilding;
        this.pathfinder = new Pathfinder(map);
        this.maxHealth = health;
        this.attackDamage = attackDamage;
        this.enemyType = enemyType == null ? EnemyType.NORMAL : enemyType;
        this.realX = spawnTile.getX();
        this.realY = spawnTile.getY();
        requestRepath();
    }

    private static Tile requireSpawn(Tile spawnTile) {
        if (spawnTile == null) throw new IllegalArgumentException("Точка спавна не может быть null");
        return spawnTile;
    }

    @Override
    public void render(Graphics g, int tileSize) {
        if (!isAlive) return;

        int x = Math.round(realX * tileSize);
        int y = Math.round(realY * tileSize);

        if (slowTicks > 0) {
            g.setColor(new Color(85, 225, 245, 150));
            g.fillOval(x, y, tileSize, tileSize);
        }
        if (attackEffectTicks > 0) {
            g.setColor(new Color(255, 170, 80, 150));
            g.fillOval(x - 2, y - 2, tileSize + 4, tileSize + 4);
        }
        Graphics2D robot = (Graphics2D) g.create();
        robot.setStroke(new BasicStroke(2f));
        switch (enemyType) {
            case FAST:
                robot.setColor(new Color(55, 35, 18));
                int[] fx = {x + tileSize / 2, x + tileSize - 3, x + 5};
                int[] fy = {y + 3, y + tileSize - 5, y + tileSize - 5};
                robot.fillPolygon(fx, fy, 3);
                robot.setColor(color);
                int[] fxi = {x + tileSize / 2, x + tileSize - 8, x + 10};
                int[] fyi = {y + 7, y + tileSize - 9, y + tileSize - 9};
                robot.fillPolygon(fxi, fyi, 3);
                robot.setColor(new Color(255, 225, 120));
                robot.drawLine(x + 8, y + tileSize / 2, x + tileSize - 8, y + tileSize / 2);
                break;
            case ARMORED:
                robot.setColor(new Color(35, 45, 55));
                robot.fillRoundRect(x + 2, y + 2, tileSize - 4, tileSize - 4, 5, 5);
                robot.setColor(color);
                robot.fillRect(x + 7, y + 7, tileSize - 14, tileSize - 14);
                robot.setColor(new Color(185, 210, 225));
                robot.drawRect(x + 5, y + 5, tileSize - 10, tileSize - 10);
                robot.drawLine(x + 6, y + tileSize / 2, x + tileSize - 6, y + tileSize / 2);
                break;
            case HEAVY:
                robot.setColor(new Color(48, 20, 30));
                robot.fillRoundRect(x + 1, y + 1, tileSize - 2, tileSize - 2, 8, 8);
                robot.setColor(color);
                robot.fillRoundRect(x + 5, y + 5, tileSize - 10, tileSize - 10, 7, 7);
                robot.setColor(new Color(235, 175, 95));
                robot.drawRect(x + 8, y + 8, tileSize - 16, tileSize - 16);
                robot.fillRect(x + 5, y + tileSize / 2 - 2, tileSize - 10, 4);
                break;
            case NORMAL:
            default:
                robot.setColor(new Color(45, 18, 22));
                robot.fillOval(x + 2, y + 2, tileSize - 4, tileSize - 4);
                robot.setColor(color);
                robot.fillOval(x + 5, y + 5, tileSize - 10, tileSize - 10);
                robot.setColor(new Color(245, 205, 130));
                robot.drawLine(x + 8, y + tileSize / 2, x + tileSize - 8, y + tileSize / 2);
                robot.drawLine(x + tileSize / 2, y + 8, x + tileSize / 2, y + tileSize - 8);
                break;
        }
        robot.dispose();

        int barWidth = tileSize - 8;
        int healthWidth = Math.max(0, Math.min(barWidth, barWidth * health / Math.max(1, maxHealth)));
        g.setColor(new Color(55, 20, 20));
        g.fillRect(x + 4, y + 1, barWidth, 4);
        g.setColor(new Color(225, 65, 70));
        g.fillRect(x + 4, y + 1, healthWidth, 4);
    }

    @Override
    public void update() {
        if (!isAlive || !mainBuilding.isAlive()) return;
        if (attackCooldown > 0) attackCooldown--;
        if (attackEffectTicks > 0) attackEffectTicks--;
        if (pathRecalcTicks > 0) pathRecalcTicks--;
        if (slowTicks > 0) {
            slowTicks--;
            if (slowTicks == 0) slowMultiplier = 1.0f;
        }

        if (attackTarget != null) {
            if (!attackTarget.isAlive()) {
                attackTarget = null;
                requestRepath();
            } else if (isAdjacentToBuilding(attackTarget)) {
                attackCurrentTarget();
                return;
            } else {
                attackTarget = null;
                requestRepath();
            }
        }

        if (path == null || currentPathIndex >= path.size()) {
            if (isAdjacentTo(mainBuilding.getPosition())) {
                attackTarget = mainBuilding;
                attackCurrentTarget();
                return;
            }
            calculatePath();
        } else if (pathRecalcTicks <= 0 && isAtTileCenter()) {
            calculatePath();
        }

        if (path == null || currentPathIndex >= path.size()) return;
        Tile next = path.get(currentPathIndex);

        if (next.hasBuilding()) {
            snapToCurrentTile();
            attackTarget = next.getBuilding();
            path = null;
            attackCurrentTarget();
            return;
        }
        if (!next.isPassable()) {
            snapToCurrentTile();
            requestRepath();
            return;
        }

        moveTowards(next);
    }

    private void calculatePath() {
        if (!isAtTileCenter()) return;

        List<Tile> direct = pathfinder.findEnemyPathToAdjacent(position, mainBuilding.getPosition());
        if (direct != null) {
            path = direct;
            currentPathIndex = Math.min(1, path.size());
            pathRecalcTicks = PATH_RECALC_INTERVAL;
            if (path.size() <= 1 && isAdjacentTo(mainBuilding.getPosition())) attackTarget = mainBuilding;
            return;
        }

        path = pathfinder.findEnemyBreachPath(position, mainBuilding.getPosition());
        currentPathIndex = path == null ? 0 : Math.min(1, path.size());
        pathRecalcTicks = PATH_RECALC_INTERVAL;
    }

    private void moveTowards(Tile next) {
        float dx = next.getX() - realX;
        float dy = next.getY() - realY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        float effectiveSpeed = speed * slowMultiplier;
        if (distance <= effectiveSpeed || distance < 0.0001f) {
            realX = next.getX();
            realY = next.getY();
            position = next;
            currentPathIndex++;
            if (currentPathIndex >= path.size() && isAdjacentTo(mainBuilding.getPosition())) {
                attackTarget = mainBuilding;
            }
        } else {
            realX += dx / distance * effectiveSpeed;
            realY += dy / distance * effectiveSpeed;
        }
    }

    private void attackCurrentTarget() {
        if (attackTarget == null || !attackTarget.isAlive() || !isAdjacentToBuilding(attackTarget)) return;
        if (attackCooldown > 0) return;

        attackCooldown = ATTACK_COOLDOWN_TICKS;
        attackEffectTicks = 8;
        attackTarget.takeDamage(attackDamage);
        if (attackTarget == mainBuilding) hasAttackedMainBuilding = true;
        if (!attackTarget.isAlive()) {
            attackTarget = null;
            requestRepath();
        }
    }

    private boolean isAdjacentToBuilding(Building building) {
        if (building == null || building.getPosition() == null || position == null) return false;
        int minX = building.getPosition().getX();
        int minY = building.getPosition().getY();
        int maxX = minX + building.getFootprintWidth() - 1;
        int maxY = minY + building.getFootprintHeight() - 1;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (Math.abs(position.getX() - x) + Math.abs(position.getY() - y) == 1) return true;
            }
        }
        return false;
    }

    private boolean isAdjacentTo(Tile tile) {
        return tile != null && position != null
                && Math.abs(position.getX() - tile.getX()) + Math.abs(position.getY() - tile.getY()) == 1;
    }

    private boolean isAtTileCenter() {
        return position != null
                && Math.abs(realX - position.getX()) < 0.001f
                && Math.abs(realY - position.getY()) < 0.001f;
    }

    private void snapToCurrentTile() {
        if (position == null) return;
        realX = position.getX();
        realY = position.getY();
    }

    @Override
    public void takeDamage(int damage) {
        if (damage <= 0 || !isAlive) return;
        int reducedDamage = Math.max(1, damage - enemyType.getFlatArmor());
        super.takeDamage(reducedDamage);
    }

    public void requestRepath() {
        path = null;
        currentPathIndex = 0;
        pathRecalcTicks = 0;
    }

    public boolean hasArrived() { return false; }
    public float getRealX() { return realX; }
    public float getRealY() { return realY; }
    public int getMaxHealth() { return maxHealth; }
    public int getAttackDamage() { return attackDamage; }
    public Building getAttackTarget() { return attackTarget; }
    public boolean hasAttackedMainBuilding() { return hasAttackedMainBuilding; }
    public EnemyType getEnemyType() { return enemyType; }
    public int getScrapReward() { return enemyType.getScrapReward(); }

    public void applySlow(float multiplier, int durationTicks) {
        if (!isAlive || durationTicks <= 0) return;
        float normalized = Math.max(0.20f, Math.min(1.0f, multiplier));
        if (slowTicks <= 0 || normalized < slowMultiplier) slowMultiplier = normalized;
        slowTicks = Math.max(slowTicks, durationTicks);
    }

    public boolean isSlowed() { return slowTicks > 0; }
    public int getSlowTicks() { return slowTicks; }
    public float getSlowMultiplier() { return slowMultiplier; }
}
