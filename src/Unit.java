import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class Unit  extends Entity {
    private Tile targetTile;  // Целевой тайл
    private UnitType type; // Рабочий, солдат, инженер
    private Inventory inventory;
    private Task currentTask; // Текущее задание (сбор, строительство)

    private GameMap map;
    private List<Tile> path;
    private int currentPathIndex;

    private List<Tile> waypoints; // Ключевые точки пути
    private int currentWaypointIndex;
    private Tile intermediateTarget; // Текущая промежуточная цель

    private float realX, realY; // Точные координаты для плавности

//    private Color color;
//    private Tile position;
//    private float speed = 0.1f;

    public Unit(Tile position, UnitType type, Color color, GameMap map) {
        super(10, 0.1f, position, color);
//        this.position = position;
//        this.color = color;
        this.map = map;
        this.type = type;
        this.realX = position.getX();
        this.realY = position.getY();
    }

    public void render(Graphics g, int tileSize) {
        g.setColor(color);
        g.fillOval(
                (int)(realX * tileSize) + 2,
                (int)(realY * tileSize) + 2,
                tileSize - 4, tileSize - 4
        );
        renderPath(g, tileSize);
    }

    // Визуализация waypoints
    public void renderPath(Graphics g, int tileSize) {
        if (waypoints != null) {
            g.setColor(new Color(0, 255, 0, 100));

            // Рисуем линии между waypoints
            for (int i = 0; i < waypoints.size() - 1; i++) {
                Tile current = waypoints.get(i);
                Tile next = waypoints.get(i + 1);

                g.drawLine(
                        current.getX() * tileSize + tileSize/2,
                        current.getY() * tileSize + tileSize/2,
                        next.getX() * tileSize + tileSize/2,
                        next.getY() * tileSize + tileSize/2
                );

                // Рисуем круги на waypoints
                g.fillOval(
                        current.getX() * tileSize + tileSize/2 - 3,
                        current.getY() * tileSize + tileSize/2 - 3,
                        6, 6
                );
            }

            // Последний waypoint
            if (!waypoints.isEmpty()) {
                Tile last = waypoints.get(waypoints.size() - 1);
                g.fillOval(
                        last.getX() * tileSize + tileSize/2 - 3,
                        last.getY() * tileSize + tileSize/2 - 3,
                        6, 6
                );
            }
        }
    }

    public void setTarget(Tile target) {
        Pathfinder pathfinder = new Pathfinder(map);
        this.waypoints = pathfinder.findPath(position, target);
        this.currentWaypointIndex = 0;
        this.targetTile = target;

        if (waypoints != null && !waypoints.isEmpty()) {
            this.intermediateTarget = waypoints.get(0);
        }
    }

    @Override
    public void update() {
        if (intermediateTarget == null) return;

        // Движение к текущей промежуточной цели
        float dx = intermediateTarget.getX() - realX;
        float dy = intermediateTarget.getY() - realY;
        float distance = (float)Math.sqrt(dx*dx + dy*dy);

        if (distance > 0.05f) {
            // Плавное движение по прямой
            realX += (dx / distance) * speed;
            realY += (dy / distance) * speed;
        } else {
            // Достигли waypoint
            move(intermediateTarget);

            // Переходим к следующему waypoint
            currentWaypointIndex++;
            if (currentWaypointIndex < waypoints.size()) {
                intermediateTarget = waypoints.get(currentWaypointIndex);
            } else {
                // Конец пути
                waypoints = null;
                intermediateTarget = null;
                targetTile = null;
                currentWaypointIndex = 0;
            }
        }

        // Проверка на необходимость перерасчета пути
        checkPathValidity();
    }

    private void checkPathValidity() {
        if (waypoints != null && intermediateTarget != null) {
            // Если текущий waypoint стал недоступен - пересчитываем путь
            if (!intermediateTarget.isPassable() || intermediateTarget.hasUnit()) {
                setTarget(targetTile); // Перерасчет
            }
        }
    }

    // Теперь move() синхронизирует логические и фактические координаты
    public void move(Tile newTile) {
        if (position != null) position.setUnit(null);
        this.position = newTile;
        newTile.setUnit(this);
        realX = position.getX();
        realY = position.getY();
    }

    public void baseUpdate() {
    }

    public void collectResource(Resource resource) {}


}