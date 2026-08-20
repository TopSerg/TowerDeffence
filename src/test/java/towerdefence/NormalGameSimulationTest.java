package towerdefence;

import towerdefence.building.BuildableType;
import towerdefence.building.Building;
import towerdefence.building.ConstructionSite;
import towerdefence.building.Conveyor;
import towerdefence.building.Drill;
import towerdefence.building.FactoryPort;
import towerdefence.building.Workshop;
import towerdefence.building.WorkshopItem;
import towerdefence.combat.CombatTower;
import towerdefence.game.GameStatus;
import towerdefence.game.RoadmapGameState;
import towerdefence.resource.Resource;
import towerdefence.resource.ResourceType;
import towerdefence.roadmap.RoadmapRuntime;
import towerdefence.world.Direction;
import towerdefence.world.GameMap;
import towerdefence.world.Tile;
import towerdefence.world.TileType;

import java.util.function.BooleanSupplier;

/**
 * Длинный headless smoke-test нормальной игровой сессии.
 *
 * Сценарий намеренно идёт через публичные игровые API, а не создаёт готовые
 * здания напрямую: Construction Rover реально доставляет материалы и строит,
 * буры реально добывают, конвейеры реально возят ресурс, башня получает патроны
 * и стреляет, а Workshop принимает ресурс через INPUT Gateway и возвращает его
 * через OUTPUT Gateway.
 */
public class NormalGameSimulationTest {
    private static final int BUILD_TIMEOUT = 14_000;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static int runUntil(RoadmapGameState state, int maxTicks,
                                BooleanSupplier condition, String failureMessage) {
        for (int tick = 0; tick < maxTicks; tick++) {
            if (condition.getAsBoolean()) return tick;
            state.update();
            checkCoreInvariants(state);
        }
        if (condition.getAsBoolean()) return maxTicks;
        throw new AssertionError(failureMessage + " after " + maxTicks + " ticks");
    }

    private static void runTicks(RoadmapGameState state, int ticks) {
        for (int i = 0; i < ticks; i++) {
            state.update();
            checkCoreInvariants(state);
        }
    }

    private static void checkCoreInvariants(RoadmapGameState state) {
        check(state.getStatus() == GameStatus.RUNNING, "Игра неожиданно завершилась во время smoke-test");
        check(state.getMainBuilding() != null && state.getMainBuilding().isAlive(), "Главная база уничтожена");
        for (ResourceType type : ResourceType.values()) {
            check(state.getInventory().getAmount(type) >= 0, "Отрицательный ресурс: " + type);
        }
        for (Building building : state.getAllBuildings()) {
            if (building.isAlive()) check(building.getPosition() != null, "Живое здание без позиции: " + building.getType());
        }
    }

    private static void clearSandbox(GameMap map) {
        for (int y = 2; y <= 12; y++) {
            for (int x = 4; x <= 16; x++) {
                Tile tile = map.getTile(x, y);
                if (tile == null) continue;

                // Ресурсы очищаем даже под временно стоящими юнитами. Иначе юнит
                // позже уедет, залежь останется, и следующий чертёж случайно попадёт на неё.
                tile.setResource(null);

                if (tile.hasBuilding() || tile.hasUnit()) continue;
                tile.setType(TileType.DIRT);
                tile.setPassable(true);
            }
        }
    }

    private static void clearBuildFootprint(GameMap map, BuildableType type, Tile origin) {
        if (type == BuildableType.DRILL) return;
        for (int dy = 0; dy < type.getFootprintHeight(); dy++) {
            for (int dx = 0; dx < type.getFootprintWidth(); dx++) {
                Tile footprintTile = map.getTile(origin.getX() + dx, origin.getY() + dy);
                check(footprintTile != null,
                        "Тест пытается строить " + type + " за пределами карты from "
                                + origin.getX() + "," + origin.getY());
                footprintTile.setResource(null);
                check(!footprintTile.hasResource(),
                        "Не удалось очистить ресурс под чертёж " + type + " at "
                                + footprintTile.getX() + "," + footprintTile.getY());
            }
        }
    }

    private static Building build(RoadmapGameState state, GameMap map, BuildableType type,
                                  Tile tile, Direction direction) {
        clearBuildFootprint(map, type, tile);
        check(state.placeBuilding(type, tile, direction),
                "Не удалось поставить чертёж " + type + " at " + tile.getX() + "," + tile.getY()
                        + ": " + state.getBuildFailureReason(type, tile));
        runUntil(state, BUILD_TIMEOUT,
                () -> tile.getBuilding() != null && !(tile.getBuilding() instanceof ConstructionSite),
                "Construction Rover не завершил " + type + " at " + tile.getX() + "," + tile.getY());
        Building result = tile.getBuilding();
        check(BuildableType.fromBuilding(result) == type,
                "После стройки получен неверный тип: expected=" + type + ", actual=" + BuildableType.fromBuilding(result));
        return result;
    }

