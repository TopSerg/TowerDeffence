package towerdefence.building;

import towerdefence.resource.ResourceType;
import towerdefence.world.Direction;
import towerdefence.world.Tile;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Фабричная оболочка 3x3 с отдельным физическим интерьером 9x9. */
public class Workshop extends Building {
    public static final int WIDTH_TILES = 3;
    public static final int HEIGHT_TILES = 3;
    public static final int INTERIOR_SCALE = 3;
    public static final int INTERIOR_SIZE = 9;
    public static final int MAX_HEALTH = 900;

    private final int[][] sectorDamage = new int[3][3];
    private final List<FactoryPort> ports = new ArrayList<>();
    private final WorkshopInterior interior = new WorkshopInterior(INTERIOR_SIZE, INTERIOR_SIZE);
    private final List<WorkshopItem> interiorItems = new ArrayList<>();
    private final boolean[][] reservedInterior = new boolean[INTERIOR_SIZE][INTERIOR_SIZE];
    private boolean ruined;

    public Workshop(Tile position) {
        super(MAX_HEALTH, 0, position, new Color(98, 108, 140));
        this.type = BuildingType.WORKSHOP;
    }

    @Override public int getFootprintWidth() { return WIDTH_TILES; }
    @Override public int getFootprintHeight() { return HEIGHT_TILES; }

    public int getInteriorWidth() { return interior.getWidth(); }
    public int getInteriorHeight() { return interior.getHeight(); }
    public WorkshopInterior getInterior() { return interior; }
    public int getMaxHealth() { return MAX_HEALTH; }
    public boolean isRuined() { return ruined; }
    public boolean isOperational() { return !ruined && health > 0 && isAlive; }
    public List<WorkshopItem> getInteriorItems() { return Collections.unmodifiableList(interiorItems); }

    public WorkshopItem getInteriorItemAt(int x, int y) {
        for (WorkshopItem item : interiorItems) {
            if (item.getX() == x && item.getY() == y) return item;
        }
        return null;
    }

    public boolean removeInteriorItem(WorkshopItem item) {
        return item != null && interiorItems.remove(item);
    }

    /** Используется станками для физической выдачи продукта на существующий внутренний конвейер. */
    public boolean spawnInteriorItem(ResourceType type, int x, int y) {
        if (type == null || !interior.contains(x, y) || getInteriorItemAt(x, y) != null) return false;
        InteriorConveyor conveyor = interior.getConveyor(x, y);
        if (conveyor == null || !conveyor.isOperational()) return false;
        interiorItems.add(new WorkshopItem(type, x, y, null));
        return true;
    }

    public boolean reserveInteriorCell(int x, int y) {
        if (!interior.contains(x, y) || isGatewayCell(x, y) || isInteriorReserved(x, y)
                || interior.getConveyor(x, y) != null || getInteriorItemAt(x, y) != null) return false;
        reservedInterior[y][x] = true;
        return true;
    }

    public void releaseInteriorReservation(int x, int y) {
        if (interior.contains(x, y)) reservedInterior[y][x] = false;
    }

    public boolean isInteriorReserved(int x, int y) {
        return interior.contains(x, y) && reservedInterior[y][x];
    }

    public boolean containsWorldTile(Tile tile) {
        if (tile == null || position == null) return false;
        return tile.getX() >= position.getX() && tile.getX() < position.getX() + WIDTH_TILES
                && tile.getY() >= position.getY() && tile.getY() < position.getY() + HEIGHT_TILES;
    }

    public int toSectorColumn(int worldX) { return Math.max(0, Math.min(2, worldX - position.getX())); }
    public int toSectorRow(int worldY) { return Math.max(0, Math.min(2, worldY - position.getY())); }

    public void registerPort(FactoryPort port) {
        if (port == null || ports.contains(port)) return;
        ports.add(port);
        for (Point cell : getGatewayCells(port)) {
            interior.removeConveyor(cell.x, cell.y);
            reservedInterior[cell.y][cell.x] = false;
        }
    }

