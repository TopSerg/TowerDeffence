import java.awt.*;
import java.util.*;
import java.util.List;

public class GameState {
    private List<Unit> allUnits = new ArrayList<>();
    private List<Building> allBuildings = new ArrayList<>();
    private GameMap map;
    private Inventory inventory = new Inventory(50);

    public GameState(GameMap map) {
        this.map = map;
        spawnInitialEntities();
    }

    private void spawnInitialEntities() {
        // Спавн стартового юнита
        Tile spawnTile = map.getTile(5, 5);
        Unit worker = new Unit(spawnTile, UnitType.WORKER, Color.BLUE, map);
        addUnit(worker, spawnTile);

        // Спавн первого здания
        Tile houseTile = map.getTile(6, 5);
        Building house = new House(houseTile, Color.RED);
        houseTile.setPassable(false);
        addBuilding(house, houseTile);
    }

    // Добавление юнита с привязкой к тайлу
    public void addUnit(Unit unit, Tile tile) {
        if (tile.hasUnit()) return;  // Тайл уже занят
        allUnits.add(unit);
        tile.setUnit(unit);
        unit.setPosition(tile);
    }

    // Добавление здания
    public void addBuilding(Building building, Tile tile) {
        if (tile.hasBuilding()) return;
        allBuildings.add(building);
        tile.setBuilding(building);
        building.setPosition(tile);
    }

    public List<Building> getAllBuildings() {
        return allBuildings;
    }

    public List<Unit> getAllUnits() {
        return allUnits;
    }
}