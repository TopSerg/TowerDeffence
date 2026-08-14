package towerdefence;

import towerdefence.building.BuildableType;
import towerdefence.building.Conveyor;
import towerdefence.combat.CombatTower;
import towerdefence.combat.Enemy;
import towerdefence.combat.EnemyType;
import towerdefence.combat.TowerModuleType;
import towerdefence.combat.WaveManager;
import towerdefence.game.GameState;
import towerdefence.resource.ResourceType;
import towerdefence.world.Direction;
import towerdefence.world.GameMap;
import towerdefence.world.Pathfinder;
import towerdefence.world.Tile;
import towerdefence.world.TileType;

import java.util.*;

public class Stage2FeatureTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Tile findFree(GameState state, GameMap map) {
        for (int y = 2; y < map.getHeight() - 2; y++) {
            for (int x = 2; x < map.getWidth() - 2; x++) {
                Tile tile = map.getTile(x, y);
                if (state.canPlaceBuilding(BuildableType.MACHINE_GUN_TOWER, tile)) return tile;
            }
        }
        throw new AssertionError("Нет свободной клетки");
    }

    private static Tile adjacentFree(GameMap map, Tile tile) {
        int[][] d = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] v : d) {
            Tile candidate = map.getTile(tile.getX()+v[0], tile.getY()+v[1]);
            if (candidate != null && candidate.getType() != TileType.WATER && !candidate.hasBuilding()) return candidate;
        }
        throw new AssertionError("Нет соседней клетки");
    }

    public static void main(String[] args) {
        verifyWorkerCanCrossConveyor();
        GameMap map = new GameMap(20, 20);
        GameState state = new GameState(map);
        check(state.getMainBuilding().getPosition() == map.getTile(6,5), "Стартовая база изменена");
        check(state.getEnemySpawnPoint().getPosition() == map.getTile(2,3), "Спавн изменён");
        check(state.getAllUnits().get(0).getPosition() == map.getTile(5,5), "Рабочий изменён");

        state.getInventory().add(ResourceType.METAL, 500);
        state.getInventory().add(ResourceType.COAL, 200);
        state.getInventory().add(ResourceType.SCRAP, 200);
        Tile towerTile = findFree(state, map);
        check(state.placeBuilding(BuildableType.MACHINE_GUN_TOWER, towerTile, Direction.RIGHT), "Чертёж башни не разместился");
        for (int i = 0; i < 2400 && !(towerTile.getBuilding() instanceof CombatTower); i++) state.update();
        check(towerTile.getBuilding() instanceof CombatTower, "Рабочий не завершил башню");
        CombatTower tower = (CombatTower) towerTile.getBuilding();
        check(!state.installTowerModule(tower, TowerModuleType.STABILIZER_II), "Второй стабилизатор поставился без требований");
        check(state.installTowerModule(tower, TowerModuleType.STABILIZER_I), "Не поставился стабилизатор I");
        check(state.installTowerModule(tower, TowerModuleType.COOLING_I), "Не поставилось охлаждение");
        check(state.installTowerModule(tower, TowerModuleType.STABILIZER_II), "Не поставился стабилизатор II");
        check(tower.getEffectiveFireCooldownTicks() < tower.getBaseFireCooldownTicks(), "Модули не ускорили стрельбу");

        tower.addAmmo(100);
        Tile enemyTile = adjacentFree(map, towerTile);
        Enemy target = new Enemy(10000, 0.0f, 0, map, enemyTile, state.getMainBuilding(), EnemyType.HEAVY);
        for (int i = 0; i < 400 && !tower.isOverheated(); i++) {
            tower.updateCombat(Collections.singletonList(target));
            tower.update();
        }
        check(tower.isOverheated(), "Башня не перегревается при ускоренной стрельбе");

        Map<ResourceType,Integer> expected = state.getTowerSaleRefund(tower);
        check(expected.get(ResourceType.METAL) == 63, "Неверный возврат металла: " + expected);
        check(expected.get(ResourceType.COAL) == 3, "Неверный возврат угля: " + expected);
        check(expected.get(ResourceType.SCRAP) == 9, "Неверный возврат лома: " + expected);
        int metalBefore = state.getInventory().getAmount(ResourceType.METAL);
        int coalBefore = state.getInventory().getAmount(ResourceType.COAL);
        int scrapBefore = state.getInventory().getAmount(ResourceType.SCRAP);
        Map<ResourceType,Integer> sold = state.sellTower(tower);
        check(!sold.isEmpty(), "Башня не продалась");
        check(!state.getAllBuildings().contains(tower), "Проданная башня осталась на карте");
        check(state.getInventory().getAmount(ResourceType.METAL) == metalBefore + 63, "Металл не возвращён");
        check(state.getInventory().getAmount(ResourceType.COAL) == coalBefore + 3, "Уголь не возвращён");
        check(state.getInventory().getAmount(ResourceType.SCRAP) == scrapBefore + 9, "Лом не возвращён");

        Tile e1 = map.getTile(3,3);
        Enemy normal = new Enemy(100, 0, 0, map, e1, state.getMainBuilding(), EnemyType.NORMAL);
        Enemy armored = new Enemy(100, 0, 0, map, e1, state.getMainBuilding(), EnemyType.ARMORED);
        normal.takeDamage(10);
        armored.takeDamage(10);
        check(normal.getHealth() == 90, "Обычный робот неверно получает урон");
        check(armored.getHealth() == 95, "Броня не уменьшает урон");

        check(EnemyType.values().length == 4, "Должно быть четыре типа врагов");
        check(EnemyType.FAST.getBaseSpeed() > EnemyType.NORMAL.getBaseSpeed(), "Быстрый робот не быстрее обычного");
        check(EnemyType.HEAVY.getBaseHealth() > EnemyType.ARMORED.getBaseHealth(), "Тяжёлый робот недостаточно прочный");
        check(EnemyType.HEAVY.getBaseDamage() > EnemyType.NORMAL.getBaseDamage(), "Тяжёлый робот не опаснее базы");

        WaveManager waves = new WaveManager();
        check(waves.chooseEnemyType(1, 0) == EnemyType.NORMAL, "Первая волна начинается не с обычного");
        check(waves.chooseEnemyType(1, 3) == EnemyType.FAST, "В первой волне нет быстрого");
        check(waves.chooseEnemyType(3, 3) == EnemyType.ARMORED, "В третьей волне нет бронированного");
        check(waves.chooseEnemyType(4, 5) == EnemyType.HEAVY, "В четвёртой волне нет тяжёлого");

        System.out.println("Stage2FeatureTest: OK");
    }

    private static void verifyWorkerCanCrossConveyor() {
        GameMap map = new GameMap(20, 20);
        GameState state = new GameState(map);
        Tile conveyorTile = map.getTile(10, 10);
        check(state.addBuilding(new Conveyor(conveyorTile, Direction.RIGHT), conveyorTile),
                "Не удалось поставить тестовый конвейер");
        check(conveyorTile.isPassable(), "Конвейер должен помечать клетку проходимой");
        List<Tile> path = new Pathfinder(map).findPath(map.getTile(9, 10), map.getTile(11, 10));
        check(path != null && path.contains(conveyorTile),
                "Рабочий не может проложить путь через конвейер");
    }
}