    private static void removeConveyor(RoadmapGameState state, Tile tile) {
        check(tile.getBuilding() instanceof Conveyor,
                "Ожидался conveyor для демонтажа at " + tile.getX() + "," + tile.getY());
        check(state.removeBuilding(tile.getBuilding()),
                "Не удалось демонтировать conveyor at " + tile.getX() + "," + tile.getY());
    }

    private static CombatLane findFreeCombatLane(RoadmapGameState state, GameMap map) {
        Tile base = state.getMainBuilding().getPosition();
        check(base != null, "Главная база не имеет позиции");

        Direction[] directions = {Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT};
        int[][] offsets = {
                {0, -1, 0, -2},
                {0, 1, 0, 2},
                {-1, 0, -2, 0},
                {1, 0, 2, 0}
        };

        for (int i = 0; i < directions.length; i++) {
            Tile conveyorTile = map.getTile(base.getX() + offsets[i][0], base.getY() + offsets[i][1]);
            Tile towerTile = map.getTile(base.getX() + offsets[i][2], base.getY() + offsets[i][3]);
            if (conveyorTile == null || towerTile == null) continue;

            clearBuildFootprint(map, BuildableType.CONVEYOR, conveyorTile);
            clearBuildFootprint(map, BuildableType.MACHINE_GUN_TOWER, towerTile);

            if (state.canPlaceBuilding(BuildableType.CONVEYOR, conveyorTile)
                    && state.canPlaceBuilding(BuildableType.MACHINE_GUN_TOWER, towerTile)) {
                return new CombatLane(conveyorTile, towerTile, directions[i]);
            }
        }

        throw new AssertionError("Нет свободного двухклеточного коридора от базы для боевого smoke-test");
    }

    private static CombatLane buildCombatLane(RoadmapGameState state, GameMap map) {
        CombatLane lane = findFreeCombatLane(state, map);

        // Оба чертежа ставим до первого update(). Так Construction Rover не сможет
        // закончить первый объект и припарковаться на клетке, где должен стоять второй.
        check(state.placeBuilding(BuildableType.CONVEYOR, lane.conveyorTile, lane.direction),
                "Не удалось поставить боевой conveyor at "
                        + lane.conveyorTile.getX() + "," + lane.conveyorTile.getY()
                        + ": " + state.getBuildFailureReason(BuildableType.CONVEYOR, lane.conveyorTile));
        check(state.placeBuilding(BuildableType.MACHINE_GUN_TOWER, lane.towerTile, lane.direction),
                "Не удалось поставить боевую башню at "
                        + lane.towerTile.getX() + "," + lane.towerTile.getY()
                        + ": " + state.getBuildFailureReason(BuildableType.MACHINE_GUN_TOWER, lane.towerTile));

        runUntil(state, BUILD_TIMEOUT,
                () -> lane.conveyorTile.getBuilding() instanceof Conveyor
                        && lane.towerTile.getBuilding() instanceof CombatTower,
                "Construction Rover не завершил боевой conveyor + tower");

        lane.conveyor = (Conveyor) lane.conveyorTile.getBuilding();
        lane.tower = (CombatTower) lane.towerTile.getBuilding();
        return lane;
    }

    private static void buildInteriorLane(RoadmapGameState state, Workshop workshop, int y) {
        RoadmapRuntime runtime = state.getRoadmap();
        RoadmapRuntime.FactoryState factory = runtime.getFactoryState(workshop);
        check(factory != null, "Workshop отсутствует в RoadmapRuntime");

        // Строим от INPUT Gateway к OUTPUT Gateway по одной задаче, чтобы
        // физический внутренний бот всегда имел путь к следующему чертежу.
        for (int x = 7; x >= 1; x--) {
            int oldSize = factory.getTasks().size();
            check(runtime.queueInteriorConveyor(workshop, x, y, Direction.LEFT),
                    "Не удалось запланировать внутренний conveyor " + x + "," + y);
            RoadmapRuntime.InteriorBuildTask task = factory.getTasks().get(oldSize);
            runUntil(state, 2_000,
                    () -> task.getPhase() == RoadmapRuntime.BlueprintPhase.DONE,
                    "Внутренний строительный робот застрял на conveyor " + x + "," + y
                            + "; phase=" + task.getPhase());
            check(workshop.getInteriorConveyor(x, y) != null,
                    "Внутренний conveyor помечен DONE, но физически отсутствует at " + x + "," + y);
        }
    }

