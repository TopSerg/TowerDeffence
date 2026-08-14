package towerdefence.building;

import towerdefence.world.Direction;
import towerdefence.world.Tile;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Прототип фабрики: снаружи 3x3, внутри 9x9. */
public class Workshop extends Building {
    public static final int WIDTH_TILES = 3;
    public static final int HEIGHT_TILES = 3;
    public static final int INTERIOR_SCALE = 3;
    public static final int INTERIOR_SIZE = 9;
    public static final int MAX_HEALTH = 900;

    private final int[][] sectorDamage = new int[3][3];
    private final List<FactoryPort> ports = new ArrayList<>();
    private final WorkshopInterior interior = new WorkshopInterior(INTERIOR_SIZE, INTERIOR_SIZE);

    public Workshop(Tile position) {
        super(MAX_HEALTH, 0, position, new Color(98, 108, 140));
        this.type = BuildingType.WORKSHOP;
    }

    @Override
    public int getFootprintWidth() { return WIDTH_TILES; }

    @Override
    public int getFootprintHeight() { return HEIGHT_TILES; }

    public int getInteriorWidth() { return interior.getWidth(); }
    public int getInteriorHeight() { return interior.getHeight(); }
    public WorkshopInterior getInterior() { return interior; }

    public boolean containsWorldTile(Tile tile) {
        if (tile == null || position == null) return false;
        return tile.getX() >= position.getX() && tile.getX() < position.getX() + WIDTH_TILES
                && tile.getY() >= position.getY() && tile.getY() < position.getY() + HEIGHT_TILES;
    }

    public int toSectorColumn(int worldX) {
        return Math.max(0, Math.min(2, worldX - position.getX()));
    }

    public int toSectorRow(int worldY) {
        return Math.max(0, Math.min(2, worldY - position.getY()));
    }

    public void registerPort(FactoryPort port) {
        if (port == null || ports.contains(port)) return;
        ports.add(port);
        // Gateway является частью оболочки Workshop и имеет приоритет над временной
        // внутренней раскладкой. Внутренние конвейеры пока бесплатные/технические,
        // поэтому при достройке нового порта освобождаем его три граничные клетки.
        for (Point cell : getGatewayCells(port)) {
            interior.removeConveyor(cell.x, cell.y);
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

    /**
     * Возвращает три внутренние граничные клетки, соответствующие конкретной внешней клетке порта.
     * Так сохраняется общее правило масштаба: 1 внешняя клетка = 3 внутренних клетки вдоль стены.
     */
    public List<Point> getGatewayCells(FactoryPort port) {
        if (port == null || port.getWorkshop() != this || port.getAttachedSide() == null
                || port.getPosition() == null || position == null) return Collections.emptyList();

        Direction side = port.getAttachedSide();
        int slot;
        if (side == Direction.UP || side == Direction.DOWN) {
            slot = port.getPosition().getX() - position.getX();
        } else {
            slot = port.getPosition().getY() - position.getY();
        }
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

    public boolean isGatewayCell(int x, int y) {
        for (FactoryPort port : ports) {
            for (Point cell : getGatewayCells(port)) {
                if (cell.x == x && cell.y == y) return true;
            }
        }
        return false;
    }

    public boolean placeInteriorConveyor(int x, int y, Direction direction) {
        if (isGatewayCell(x, y)) return false;
        return interior.placeConveyor(x, y, direction);
    }

    public boolean removeInteriorConveyor(int x, int y) {
        return interior.removeConveyor(x, y);
    }

    public InteriorConveyor getInteriorConveyor(int x, int y) {
        return interior.getConveyor(x, y);
    }

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
        sectorDamage[row][col] = Math.min(100, sectorDamage[row][col] + Math.max(1, amount));
    }

    public int getSectorDamage(int row, int col) {
        if (row < 0 || row >= 3 || col < 0 || col >= 3) return 0;
        return sectorDamage[row][col];
    }

    @Override
    public void takeDamage(int damage) {
        super.takeDamage(damage);
        // Пока что отмечаем центральный сектор как общий уровень износа.
        sectorDamage[1][1] = Math.min(100, sectorDamage[1][1] + Math.max(1, damage / 2));
    }

    @Override
    public void render(Graphics g, int tileSize) {
        if (position == null) return;
        int x = position.getX() * tileSize;
        int y = position.getY() * tileSize;
        int w = getFootprintWidth() * tileSize;
        int h = getFootprintHeight() * tileSize;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(46, 52, 68));
        g2.fillRoundRect(x + 2, y + 2, w - 4, h - 4, 12, 12);
        g2.setColor(new Color(92, 103, 135));
        g2.fillRoundRect(x + 6, y + 8, w - 12, h - 16, 10, 10);
        g2.setColor(new Color(146, 168, 205));
        g2.drawRoundRect(x + 6, y + 8, w - 12, h - 16, 10, 10);

        // Три внешних сектора по каждой оси.
        g2.setColor(new Color(15, 18, 26, 110));
        g2.drawLine(x + tileSize, y + 8, x + tileSize, y + h - 8);
        g2.drawLine(x + tileSize * 2, y + 8, x + tileSize * 2, y + h - 8);
        g2.drawLine(x + 8, y + tileSize, x + w - 8, y + tileSize);
        g2.drawLine(x + 8, y + tileSize * 2, x + w - 8, y + tileSize * 2);

        g2.setColor(new Color(215, 224, 240));
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, tileSize / 3)));
        g2.drawString("Workshop", x + 10, y + 18);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(9, tileSize / 4)));
        g2.drawString("3x3 → 9x9", x + 10, y + 33);
        g2.drawString("Ports: " + ports.size(), x + 10, y + h - 12);

        int barWidth = w - 12;
        int healthWidth = Math.max(0, barWidth * health / MAX_HEALTH);
        g2.setColor(new Color(28, 32, 40));
        g2.fillRect(x + 6, y + 2, barWidth, 5);
        g2.setColor(new Color(105, 225, 125));
        g2.fillRect(x + 6, y + 2, healthWidth, 5);
        g2.dispose();
    }

    @Override
    public void update() { }
}
