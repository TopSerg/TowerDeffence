package towerdefence.world;

import towerdefence.resource.Resource;
import towerdefence.resource.ResourceType;

import java.util.List;

public class GameMap {
    private final Tile[][] tiles;
    private final Resource[][] initialResources;
    private final int width;
    private final int height;

    public GameMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.tiles = new Tile[width][height];
        this.initialResources = new Resource[width][height];
        generateMap();
        rememberInitialResources();
    }

    private void generateMap() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                TileType type = TileType.DIRT;
                Resource resource = null;
                double roll = Math.random();
                if (roll < 0.075) {
                    resource = new Resource(ResourceType.METAL, 180 + (int) (Math.random() * 221));
                } else if (roll < 0.115) {
                    resource = new Resource(ResourceType.COAL, 140 + (int) (Math.random() * 181));
                } else if (roll < 0.132) {
                    resource = new Resource(ResourceType.OIL, 180 + (int) (Math.random() * 221));
                }
                tiles[x][y] = new Tile(x, y, type, resource, null, true);
            }
        }

        for (int x = 0; x < width; x++) {
            makeWater(tiles[x][0]);
            makeWater(tiles[x][height - 1]);
        }
        for (int y = 0; y < height; y++) {
            makeWater(tiles[0][y]);
            makeWater(tiles[width - 1][y]);
        }

        clearResource(5, 5);
        clearResource(6, 5);
        clearResource(7, 5);
        clearResource(6, 4);
        clearResource(6, 6);
        clearResource(2, 3);

        ensureDeposit(4, 8, ResourceType.METAL, 300);
        ensureDeposit(9, 7, ResourceType.COAL, 240);
        // Fluid infrastructure needs a deterministic first oil field just like early solid resources.
        ensureDeposit(14, 10, ResourceType.OIL, 320);
    }

    private void ensureDeposit(int x, int y, ResourceType type, int amount) {
        Tile tile = getTile(x, y);
        if (tile != null && tile.getType() != TileType.WATER) tile.setResource(new Resource(type, amount));
    }

    private void rememberInitialResources() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Resource resource = tiles[x][y].getResource();
                initialResources[x][y] = resource == null ? null : resource.copy();
            }
        }
    }

    private void restoreInitialResources() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Resource resource = initialResources[x][y];
                tiles[x][y].setResource(resource == null ? null : resource.copy());
            }
        }
    }

    private void makeWater(Tile tile) {
        tile.setType(TileType.WATER);
        tile.setPassable(false);
        tile.setResource(null);
    }

    private void clearResource(int x, int y) {
        Tile tile = getTile(x, y);
        if (tile != null) tile.setResource(null);
    }

    public void clearDynamicOccupants() {
        restoreInitialResources();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Tile tile = tiles[x][y];
                tile.setBuilding(null);
                tile.setUnit(null);
                tile.setPassable(tile.getType() != TileType.WATER);
            }
        }
        clearResource(5, 5);
        clearResource(6, 5);
        clearResource(2, 3);
    }

    public Tile getTile(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return null;
        return tiles[x][y];
    }

    public List<Tile> findPath(Tile start, Tile end) { return new Pathfinder(this).findPath(start, end); }
    public int getHeight() { return height; }
    public int getWidth() { return width; }
}
