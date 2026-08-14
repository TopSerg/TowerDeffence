package towerdefence.combat;

import towerdefence.world.GameMap;
import towerdefence.world.Tile;
import towerdefence.world.TileType;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Один постоянный маршрут противников. Маршрут строится по ортогональным
 * контрольным точкам, поэтому каждый следующий элемент списка — соседняя клетка.
 */
public class EnemyRoute {
    private final List<Tile> tiles;
    private final Set<Tile> tileSet;

    public EnemyRoute(GameMap map, List<Point> waypoints) {
        if (map == null || waypoints == null || waypoints.size() < 2) {
            throw new IllegalArgumentException("Маршруту нужны карта и минимум две контрольные точки");
        }

        List<Tile> generated = new ArrayList<>();
        Point previous = waypoints.get(0);
        addTile(map, generated, previous.x, previous.y);

        for (int i = 1; i < waypoints.size(); i++) {
            Point next = waypoints.get(i);
            appendSegment(map, generated, previous, next);
            previous = next;
        }

        this.tiles = Collections.unmodifiableList(generated);
        this.tileSet = Collections.unmodifiableSet(new HashSet<>(generated));

        for (Tile tile : generated) {
            tile.setResource(null);
        }
    }

    private void appendSegment(GameMap map, List<Tile> generated, Point from, Point to) {
        if (from.x != to.x && from.y != to.y) {
            throw new IllegalArgumentException("Сегменты маршрута должны быть горизонтальными или вертикальными");
        }

        int dx = Integer.compare(to.x, from.x);
        int dy = Integer.compare(to.y, from.y);
        int x = from.x;
        int y = from.y;

        while (x != to.x || y != to.y) {
            x += dx;
            y += dy;
            addTile(map, generated, x, y);
        }
    }

    private void addTile(GameMap map, List<Tile> generated, int x, int y) {
        Tile tile = map.getTile(x, y);
        if (tile == null || tile.getType() == TileType.WATER) {
            throw new IllegalArgumentException("Маршрут выходит за пределы суши: " + x + ", " + y);
        }
        if (generated.isEmpty() || generated.get(generated.size() - 1) != tile) {
            generated.add(tile);
        }
    }

    public List<Tile> getTiles() {
        return tiles;
    }

    public Tile getStart() {
        return tiles.get(0);
    }

    public Tile getEnd() {
        return tiles.get(tiles.size() - 1);
    }

    public boolean contains(Tile tile) {
        return tileSet.contains(tile);
    }

    public void render(Graphics g, int tileSize) {
        Graphics2D g2 = (Graphics2D) g.create();

        for (Tile tile : tiles) {
            int x = tile.getX() * tileSize;
            int y = tile.getY() * tileSize;
            g2.setColor(new Color(116, 101, 82));
            g2.fillRoundRect(x + 2, y + 2, tileSize - 4, tileSize - 4, 8, 8);
            g2.setColor(new Color(151, 132, 101));
            g2.drawRoundRect(x + 3, y + 3, tileSize - 7, tileSize - 7, 7, 7);
        }

        g2.setColor(new Color(222, 196, 131, 190));
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 1; i < tiles.size(); i++) {
            Tile previous = tiles.get(i - 1);
            Tile current = tiles.get(i);
            int x1 = previous.getX() * tileSize + tileSize / 2;
            int y1 = previous.getY() * tileSize + tileSize / 2;
            int x2 = current.getX() * tileSize + tileSize / 2;
            int y2 = current.getY() * tileSize + tileSize / 2;
            g2.drawLine(x1, y1, x2, y2);
        }

        g2.dispose();
    }
}