    public void unregisterPort(FactoryPort port) { ports.remove(port); }
    public List<FactoryPort> getPorts() { return Collections.unmodifiableList(ports); }

    public List<FactoryPort> getInputPorts() {
        List<FactoryPort> result = new ArrayList<>();
        for (FactoryPort port : ports) if (port.isInput()) result.add(port);
        return result;
    }

    public List<FactoryPort> getOutputPorts() {
        List<FactoryPort> result = new ArrayList<>();
        for (FactoryPort port : ports) if (!port.isInput()) result.add(port);
        return result;
    }

    /** Три внутренние граничные клетки, соответствующие конкретной внешней клетке порта. */
    public List<Point> getGatewayCells(FactoryPort port) {
        if (port == null || port.getWorkshop() != this || port.getAttachedSide() == null
                || port.getPosition() == null || position == null) return Collections.emptyList();

        Direction side = port.getAttachedSide();
        int slot = (side == Direction.UP || side == Direction.DOWN)
                ? port.getPosition().getX() - position.getX()
                : port.getPosition().getY() - position.getY();
        if (slot < 0 || slot >= 3) return Collections.emptyList();

        List<Point> cells = new ArrayList<>(INTERIOR_SCALE);
        for (int i = 0; i < INTERIOR_SCALE; i++) {
            int offset = slot * INTERIOR_SCALE + i;
            switch (side) {
                case UP: cells.add(new Point(offset, 0)); break;
                case DOWN: cells.add(new Point(offset, INTERIOR_SIZE - 1)); break;
                case LEFT: cells.add(new Point(0, offset)); break;
                case RIGHT: cells.add(new Point(INTERIOR_SIZE - 1, offset)); break;
            }
        }
        return Collections.unmodifiableList(cells);
    }

    public FactoryPort findPortForGatewayCell(int x, int y) {
        for (FactoryPort port : ports) {
            for (Point cell : getGatewayCells(port)) {
                if (cell.x == x && cell.y == y) return port;
            }
        }
        return null;
    }

    public int getGatewayLaneIndex(FactoryPort port, int x, int y) {
        if (port == null) return -1;
        List<Point> cells = getGatewayCells(port);
        for (int i = 0; i < cells.size(); i++) {
            Point cell = cells.get(i);
            if (cell.x == x && cell.y == y) return i;
        }
        return -1;
    }

    public boolean isGatewayCell(int x, int y) { return findPortForGatewayCell(x, y) != null; }

    public boolean placeInteriorConveyor(int x, int y, Direction direction) {
        if (isGatewayCell(x, y) || isInteriorReserved(x, y) || getInteriorItemAt(x, y) != null) return false;
        return interior.placeConveyor(x, y, direction);
    }

    public boolean removeInteriorConveyor(int x, int y) { return interior.removeConveyor(x, y); }
    public InteriorConveyor getInteriorConveyor(int x, int y) { return interior.getConveyor(x, y); }

    /** Один внутренний логистический такт: предмет проходит максимум одну клетку. */
    public void advanceInteriorTransport() {
        if (!isOperational()) return;
        for (WorkshopItem item : new ArrayList<>(interiorItems)) moveInteriorItem(item);
        for (FactoryPort input : getInputPorts()) injectInputItem(input);
    }

    private void moveInteriorItem(WorkshopItem item) {
        if (item == null || !interiorItems.contains(item)) return;

        if (item.getEntrySide() != null) {
            Direction inward = opposite(item.getEntrySide());
            int nx = item.getX() + inward.getDx();
            int ny = item.getY() + inward.getDy();
            InteriorConveyor next = interior.getConveyor(nx, ny);
            if (next != null && next.isOperational() && getInteriorItemAt(nx, ny) == null) {
                item.moveTo(nx, ny);
                item.leaveGateway();
            }
            return;
        }

        InteriorConveyor conveyor = interior.getConveyor(item.getX(), item.getY());
        if (conveyor == null || !conveyor.isOperational()) return;
        Direction direction = conveyor.getDirection();
        int nx = item.getX() + direction.getDx();
        int ny = item.getY() + direction.getDy();
        if (!interior.contains(nx, ny)) return;

        FactoryPort output = findOutputPort(nx, ny, direction);
        if (output != null) {
            if (output.offerInteriorOutput(item.getType())) interiorItems.remove(item);
            return;
        }

        if (isGatewayCell(nx, ny) || isInteriorReserved(nx, ny)) return;
        InteriorConveyor next = interior.getConveyor(nx, ny);
        if (next != null && next.isOperational() && getInteriorItemAt(nx, ny) == null) item.moveTo(nx, ny);
    }

