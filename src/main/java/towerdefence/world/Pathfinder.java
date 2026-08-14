package towerdefence.world;

import towerdefence.building.Building;
import towerdefence.building.Conveyor;
import towerdefence.building.Wall;
import towerdefence.combat.MachineGunTower;

import java.util.*;

public class Pathfinder {
    private final GameMap map;

    public Pathfinder(GameMap map) {
        this.map = map;
    }

    /** Обычный путь для рабочего: здания и другие юниты считаются препятствиями. */
    public List<Tile> findPath(Tile start, Tile end) {
        List<Tile> raw = findRawPath(start, end, false, false);
        return simplifyPath(raw, start, end);
    }

    /** Кратчайший клеточный путь врага к любой свободной клетке рядом со зданием. */
    public List<Tile> findEnemyPathToAdjacent(Tile start, Tile targetBuildingTile) {
        if (start == null || targetBuildingTile == null) return null;

        List<Tile> best = null;
        for (Tile candidate : getNeighbors(targetBuildingTile)) {
            if (candidate == null || !candidate.isPassable() || candidate.hasBuilding()) continue;
            List<Tile> path = findRawPath(start, candidate, true, false);
            if (path != null && (best == null || path.size() < best.size())) {
                best = path;
            }
        }
        return best;
    }

    /**
     * Ищет наиболее дешёвый путь прорыва к зданию. Вода непроходима, а клетки
     * с постройками разрешены, но имеют повышенную цену. Поэтому враг выберет
     * короткий проход и будет ломать первую встретившуюся постройку.
     */
    public List<Tile> findEnemyBreachPath(Tile start, Tile targetBuildingTile) {
        if (start == null || targetBuildingTile == null) return null;

        PriorityQueue<WeightedNode> open = new PriorityQueue<>();
        Map<Tile, Integer> bestCost = new HashMap<>();
        Map<Tile, Tile> parent = new HashMap<>();

        open.add(new WeightedNode(start, 0));
        bestCost.put(start, 0);

        while (!open.isEmpty()) {
            WeightedNode current = open.poll();
            if (current.cost != bestCost.getOrDefault(current.tile, Integer.MAX_VALUE)) continue;
            if (current.tile == targetBuildingTile) {
                return reconstruct(parent, start, targetBuildingTile);
            }

            for (Tile neighbor : getNeighbors(current.tile)) {
                if (neighbor == null || neighbor.getType() == TileType.WATER) continue;

                int stepCost = 1;
                if (neighbor.hasBuilding() && neighbor != targetBuildingTile) {
                    stepCost += getBuildingBreachCost(neighbor.getBuilding());
                }

                int newCost = current.cost + stepCost;
                if (newCost < bestCost.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    bestCost.put(neighbor, newCost);
                    parent.put(neighbor, current.tile);
                    open.add(new WeightedNode(neighbor, newCost));
                }
            }
        }
        return null;
    }

    private int getBuildingBreachCost(Building building) {
        if (building == null) return 0;
        if (building instanceof Conveyor) return 8;
        if (building instanceof MachineGunTower) return 16;
        if (building instanceof Wall) return 24;
        return 20;
    }

    private List<Tile> findRawPath(Tile start, Tile end, boolean ignoreUnits, boolean allowBlockedEnd) {
        if (start == null || end == null) return null;
        if (!allowBlockedEnd && isMovementBlocked(end)) return null;

        PriorityQueue<Node> open = new PriorityQueue<>();
        Map<Tile, Integer> bestG = new HashMap<>();
        Map<Tile, Tile> parent = new HashMap<>();

        open.add(new Node(start, 0, heuristic(start, end)));
        bestG.put(start, 0);

        while (!open.isEmpty()) {
            Node current = open.poll();
            if (current.gCost != bestG.getOrDefault(current.tile, Integer.MAX_VALUE)) continue;
            if (current.tile == end) return reconstruct(parent, start, end);

            for (Tile neighbor : getNeighbors(current.tile)) {
                if (neighbor == null) continue;
                if (neighbor != end && isMovementBlocked(neighbor)) continue;
                if (!ignoreUnits && neighbor != end && neighbor.hasUnit()) continue;
                if (neighbor == end && !allowBlockedEnd && isMovementBlocked(neighbor)) continue;

                int tentativeG = current.gCost + 1;
                if (tentativeG < bestG.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    bestG.put(neighbor, tentativeG);
                    parent.put(neighbor, current.tile);
                    open.add(new Node(neighbor, tentativeG, heuristic(neighbor, end)));
                }
            }
        }
        return null;
    }