    private static int countInterior(Workshop workshop, ResourceType type) {
        int count = 0;
        for (WorkshopItem item : workshop.getInteriorItems()) if (item.getType() == type) count++;
        return count;
    }

    private static int coalMass(RoadmapGameState state, Drill drill, FactoryPort input,
                                FactoryPort output, Workshop workshop) {
        return state.getInventory().getAmount(ResourceType.COAL)
                + drill.getRemainingDeposit()
                + drill.getBuffer()
                + input.getBufferSize()
                + output.getBufferSize()
                + countInterior(workshop, ResourceType.COAL);
    }

    public static void main(String[] args) {
        GameMap map = new GameMap(40, 30);
        RoadmapGameState state = new RoadmapGameState(map);
        state.getWaveManager().setWaveDelaySeconds(3600);
        clearSandbox(map);

        Tile metalDeposit = map.getTile(11, 5);
        Tile coalDeposit = map.getTile(11, 6);
        check(!metalDeposit.hasBuilding() && !metalDeposit.hasUnit(), "Metal test deposit occupied");
        check(!coalDeposit.hasBuilding() && !coalDeposit.hasUnit(), "Coal test deposit occupied");
        metalDeposit.setResource(new Resource(ResourceType.METAL, 1_000));
        coalDeposit.setResource(new Resource(ResourceType.COAL, 1_000));

        // 1. Реально строим две бурилки.
        Drill metalDrill = (Drill) build(state, map, BuildableType.DRILL, metalDeposit, Direction.LEFT);
        Drill coalDrill = (Drill) build(state, map, BuildableType.DRILL, coalDeposit, Direction.LEFT);

        // 2. Реально строим две линии добычи к общему складу.
        build(state, map, BuildableType.CONVEYOR, map.getTile(10, 5), Direction.LEFT);
        build(state, map, BuildableType.CONVEYOR, map.getTile(9, 5), Direction.LEFT);
        build(state, map, BuildableType.CONVEYOR, map.getTile(8, 5), Direction.LEFT);
        build(state, map, BuildableType.CONVEYOR, map.getTile(7, 5), Direction.LEFT);

        build(state, map, BuildableType.CONVEYOR, map.getTile(10, 6), Direction.LEFT);
        build(state, map, BuildableType.CONVEYOR, map.getTile(9, 6), Direction.LEFT);
        build(state, map, BuildableType.CONVEYOR, map.getTile(8, 6), Direction.LEFT);
        build(state, map, BuildableType.CONVEYOR, map.getTile(7, 6), Direction.UP);

        int metalBeforeMining = state.getInventory().getAmount(ResourceType.METAL);
        int coalBeforeMining = state.getInventory().getAmount(ResourceType.COAL);
        int miningTicks = runUntil(state, 8_000,
                () -> state.getInventory().getAmount(ResourceType.METAL) >= metalBeforeMining + 80
                        && state.getInventory().getAmount(ResourceType.COAL) >= coalBeforeMining + 12,
                "Две добывающие линии не пополнили склад");
        check(metalDrill.isOutputConnected(), "Metal Drill не видит конвейер до склада");
        check(coalDrill.isOutputConnected(), "Coal Drill не видит конвейер до склада");
        check(metalDrill.getRemainingDeposit() < 1_000, "Metal Drill ничего не добыл");
        check(coalDrill.getRemainingDeposit() < 1_000, "Coal Drill ничего не добыл");

        // 3. Строим связку conveyor + tower двумя одновременными чертежами.
        CombatLane combatLane = buildCombatLane(state, map);
        CombatTower tower = combatLane.tower;
        int baseAmmoBeforeSupply = state.getMainBuilding().getAmmoStock();
        runUntil(state, 1_500,
                () -> tower.isSupplied() && tower.getAmmo() >= 8,
                "Построенная башня не получила патроны со склада");
        check(state.getMainBuilding().getAmmoStock() < baseAmmoBeforeSupply,
                "Подача патронов в башню не уменьшила запас базы");

        int killsBefore = state.getDestroyedEnemies();
        check(state.spawnTestEnemy(), "Не удалось создать тестового противника");
        int combatTicks = runUntil(state, 2_000,
                () -> state.getDestroyedEnemies() > killsBefore,
                "Построенная и снабжённая башня не уничтожила противника");

        // Боевой стенд временный: освобождаем выбранную сторону базы для следующих этапов.
        check(state.removeBuilding(combatLane.tower), "Не удалось демонтировать тестовую башню");
        check(state.removeBuilding(combatLane.conveyor), "Не удалось демонтировать тестовый conveyor башни");

        // 4. Строим Workshop через тот же реальный Construction Rover.
        Workshop workshop = (Workshop) build(state, map, BuildableType.WORKSHOP,
                map.getTile(8, 8), Direction.RIGHT);
        runUntil(state, 300,
                () -> state.getRoadmap().getFactoryState(workshop) != null,
                "RoadmapRuntime не зарегистрировал построенный Workshop");

        FactoryPort input = (FactoryPort) build(state, map, BuildableType.FACTORY_INPUT_PORT,
                map.getTile(11, 9), Direction.LEFT);
        FactoryPort output = (FactoryPort) build(state, map, BuildableType.FACTORY_OUTPUT_PORT,
                map.getTile(7, 9), Direction.LEFT);
        check(input.getWorkshop() == workshop && output.getWorkshop() == workshop,
                "INPUT/OUTPUT ports не привязались к Workshop");

        // 5. Перенаправляем уже работающую угольную бурилку из склада во вход фабрики.
        removeConveyor(state, map.getTile(10, 6));
        removeConveyor(state, map.getTile(9, 6));
        removeConveyor(state, map.getTile(8, 6));
        removeConveyor(state, map.getTile(7, 6));
        build(state, map, BuildableType.CONVEYOR, map.getTile(11, 7), Direction.DOWN);
        build(state, map, BuildableType.CONVEYOR, map.getTile(11, 8), Direction.DOWN);

        // OUTPUT Gateway возвращает поток в главный склад.
        build(state, map, BuildableType.CONVEYOR, map.getTile(6, 9), Direction.UP);
        build(state, map, BuildableType.CONVEYOR, map.getTile(6, 8), Direction.UP);
        build(state, map, BuildableType.CONVEYOR, map.getTile(6, 7), Direction.UP);
        build(state, map, BuildableType.CONVEYOR, map.getTile(6, 6), Direction.UP);

        // 6. Вася физически входит внутрь, а внутренний робот строит линию 9x9.
        state.getRoadmap().requestEnterWorkshop(workshop);
        runUntil(state, 3_000,
                () -> state.getRoadmap().getVasyaInsideWorkshop() == workshop,
                "Вася не смог физически войти в Workshop");
        buildInteriorLane(state, workshop, 4);

        check(state.getConstructionQueue().isEmpty(), "После подготовки фабрики остались внешние стройки");

        // 7. Проверяем полный живой поток Drill -> INPUT -> interior -> OUTPUT -> Storage.
        int baseCoalBeforeFactory = state.getInventory().getAmount(ResourceType.COAL);
        int massBefore = coalMass(state, coalDrill, input, output, workshop);
        boolean sawInputBuffer = false;
        boolean sawInteriorCoal = false;
        boolean sawInputConnection = false;
        boolean sawOutputConnection = false;

        for (int tick = 0; tick < 1_200; tick++) {
            state.update();
            checkCoreInvariants(state);
            sawInputBuffer |= input.getBufferSize() > 0;
            sawInteriorCoal |= countInterior(workshop, ResourceType.COAL) > 0;
            sawInputConnection |= input.isExternalConnected() && coalDrill.isOutputConnected();
            sawOutputConnection |= output.isExternalConnected();
        }

        int baseCoalAfterFactory = state.getInventory().getAmount(ResourceType.COAL);
        int massAfter = coalMass(state, coalDrill, input, output, workshop);
        check(sawInputConnection, "Coal Drill -> INPUT Port никогда не стал активным");
        check(sawInputBuffer, "Ресурс ни разу не попал во входной Gateway buffer");
        check(sawInteriorCoal, "Ресурс ни разу не появился внутри Workshop 9x9");
        check(sawOutputConnection, "OUTPUT Port не видит путь обратно к складу");
        check(baseCoalAfterFactory > baseCoalBeforeFactory,
                "Ресурс вошёл в фабрику, но не вернулся из OUTPUT Port на склад");
        check(massAfter == massBefore,
                "Нарушено сохранение throughput/resource mass: before=" + massBefore + ", after=" + massAfter);

        System.out.println("NormalGameSimulationTest: OK"
                + "; miningTicks=" + miningTicks
                + ", combatTicks=" + combatTicks
                + ", metal=" + state.getInventory().getAmount(ResourceType.METAL)
                + ", coal=" + state.getInventory().getAmount(ResourceType.COAL)
                + ", factoryDelivered=" + (baseCoalAfterFactory - baseCoalBeforeFactory));
    }

    private static final class CombatLane {
        private final Tile conveyorTile;
        private final Tile towerTile;
        private final Direction direction;
        private Conveyor conveyor;
        private CombatTower tower;

        private CombatLane(Tile conveyorTile, Tile towerTile, Direction direction) {
            this.conveyorTile = conveyorTile;
            this.towerTile = towerTile;
            this.direction = direction;
        }
    }
}