    /** INPUT Gateway выбирает линию по фильтру; ANY обозначается null. */
    private void injectInputItem(FactoryPort input) {
        if (input == null || !input.isInput() || input.getAttachedSide() == null || !isOperational()) return;
        ResourceType type = input.peekInputResource();
        if (type == null) return;
        List<Point> cells = getGatewayCells(input);
        if (cells.isEmpty()) return;

        int start = input.getGatewayCursor();
        Direction inward = opposite(input.getAttachedSide());
        for (int attempt = 0; attempt < cells.size(); attempt++) {
            int lane = (start + attempt) % cells.size();
            if (!input.acceptsLane(lane, type)) continue;
            Point gateway = cells.get(lane);
            int nextX = gateway.x + inward.getDx();
            int nextY = gateway.y + inward.getDy();
            InteriorConveyor first = interior.getConveyor(nextX, nextY);
            if (getInteriorItemAt(gateway.x, gateway.y) != null || first == null || !first.isOperational()) continue;

            ResourceType committed = input.commitInputToInterior();
            if (committed == null) return;
            interiorItems.add(new WorkshopItem(committed, gateway.x, gateway.y, input.getAttachedSide()));
            input.setGatewayCursor(lane + 1);
            return;
        }
    }

    private FactoryPort findOutputPort(int x, int y, Direction travelDirection) {
        for (FactoryPort port : getOutputPorts()) {
            if (port.getAttachedSide() != travelDirection) continue;
            for (Point gateway : getGatewayCells(port)) {
                if (gateway.x == x && gateway.y == y) return port;
            }
        }
        return null;
    }

    private Direction opposite(Direction direction) {
        if (direction == null) return Direction.RIGHT;
        switch (direction) {
            case UP: return Direction.DOWN;
            case DOWN: return Direction.UP;
            case LEFT: return Direction.RIGHT;
            case RIGHT: return Direction.LEFT;
            default: return Direction.RIGHT;
        }
    }

    /** Совместимый старый API: повреждает центральный сектор указанной стороны. */
    public void markSectorDamaged(Direction side, int amount) {
        if (side == null) return;
        int row = 1;
        int col = 1;
        switch (side) {
            case UP: row = 0; break;
            case DOWN: row = 2; break;
            case LEFT: col = 0; break;
            case RIGHT: col = 2; break;
        }
        addSectorDamage(row, col, amount);
    }

    public int getSectorDamage(int row, int col) {
        if (row < 0 || row >= 3 || col < 0 || col >= 3) return 0;
        return sectorDamage[row][col];
    }

    /** Пространственный удар: внешняя точка атаки выбирает конкретную внешнюю клетку/внутренний сектор 3x3. */
    public void takeDamageFromWorldTile(int damage, Tile attackerTile) {
        if (damage <= 0 || position == null || ruined) return;
        int col = 1;
        int row = 1;
        if (attackerTile != null) {
            int ax = attackerTile.getX();
            int ay = attackerTile.getY();
            int nearestX = Math.max(position.getX(), Math.min(position.getX() + WIDTH_TILES - 1, ax));
            int nearestY = Math.max(position.getY(), Math.min(position.getY() + HEIGHT_TILES - 1, ay));
            col = nearestX - position.getX();
            row = nearestY - position.getY();
        }
        applyShellDamage(damage, row, col);
    }