    private List<Tile> reconstruct(Map<Tile, Tile> parent, Tile start, Tile end) {
        LinkedList<Tile> path = new LinkedList<>();
        Tile current = end;
        path.addFirst(current);
        while (current != start) {
            current = parent.get(current);
            if (current == null) return null;
            path.addFirst(current);
        }
        return path;
    }

    private List<Tile> simplifyPath(List<Tile> rawPath, Tile start, Tile end) {
        if (rawPath == null || rawPath.size() <= 2) return rawPath;

        List<Tile> simplified = new ArrayList<>();
        simplified.add(start);
        int currentIndex = 0;

        while (currentIndex < rawPath.size() - 1) {
            int farthestVisible = currentIndex + 1;
            for (int i = currentIndex + 1; i < rawPath.size(); i++) {
                if (hasLineOfSight(rawPath.get(currentIndex), rawPath.get(i))) {
                    farthestVisible = i;
                } else {
                    break;
                }
            }
            simplified.add(rawPath.get(farthestVisible));
            currentIndex = farthestVisible;
        }

        if (simplified.get(simplified.size() - 1) != end) simplified.add(end);
        return simplified;
    }

    private boolean hasLineOfSight(Tile start, Tile end) {
        int x0 = start.getX();
        int y0 = start.getY();
        int x1 = end.getX();
        int y1 = end.getY();
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;

        while (true) {
            if ((x != x0 || y != y0) && (x != x1 || y != y1)) {
                Tile tile = map.getTile(x, y);
                // Не выпрямляем участок через постройку: проходимый конвейер
                // должен остаться явной точкой маршрута.
                if (tile == null || isMovementBlocked(tile) || tile.hasBuilding() || tile.hasUnit()) return false;
            }
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }
        return true;
    }

    private boolean isMovementBlocked(Tile tile) {
        return tile == null
                || !tile.isPassable()
                || (tile.hasBuilding() && tile.getBuilding().blocksMovement());
    }

    private List<Tile> getNeighbors(Tile tile) {
        if (tile == null) return Collections.emptyList();
        List<Tile> result = new ArrayList<>(4);
        addIfPresent(result, tile.getX() + 1, tile.getY());
        addIfPresent(result, tile.getX() - 1, tile.getY());
        addIfPresent(result, tile.getX(), tile.getY() + 1);
        addIfPresent(result, tile.getX(), tile.getY() - 1);
        return result;
    }

    private void addIfPresent(List<Tile> tiles, int x, int y) {
        Tile tile = map.getTile(x, y);
        if (tile != null) tiles.add(tile);
    }

    private int heuristic(Tile a, Tile b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
    }

    private static class Node implements Comparable<Node> {
        private final Tile tile;
        private final int gCost;
        private final int fCost;

        private Node(Tile tile, int gCost, int hCost) {
            this.tile = tile;
            this.gCost = gCost;
            this.fCost = gCost + hCost;
        }

        @Override
        public int compareTo(Node other) {
            return Integer.compare(fCost, other.fCost);
        }
    }

    private static class WeightedNode implements Comparable<WeightedNode> {
        private final Tile tile;
        private final int cost;

        private WeightedNode(Tile tile, int cost) {
            this.tile = tile;
            this.cost = cost;
        }

        @Override
        public int compareTo(WeightedNode other) {
            return Integer.compare(cost, other.cost);
        }
    }
}
