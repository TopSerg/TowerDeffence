import java.util.*;

public class Pathfinder {
    private GameMap map;

    public Pathfinder(GameMap map) {
        this.map = map;
    }

    public List<Tile> findPath(Tile start, Tile end) {
        // Если конечная точка непроходима - путь невозможен
        if (!end.isPassable() || end.hasUnit()) {
            return null;
        }

        // Открытый и закрытый списки
        PriorityQueue<Node> openList = new PriorityQueue<>();
        Set<Node> closedList = new HashSet<>();

        // Начальный узел
        Node startNode = new Node(start, null, 0, calculateHeuristic(start, end));
        openList.add(startNode);

        while (!openList.isEmpty()) {
            // Берем узел с наименьшей общей стоимостью
            Node currentNode = openList.poll();

            // Если достигли цели - строим путь
            if (currentNode.tile.equals(end)) {
                return simplifyPath(reconstructPath(currentNode), start, end);
            }

            closedList.add(currentNode);

            // Проверяем всех соседей
            for (Tile neighbor : getNeighbors(currentNode.tile)) {
                Node neighborNode = new Node(neighbor, currentNode,
                        currentNode.gCost + 1, calculateHeuristic(neighbor, end));

                // Пропускаем если уже в закрытом списке
                if (closedList.contains(neighborNode)) continue;

                // Если сосед непроходим (кроме конечной точки) - пропускаем
                if (!neighbor.isPassable() && !neighbor.equals(end)) continue;

                // Если сосед занят юнитом (кроме конечной точки) - пропускаем
                if (neighbor.hasUnit() && !neighbor.equals(end)) continue;

                // Если уже в открытом списке с лучшей стоимостью - пропускаем
                if (openList.contains(neighborNode)) {
                    Node existing = getFromOpenList(openList, neighborNode);
                    if (existing.gCost <= neighborNode.gCost) continue;
                }

                openList.add(neighborNode);
            }
        }

        return null;
    }

    private List<Tile> simplifyPath(List<Tile> rawPath, Tile start, Tile end) {
        if (rawPath == null || rawPath.size() <= 2) return rawPath;

        List<Tile> simplified = new ArrayList<>();
        simplified.add(start); // Всегда начинаем со старта

        int currentIndex = 0;
        while (currentIndex < rawPath.size() - 1) {
            // Ищем самую дальнюю точку, до которой можно пройти по прямой
            int farthestVisible = currentIndex;
            for (int i = currentIndex + 1; i < rawPath.size(); i++) {
                if (hasLineOfSight(rawPath.get(currentIndex), rawPath.get(i))) {
                    farthestVisible = i;
                }
            }

            // Добавляем эту точку в упрощенный путь
            if (farthestVisible > currentIndex) {
                simplified.add(rawPath.get(farthestVisible));
                currentIndex = farthestVisible;
            } else {
                currentIndex++; // На всякий случай
            }
        }

        // Убедимся, что конечная точка есть в пути
        if (!simplified.get(simplified.size() - 1).equals(end)) {
            simplified.add(end);
        }

        return simplified;
    }

    // Проверка прямой видимости между двумя тайлами (алгоритм Брезенхема)
    private boolean hasLineOfSight(Tile start, Tile end) {
        int x0 = start.getX();
        int y0 = start.getY();
        int x1 = end.getX();
        int y1 = end.getY();

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = (x0 < x1) ? 1 : -1;
        int sy = (y0 < y1) ? 1 : -1;
        int err = dx - dy;

        int currentX = x0;
        int currentY = y0;

        while (true) {
            // Пропускаем начальную и конечную точки
            if ((currentX != x0 || currentY != y0) &&
                    (currentX != x1 || currentY != y1)) {

                Tile tile = map.getTile(currentX, currentY);
                // Если на пути есть непроходимое препятствие - нет прямой видимости
                if (!tile.isPassable() || tile.hasUnit()) {
                    return false;
                }
            }

            if (currentX == x1 && currentY == y1) break;

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                currentX += sx;
            }
            if (e2 < dx) {
                err += dx;
                currentY += sy;
            }
        }

        return true;
    }

    private List<Tile> reconstructPath(Node endNode) {
        List<Tile> path = new ArrayList<>();
        Node current = endNode;

        while (current != null) {
            path.add(0, current.tile); // Добавляем в начало
            current = current.parent;
        }

        return path;
    }

    private List<Tile> getNeighbors(Tile tile) {
        List<Tile> neighbors = new ArrayList<>();
        int x = tile.getX();
        int y = tile.getY();

        // 4-стороннее движение (можно расширить до 8-стороннего)
        addIfValid(neighbors, x + 1, y);
        addIfValid(neighbors, x - 1, y);
        addIfValid(neighbors, x, y + 1);
        addIfValid(neighbors, x, y - 1);

        return neighbors;
    }

    private void addIfValid(List<Tile> list, int x, int y) {
        Tile tile = map.getTile(x, y);
        if (tile != null) {
            list.add(tile);
        }
    }

    private int calculateHeuristic(Tile a, Tile b) {
        // Манхэттенское расстояние
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
    }

    private Node getFromOpenList(PriorityQueue<Node> openList, Node node) {
        for (Node n : openList) {
            if (n.equals(node)) return n;
        }
        return null;
    }

    // Внутренний класс для узлов A*
    private class Node implements Comparable<Node> {
        public Tile tile;
        public Node parent;
        public int gCost; // Стоимость от начала
        public int hCost; // Эвристическая стоимость до цели
        public int fCost; // Общая стоимость (g + h)

        public Node(Tile tile, Node parent, int gCost, int hCost) {
            this.tile = tile;
            this.parent = parent;
            this.gCost = gCost;
            this.hCost = hCost;
            this.fCost = gCost + hCost;
        }

        @Override
        public int compareTo(Node other) {
            return Integer.compare(this.fCost, other.fCost);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Node node = (Node) obj;
            return tile.equals(node.tile);
        }

        @Override
        public int hashCode() {
            return tile.hashCode();
        }
    }
}