    @Override
    public void takeDamage(int damage) {
        // Источник обычного Entity.takeDamage неизвестен. RoadmapRuntime сопоставит
        // изменение HP с реально атакующим врагом и затем отметит точный сектор.
        applyShellDamage(damage, -1, -1);
    }

    public void markImpactFromWorldTile(Tile attackerTile, int damage) {
        if (attackerTile == null || position == null || damage <= 0) return;
        int ax = attackerTile.getX();
        int ay = attackerTile.getY();
        int nearestX = Math.max(position.getX(), Math.min(position.getX() + WIDTH_TILES - 1, ax));
        int nearestY = Math.max(position.getY(), Math.min(position.getY() + HEIGHT_TILES - 1, ay));
        addSectorDamage(nearestY - position.getY(), nearestX - position.getX(), Math.max(1, damage / 2));
    }

    private void applyShellDamage(int damage, int row, int col) {
        if (damage <= 0 || ruined) return;
        health = Math.max(0, health - damage);
        if (row >= 0 && col >= 0) addSectorDamage(row, col, Math.max(1, damage / 2));
        if (health <= 0) {
            ruined = true;
            // RUINED FACTORY остаётся сущностью на карте: GameState не удалит интерьер.
            isAlive = true;
            for (InteriorConveyor conveyor : interior.getConveyors()) conveyor.damage(100);
        }
    }

    private void addSectorDamage(int row, int col, int amount) {
        if (row < 0 || row >= 3 || col < 0 || col >= 3) return;
        sectorDamage[row][col] = Math.min(100, sectorDamage[row][col] + Math.max(1, amount));
    }

    public void repairShell(int amount) {
        if (amount <= 0) return;
        health = Math.min(MAX_HEALTH, health + amount);
        if (health > 0) ruined = false;
        if (health >= MAX_HEALTH) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) sectorDamage[row][col] = Math.max(0, sectorDamage[row][col] - 1);
            }
        }
    }

    @Override
    public void render(Graphics g, int tileSize) {
        if (position == null) return;
        int x = position.getX() * tileSize;
        int y = position.getY() * tileSize;
        int w = getFootprintWidth() * tileSize;
        int h = getFootprintHeight() * tileSize;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(ruined ? new Color(58, 48, 46) : new Color(46, 52, 68));
        g2.fillRoundRect(x + 2, y + 2, w - 4, h - 4, 12, 12);
        g2.setColor(ruined ? new Color(86, 66, 60) : new Color(92, 103, 135));
        g2.fillRoundRect(x + 6, y + 8, w - 12, h - 16, 10, 10);
        g2.setColor(ruined ? new Color(205, 115, 95) : new Color(146, 168, 205));
        g2.drawRoundRect(x + 6, y + 8, w - 12, h - 16, 10, 10);

        g2.setColor(new Color(15, 18, 26, 110));
        g2.drawLine(x + tileSize, y + 8, x + tileSize, y + h - 8);
        g2.drawLine(x + tileSize * 2, y + 8, x + tileSize * 2, y + h - 8);
        g2.drawLine(x + 8, y + tileSize, x + w - 8, y + tileSize);
        g2.drawLine(x + 8, y + tileSize * 2, x + w - 8, y + tileSize * 2);

        g2.setColor(new Color(215, 224, 240));
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, tileSize / 3)));
        g2.drawString(ruined ? "RUINED FACTORY" : "Workshop", x + 10, y + 18);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(9, tileSize / 4)));
        g2.drawString("3x3 → 9x9", x + 10, y + 33);
        g2.drawString("Ports: " + ports.size() + " · items: " + interiorItems.size(), x + 10, y + h - 12);

        int barWidth = w - 12;
        int healthWidth = Math.max(0, barWidth * health / MAX_HEALTH);
        g2.setColor(new Color(28, 32, 40));
        g2.fillRect(x + 6, y + 2, barWidth, 5);
        g2.setColor(ruined ? new Color(230, 70, 60) : new Color(105, 225, 125));
        g2.fillRect(x + 6, y + 2, healthWidth, 5);
        g2.dispose();
    }

    @Override public void update() { }
}
