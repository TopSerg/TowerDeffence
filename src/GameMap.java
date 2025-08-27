import java.util.List;
import java.util.Vector;

public class GameMap {
    private Tile[][] tiles;
    private Vector<Unit> units;
    private Vector<Building> buildings;
    private int width, height;

    public GameMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.tiles = new Tile[width][height];
        generateMap(); // Генерация случайной карты
    }

    private void generateMap() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Простая генерация: земля + случайные ресурсы
                TileType type = TileType.DIRT;  // По умолчанию земля
                Resource resource = null;

                // 10% шанс добавить ресурс
                if (Math.random() < 0.1) {
                    resource = new Resource(ResourceType.METAL, 1);
                }

                tiles[x][y] = new Tile(x, y, type, resource, null, true);
            }
        }

        // Добавим воду по краям для примера
        for (int x = 0; x < width; x++) {
            tiles[x][0].setType(TileType.WATER);
            tiles[x][height - 1].setType(TileType.WATER);
            tiles[x][0].setPassable(false);
            tiles[x][height - 1].setPassable(false);
        }
        for (int y = 0; y < height; y++) {
            tiles[0][y].setType(TileType.WATER);
            tiles[width - 1][y].setType(TileType.WATER);
        }
    }

    public Tile getTile(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return null;
        return tiles[x][y];
    }
    public List<Tile> findPath(Tile start, Tile end) {return null;}// A* алгоритм

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public Vector<Building> getBuildings() {
        return buildings;
    }

    public Vector<Unit> getUnits() {
        return units;
    }
}