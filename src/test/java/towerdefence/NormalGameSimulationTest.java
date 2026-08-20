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

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Длинный headless smoke-test нормальной игровой сессии.
 *
 * Сценарий идёт через публичные игровые API: Construction Rover реально строит,
 * буры реально добывают, башня получает патроны, Workshop принимает ресурс через
 * INPUT Gateway и возвращает его через OUTPUT Gateway.
 */
public class NormalGameSimulationTest {
    private static final int BUILD_TIMEOUT = 14_000;
    private static final int MULTI_BUILD_TIMEOUT = 30_000;

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

    private static void checkCoreInvariants(RoadmapGameState state) {
        check(state.getStatus() == GameStatus.RUNNING, "Игра неожиданно завершилась во время smoke-test");
        check(state.getMainBuilding() != null && state.getMainBuilding().isAlive(), "Главная база уничтожена");
        for (ResourceType type : ResourceType.values()) {
            check(state.getInventory().getAmount(type) >= 0, "Отрицательный ресурс: " + type);
        }
        for (Building building : state.getAllBuildings()) {
            if (building.isAlive()) {
                check(building.getPosition() != null, "Живое здание без позиции: " + building.getType());
            }
        }
    }

    private static void clearSandbox(GameMap map) {
        for (int y = 2; y <= 12; y++) {
            for (int x = 4; x <= 16; x++) {
                Tile tile = map.getTile(x, y);
                if (tile == null) continue;
                // Даже если здесь временно стоит юнит, случайное месторождение убираем.
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
                Tile tile = map.getTile(origin.getX() + dx, origin.getY() + dy);
                check(tile != null,
                        "Тест пытается строить " + type + " за пределами карты from "
                                + origin.getX() + "," + origin.getY());
                tile.setResource(null);
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
                "После стройки получен неверный тип: expected=" + type
                        + ", actual=" + BuildableType.fromBuilding(result));
        return result;
    }

    private static void removeConveyor(RoadmapGameState state, Tile tile) {
        check(tile.getBuilding() instanceof Conveyor,
                "Ожидался conveyor для демонтажа at " + tile.getX() + "," + tile.getY());
        check(state.removeBuilding(tile.getBuilding()),
                "Не удалось демонтировать conveyor at " + tile.getX() + "," + tile.getY());
    }

    // ---------------------------------------------------------------------
    // Combat stand: two blueprints are reserved before the first update.
    // ---------------------------------------------------------------------

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
            Tile conveyor = map.getTile(base.getX() + offsets[i][0], base.getY() + offsets[i][1]);
            Tile tower = map.getTile(base.getX() + offsets[i][2], base.getY() + offsets[i][3]);
            if (conveyor == null || tower == null) continue;
            clearBuildFootprint(map, BuildableType.CONVEYOR, conveyor);
            clearBuildFootprint(map, BuildableType.MACHINE_GUN_TOWER, tower);
            if (state.canPlaceBuilding(BuildableType.CONVEYOR, conveyor)
                    && state.canPlaceBuilding(BuildableType.MACHINE_GUN_TOWER, tower)) {
                return new CombatLane(conveyor, tower, directions[i]);
            }
        }
        throw new AssertionError("Нет свободного двухклеточного коридора от базы для боевого smoke-test");
    }

    private static CombatLane buildCombatLane(RoadmapGameState state, GameMap map) {
        CombatLane lane = findFreeCombatLane(state, map);
        check(state.placeBuilding(BuildableType.CONVEYOR, lane.conveyorTile, lane.direction),
                "Не удалось поставить боевой conveyor: "
                        + state.getBuildFailureReason(BuildableType.CONVEYOR, lane.conveyorTile));
        check(state.placeBuilding(BuildableType.MACHINE_GUN_TOWER, lane.towerTile, lane.direction),
                "Не удалось поставить боевую башню: "
                        + state.getBuildFailureReason(BuildableType.MACHINE_GUN_TOWER, lane.towerTile));

        runUntil(state, MULTI_BUILD_TIMEOUT,
                () -> lane.conveyorTile.getBuilding() instanceof Conveyor
                        && lane.towerTile.getBuilding() instanceof CombatTower,
                "Construction Rover не завершил боевой conveyor + tower");

        lane.conveyor = (Conveyor) lane.conveyorTile.getBuilding();
        lane.tower = (CombatTower) lane.towerTile.getBuilding();
        return lane;
    }

    // ---------------------------------------------------------------------
    // Sliding factory window and dynamic external conveyor routing.
    // ---------------------------------------------------------------------

    /**
     * Скользящее квадратное окно вокруг базы: радиус 1, затем 2, 3 и т.д.
     * Координата кольца считается центром будущего Workshop 3x3.
     */
    private static Tile findWorkshopOriginByExpandingRings(RoadmapGameState state, GameMap map) {
        Tile base = state.getMainBuilding().getPosition();
        int maxRadius = Math.max(map.getWidth(), map.getHeight());

        for (int radius = 1; radius <= maxRadius; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                Tile left = tryWorkshopCenter(state, map, base.getX() - radius, base.getY() + dy);
                if (left != null) return left;
                Tile right = tryWorkshopCenter(state, map, base.getX() + radius, base.getY() + dy);
                if (right != null) return right;
            }
            for (int dx = -radius + 1; dx <= radius - 1; dx++) {
                Tile top = tryWorkshopCenter(state, map, base.getX() + dx, base.getY() - radius);
                if (top != null) return top;
                Tile bottom = tryWorkshopCenter(state, map, base.getX() + dx, base.getY() + radius);
                if (bottom != null) return bottom;
            }
        }
        throw new AssertionError("Скользящее окно не нашло место для Workshop 3x3");
    }

    private static Tile tryWorkshopCenter(RoadmapGameState state, GameMap map, int centerX, int centerY) {
        int originX = centerX - Workshop.WIDTH_TILES / 2;
        int originY = centerY - Workshop.HEIGHT_TILES / 2;
        Tile origin = map.getTile(originX, originY);
        if (origin == null || !state.canPlaceBuilding(BuildableType.WORKSHOP, origin)) return null;

        // Оставляем запас по периметру: после завершения Workshop один из тайлов
        // может занять Construction Rover, поэтому хотим несколько вариантов для портов.
        int freePerimeter = 0;
        for (Tile tile : workshopPerimeter(map, origin)) {
            if (state.canBuildOn(tile)) freePerimeter++;
        }
        return freePerimeter >= 4 ? origin : null;
    }

    private static List<Tile> workshopPerimeter(GameMap map, Tile origin) {
        List<Tile> result = new ArrayList<>();
        int x0 = origin.getX();
        int y0 = origin.getY();
        int x1 = x0 + Workshop.WIDTH_TILES - 1;
        int y1 = y0 + Workshop.HEIGHT_TILES - 1;
        for (int x = x0; x <= x1; x++) {
            addUnique(result, map.getTile(x, y0 - 1));
            addUnique(result, map.getTile(x, y1 + 1));
        }
        for (int y = y0; y <= y1; y++) {
            addUnique(result, map.getTile(x0 - 1, y));
            addUnique(result, map.getTile(x1 + 1, y));
        }
        return result;
    }

    private static void addUnique(List<Tile> tiles, Tile tile) {
        if (tile != null && !tiles.contains(tile)) tiles.add(tile);
    }

    private static PortPlan findPortPlan(RoadmapGameState state, GameMap map,
                                         Workshop workshop, Drill coalDrill) {
        List<Tile> perimeter = workshopPerimeter(map, workshop.getPosition());
        Tile base = state.getMainBuilding().getPosition();

        for (Tile input : perimeter) {
            if (!state.canPlaceBuilding(BuildableType.FACTORY_INPUT_PORT, input)) continue;
            for (Tile output : perimeter) {
                if (output == input || !state.canPlaceBuilding(BuildableType.FACTORY_OUTPUT_PORT, output)) continue;

                Set<Tile> reserved = identitySet();
                reserved.add(input);
                reserved.add(output);
                ExternalPaths paths = findNonOverlappingExternalPaths(
                        state, map, coalDrill.getPosition(), input, output, base, reserved);
                if (paths != null) return new PortPlan(input, output);
            }
        }
        throw new AssertionError("Не найдено свободной пары INPUT/OUTPUT вокруг Workshop");
    }

    private static ExternalPaths findNonOverlappingExternalPaths(
            RoadmapGameState state, GameMap map,
            Tile drill, Tile inputPort, Tile outputPort, Tile base,
            Set<Tile> initiallyReserved) {

        Set<Tile> reserved = identitySet();
        reserved.addAll(initiallyReserved);
        List<Tile> inputPath = findConveyorPath(state, map, drill, inputPort, reserved);
        if (inputPath != null) {
            reserved.addAll(inputPath);
            List<Tile> outputPath = findConveyorPath(state, map, outputPort, base, reserved);
            if (outputPath != null) return new ExternalPaths(inputPath, outputPath);
        }

        reserved = identitySet();
        reserved.addAll(initiallyReserved);
        List<Tile> outputPath = findConveyorPath(state, map, outputPort, base, reserved);
        if (outputPath != null) {
            reserved.addAll(outputPath);
            inputPath = findConveyorPath(state, map, drill, inputPort, reserved);
            if (inputPath != null) return new ExternalPaths(inputPath, outputPath);
        }
        return null;
    }

    private static List<Tile> findConveyorPath(RoadmapGameState state, GameMap map,
                                               Tile source, Tile target, Set<Tile> reserved) {
        Deque<Tile> queue = new ArrayDeque<>();
        Set<Tile> visited = identitySet();
        Map<Tile, Tile> parent = new IdentityHashMap<>();

        for (Tile neighbor : orthogonalNeighbors(map, source)) {
            if (!isFreeConveyorTile(state, neighbor, reserved) || neighbor == target) continue;
            visited.add(neighbor);
            parent.put(neighbor, null);
            queue.addLast(neighbor);
        }

        while (!queue.isEmpty()) {
            Tile current = queue.removeFirst();
            if (isAdjacent(current, target)) return reconstructPath(current, parent);

            for (Tile next : orthogonalNeighbors(map, current)) {
                if (visited.contains(next) || !isFreeConveyorTile(state, next, reserved)) continue;
                visited.add(next);
                parent.put(next, current);
                queue.addLast(next);
            }
        }
        return null;
    }

    private static boolean isFreeConveyorTile(RoadmapGameState state, Tile tile, Set<Tile> reserved) {
        return tile != null && !reserved.contains(tile)
                && state.canPlaceBuilding(BuildableType.CONVEYOR, tile);
    }

    private static List<Tile> reconstructPath(Tile end, Map<Tile, Tile> parent) {
        List<Tile> reverse = new ArrayList<>();
        for (Tile cursor = end; cursor != null; cursor = parent.get(cursor)) reverse.add(cursor);
        Collections.reverse(reverse);
        return reverse;
    }

    private static List<Tile> orthogonalNeighbors(GameMap map, Tile tile) {
        List<Tile> result = new ArrayList<>(4);
        if (tile == null) return result;
        result.add(map.getTile(tile.getX() + 1, tile.getY()));
        result.add(map.getTile(tile.getX() - 1, tile.getY()));
        result.add(map.getTile(tile.getX(), tile.getY() + 1));
        result.add(map.getTile(tile.getX(), tile.getY() - 1));
        return result;
    }

    private static boolean isAdjacent(Tile a, Tile b) {
        return a != null && b != null
                && Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) == 1;
    }

    private static Direction directionBetween(Tile from, Tile to) {
        return directionBetween(from.getX(), from.getY(), to.getX(), to.getY());
    }

    private static Direction directionBetween(int fromX, int fromY, int toX, int toY) {
        int dx = toX - fromX;
        int dy = toY - fromY;
        if (dx == 1 && dy == 0) return Direction.RIGHT;
        if (dx == -1 && dy == 0) return Direction.LEFT;
        if (dx == 0 && dy == 1) return Direction.DOWN;
        if (dx == 0 && dy == -1) return Direction.UP;
        throw new IllegalArgumentException("Клетки не соседние: " + fromX + "," + fromY + " -> " + toX + "," + toY);
    }

    private static Set<Tile> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static void queueConveyorPath(RoadmapGameState state, List<Tile> path, Tile endpoint) {
        check(path != null && !path.isEmpty(), "Пустой внешний conveyor path");
        for (int i = 0; i < path.size(); i++) {
            Tile tile = path.get(i);
            Tile next = i + 1 < path.size() ? path.get(i + 1) : endpoint;
            Direction direction = directionBetween(tile, next);
            check(state.placeBuilding(BuildableType.CONVEYOR, tile, direction),
                    "Не удалось зарезервировать conveyor path at " + tile.getX() + "," + tile.getY()
                            + ": " + state.getBuildFailureReason(BuildableType.CONVEYOR, tile));
        }
    }

    private static boolean conveyorPathBuilt(List<Tile> path) {
        for (Tile tile : path) if (!(tile.getBuilding() instanceof Conveyor)) return false;
        return true;
    }

    // ---------------------------------------------------------------------
    // Dynamic Workshop interior routing between the real selected gateways.
    // ---------------------------------------------------------------------

    private static void buildInteriorLane(RoadmapGameState state, Workshop workshop,
                                          FactoryPort input, FactoryPort output) {
        RoadmapRuntime runtime = state.getRoadmap();
        RoadmapRuntime.FactoryState factory = runtime.getFactoryState(workshop);
        check(factory != null, "Workshop отсутствует в RoadmapRuntime");
        check(input.getGatewayCells().size() == 3 && output.getGatewayCells().size() == 3,
                "Порты не создали три Gateway lanes");

        Point inputGateway = input.getGatewayCells().get(1);
        Point outputGateway = output.getGatewayCells().get(1);
        Direction inputInward = opposite(input.getAttachedSide());
        Direction outputInward = opposite(output.getAttachedSide());
        Point start = new Point(inputGateway.x + inputInward.getDx(), inputGateway.y + inputInward.getDy());
        Point end = new Point(outputGateway.x + outputInward.getDx(), outputGateway.y + outputInward.getDy());

        List<Point> path = findInteriorPath(workshop, start, end);
        check(path != null && !path.isEmpty(), "Не найден внутренний путь между INPUT и OUTPUT Gateway");

        for (int i = 0; i < path.size(); i++) {
            Point cell = path.get(i);
            Point next = i + 1 < path.size() ? path.get(i + 1) : outputGateway;
            Direction direction = directionBetween(cell.x, cell.y, next.x, next.y);
            int oldSize = factory.getTasks().size();
            check(runtime.queueInteriorConveyor(workshop, cell.x, cell.y, direction),
                    "Не удалось запланировать внутренний conveyor " + cell.x + "," + cell.y);
            RoadmapRuntime.InteriorBuildTask task = factory.getTasks().get(oldSize);
            runUntil(state, 2_000,
                    () -> task.getPhase() == RoadmapRuntime.BlueprintPhase.DONE,
                    "Внутренний строительный робот застрял at " + cell.x + "," + cell.y
                            + "; phase=" + task.getPhase());
            check(workshop.getInteriorConveyor(cell.x, cell.y) != null,
                    "Внутренний conveyor DONE, но отсутствует at " + cell.x + "," + cell.y);
        }
    }

    private static List<Point> findInteriorPath(Workshop workshop, Point start, Point end) {
        if (!interiorCellFree(workshop, start.x, start.y) || !interiorCellFree(workshop, end.x, end.y)) {
            return null;
        }

        Deque<Point> queue = new ArrayDeque<>();
        boolean[][] visited = new boolean[Workshop.INTERIOR_SIZE][Workshop.INTERIOR_SIZE];
        Point[][] parent = new Point[Workshop.INTERIOR_SIZE][Workshop.INTERIOR_SIZE];
        queue.addLast(start);
        visited[start.y][start.x] = true;

        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            if (current.equals(end)) {
                List<Point> reverse = new ArrayList<>();
                Point cursor = current;
                while (cursor != null) {
                    reverse.add(cursor);
                    cursor = parent[cursor.y][cursor.x];
                }
                Collections.reverse(reverse);
                return reverse;
            }
            for (int[] direction : directions) {
                int nx = current.x + direction[0];
                int ny = current.y + direction[1];
                if (nx < 0 || ny < 0 || nx >= Workshop.INTERIOR_SIZE || ny >= Workshop.INTERIOR_SIZE) continue;
                if (visited[ny][nx] || !interiorCellFree(workshop, nx, ny)) continue;
                visited[ny][nx] = true;
                parent[ny][nx] = current;
                queue.addLast(new Point(nx, ny));
            }
        }
        return null;
    }

    private static boolean interiorCellFree(Workshop workshop, int x, int y) {
        return x >= 0 && y >= 0 && x < Workshop.INTERIOR_SIZE && y < Workshop.INTERIOR_SIZE
                && !workshop.isGatewayCell(x, y)
                && !workshop.isInteriorReserved(x, y)
                && workshop.getInteriorConveyor(x, y) == null;
    }

    private static Direction opposite(Direction direction) {
        switch (direction) {
            case UP: return Direction.DOWN;
            case DOWN: return Direction.UP;
            case LEFT: return Direction.RIGHT;
            case RIGHT: return Direction.LEFT;
            default: throw new IllegalArgumentException("Unknown direction " + direction);
        }
    }

    private static int countInterior(Workshop workshop, ResourceType type) {
        int count = 0;
        for (WorkshopItem item : workshop.getInteriorItems()) {
            if (item.getType() == type) count++;
        }
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

        // 1. Две реальные бурилки.
        Drill metalDrill = (Drill) build(state, map, BuildableType.DRILL, metalDeposit, Direction.LEFT);
        Drill coalDrill = (Drill) build(state, map, BuildableType.DRILL, coalDeposit, Direction.LEFT);

        // 2. Две добывающие линии к складу.
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

        // 3. Временный боевой стенд.
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
        check(state.removeBuilding(combatLane.tower), "Не удалось демонтировать тестовую башню");
        check(state.removeBuilding(combatLane.conveyor), "Не удалось демонтировать тестовый conveyor башни");

        // 4. Workshop ищется скользящим окном по кольцам вокруг базы.
        Tile workshopOrigin = findWorkshopOriginByExpandingRings(state, map);
        Workshop workshop = (Workshop) build(state, map, BuildableType.WORKSHOP,
                workshopOrigin, Direction.RIGHT);
        runUntil(state, 300,
                () -> state.getRoadmap().getFactoryState(workshop) != null,
                "RoadmapRuntime не зарегистрировал построенный Workshop");

        // Дожидаемся запаса на оба порта, пока угольная линия ещё кормит склад.
        int portCoal = BuildableType.FACTORY_INPUT_PORT.getCost(ResourceType.COAL)
                + BuildableType.FACTORY_OUTPUT_PORT.getCost(ResourceType.COAL);
        runUntil(state, 4_000,
                () -> state.getAvailableResource(ResourceType.COAL) >= portCoal,
                "Не накопили уголь на два Factory Port");

        // Отключаем старую угольную линию: теперь этот же Drill должен кормить Workshop.
        removeConveyor(state, map.getTile(10, 6));
        removeConveyor(state, map.getTile(9, 6));
        removeConveyor(state, map.getTile(8, 6));
        removeConveyor(state, map.getTile(7, 6));

        // 5. INPUT/OUTPUT выбираются из реально свободного периметра уже готового Workshop.
        PortPlan portPlan = findPortPlan(state, map, workshop, coalDrill);
        check(state.placeBuilding(BuildableType.FACTORY_INPUT_PORT, portPlan.inputTile, Direction.RIGHT),
                "Не удалось зарезервировать INPUT Port: "
                        + state.getBuildFailureReason(BuildableType.FACTORY_INPUT_PORT, portPlan.inputTile));
        check(state.placeBuilding(BuildableType.FACTORY_OUTPUT_PORT, portPlan.outputTile, Direction.RIGHT),
                "Не удалось зарезервировать OUTPUT Port: "
                        + state.getBuildFailureReason(BuildableType.FACTORY_OUTPUT_PORT, portPlan.outputTile));

        runUntil(state, MULTI_BUILD_TIMEOUT,
                () -> portPlan.inputTile.getBuilding() instanceof FactoryPort
                        && portPlan.outputTile.getBuilding() instanceof FactoryPort,
                "Construction Rover не завершил пару Factory Port");

        FactoryPort input = (FactoryPort) portPlan.inputTile.getBuilding();
        FactoryPort output = (FactoryPort) portPlan.outputTile.getBuilding();
        check(input.isInput() && !output.isInput(), "Factory ports построились с неверными ролями");
        check(input.getWorkshop() == workshop && output.getWorkshop() == workshop,
                "INPUT/OUTPUT ports не привязались к Workshop");

        // После стройки портов Rover мог остаться где угодно, поэтому внешние пути
        // вычисляем заново по текущей карте и резервируем целиком до следующего update().
        Set<Tile> reserved = identitySet();
        ExternalPaths paths = findNonOverlappingExternalPaths(
                state, map, coalDrill.getPosition(), input.getPosition(),
                output.getPosition(), state.getMainBuilding().getPosition(), reserved);
        check(paths != null, "После постройки портов не найдено двух внешних conveyor routes");

        int requiredMetal = (paths.inputPath.size() + paths.outputPath.size())
                * BuildableType.CONVEYOR.getCost(ResourceType.METAL);
        runUntil(state, 8_000,
                () -> state.getAvailableResource(ResourceType.METAL) >= requiredMetal,
                "Не накопили металл на динамические conveyor routes");

        queueConveyorPath(state, paths.inputPath, input.getPosition());
        queueConveyorPath(state, paths.outputPath, state.getMainBuilding().getPosition());
        runUntil(state, MULTI_BUILD_TIMEOUT,
                () -> conveyorPathBuilt(paths.inputPath) && conveyorPathBuilt(paths.outputPath),
                "Construction Rover не завершил динамические внешние conveyor routes");

        // 6. Вася входит внутрь; BOT строит путь между фактическими Gateway выбранных портов.
        state.getRoadmap().requestEnterWorkshop(workshop);
        runUntil(state, 3_000,
                () -> state.getRoadmap().getVasyaInsideWorkshop() == workshop,
                "Вася не смог физически войти в Workshop");
        buildInteriorLane(state, workshop, input, output);

        check(state.getConstructionQueue().isEmpty(), "После подготовки фабрики остались внешние стройки");

        // 7. Полный поток Drill -> INPUT -> interior -> OUTPUT -> Storage.
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

        int base = state.getMainBuilding().getPosition().getX();
        int workshopDistance = Math.max(
                Math.abs((workshop.getPosition().getX() + 1) - state.getMainBuilding().getPosition().getX()),
                Math.abs((workshop.getPosition().getY() + 1) - state.getMainBuilding().getPosition().getY()));

        System.out.println("NormalGameSimulationTest: OK"
                + "; miningTicks=" + miningTicks
                + ", combatTicks=" + combatTicks
                + ", workshopRing=" + workshopDistance
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

    private static final class PortPlan {
        private final Tile inputTile;
        private final Tile outputTile;

        private PortPlan(Tile inputTile, Tile outputTile) {
            this.inputTile = inputTile;
            this.outputTile = outputTile;
        }
    }

    private static final class ExternalPaths {
        private final List<Tile> inputPath;
        private final List<Tile> outputPath;

        private ExternalPaths(List<Tile> inputPath, List<Tile> outputPath) {
            this.inputPath = inputPath;
            this.outputPath = outputPath;
        }
    }
}
