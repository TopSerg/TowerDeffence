package towerdefence;

import towerdefence.building.BuildableType;
import towerdefence.building.FactoryPort;
import towerdefence.building.InteriorConveyor;
import towerdefence.building.Workshop;
import towerdefence.game.GameState;
import towerdefence.world.Direction;
import towerdefence.world.GameMap;
import towerdefence.world.Tile;
import towerdefence.world.TileType;

import java.awt.Point;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WorkshopInteriorFeatureTest {
    private static Tile tile(int x, int y) {
        return new Tile(x, y, TileType.GRASS, null, null, true);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Tile portTile(Tile anchor, Direction side, int slot) {
        switch (side) {
            case UP: return tile(anchor.getX() + slot, anchor.getY() - 1);
            case DOWN: return tile(anchor.getX() + slot, anchor.getY() + Workshop.HEIGHT_TILES);
            case LEFT: return tile(anchor.getX() - 1, anchor.getY() + slot);
            case RIGHT: return tile(anchor.getX() + Workshop.WIDTH_TILES, anchor.getY() + slot);
            default: throw new IllegalArgumentException("Неизвестная сторона");
        }
    }

    private static void checkGateway(Workshop workshop, Direction side, int slot) {
        FactoryPort port = new FactoryPort(portTile(workshop.getPosition(), side, slot), true);
        check(port.attachTo(workshop, side), "Порт не подключился: " + side + " slot=" + slot);
        List<Point> cells = port.getGatewayCells();
        check(cells.size() == 3, "Gateway должен занимать три внутренние клетки");
        for (int i = 0; i < 3; i++) {
            Point point = cells.get(i);
            int offset = slot * 3 + i;
            switch (side) {
                case UP:
                    check(point.equals(new Point(offset, 0)), "Неверный UP gateway: " + point);
                    break;
                case DOWN:
                    check(point.equals(new Point(offset, 8)), "Неверный DOWN gateway: " + point);
                    break;
                case LEFT:
                    check(point.equals(new Point(0, offset)), "Неверный LEFT gateway: " + point);
                    break;
                case RIGHT:
                    check(point.equals(new Point(8, offset)), "Неверный RIGHT gateway: " + point);
                    break;
            }
        }
        port.detach();
        check(port.getGatewayCells().isEmpty(), "После detach gateway должен исчезнуть");
        check(!workshop.getPorts().contains(port), "После detach порт остался в Workshop");
    }

    public static void main(String[] args) {
        Workshop workshop = new Workshop(tile(5, 5));
        check(workshop.getInteriorWidth() == 9 && workshop.getInteriorHeight() == 9,
                "Workshop должен иметь интерьер 9x9");
        check(workshop.getInterior().getConveyors().isEmpty(), "Новый интерьер должен быть пустым");

        check(workshop.placeInteriorConveyor(4, 4, Direction.RIGHT), "Конвейер не установился");
        check(!workshop.placeInteriorConveyor(4, 4, Direction.LEFT), "Можно занять одну клетку дважды");
        InteriorConveyor conveyor = workshop.getInteriorConveyor(4, 4);
        check(conveyor != null && conveyor.getDirection() == Direction.RIGHT, "Направление не сохранилось");
        conveyor.rotateClockwise();
        check(conveyor.getDirection() == Direction.DOWN, "Поворот внутреннего конвейера не работает");
        check(workshop.removeInteriorConveyor(4, 4), "Конвейер не удалился");
        check(workshop.getInteriorConveyor(4, 4) == null, "Удаление не освободило клетку");

        // Если порт достроили после временной внутренней раскладки, Gateway должен
        // освободить свои клетки и сохранить инвариант отсутствия наложений.
        check(workshop.placeInteriorConveyor(3, 0, Direction.RIGHT),
                "Не удалось подготовить конфликтующий конвейер для gateway-теста");
        FactoryPort conflictPort = new FactoryPort(portTile(workshop.getPosition(), Direction.UP, 1), true);
        check(conflictPort.attachTo(workshop, Direction.UP), "Конфликтный порт не подключился");
        check(workshop.getInteriorConveyor(3, 0) == null, "Gateway не освободил занятую граничную клетку");
        conflictPort.detach();

        Set<Point> allGatewayCells = new HashSet<>();
        for (Direction side : Direction.values()) {
            for (int slot = 0; slot < 3; slot++) {
                checkGateway(workshop, side, slot);

                FactoryPort port = new FactoryPort(portTile(workshop.getPosition(), side, slot), true);
                check(port.attachTo(workshop, side), "Порт не подключился для проверки уникальности");
                allGatewayCells.addAll(port.getGatewayCells());
                for (Point cell : port.getGatewayCells()) {
                    check(!workshop.placeInteriorConveyor(cell.x, cell.y, Direction.RIGHT),
                            "Gateway разрешил построить конвейер поверх себя");
                }
                port.detach();
            }
        }
        check(allGatewayCells.size() == 32,
                "Периметр 9x9 должен давать 32 уникальные граничные клетки, получено " + allGatewayCells.size());

        GameMap map = new GameMap(20, 20);
        GameState state = new GameState(map);
        Workshop managedWorkshop = null;
        FactoryPort managedPort = null;
        outer:
        for (int y = 1; y < map.getHeight() - Workshop.HEIGHT_TILES - 1; y++) {
            for (int x = 1; x < map.getWidth() - Workshop.WIDTH_TILES - 1; x++) {
                Tile anchor = map.getTile(x, y);
                if (!state.canPlaceBuilding(BuildableType.WORKSHOP, anchor)) continue;
                Workshop candidate = new Workshop(anchor);
                if (!state.addBuilding(candidate, anchor)) continue;
                for (Direction side : Direction.values()) {
                    for (int slot = 0; slot < 3; slot++) {
                        Tile portCoordinates = portTile(anchor, side, slot);
                        Tile portPosition = map.getTile(portCoordinates.getX(), portCoordinates.getY());
                        if (portPosition == null || !state.canPlaceBuilding(BuildableType.FACTORY_INPUT_PORT, portPosition)) continue;
                        FactoryPort candidatePort = new FactoryPort(portPosition, true);
                        if (state.addBuilding(candidatePort, portPosition)) {
                            managedWorkshop = candidate;
                            managedPort = candidatePort;
                            break outer;
                        }
                    }
                }
                state.removeBuilding(candidate);
            }
        }
        check(managedWorkshop != null && managedPort != null, "Не удалось собрать Workshop с портом для lifecycle-теста");
        check(managedPort.getWorkshop() == managedWorkshop, "GameState не привязал внешний порт к Workshop");
        check(state.removeBuilding(managedWorkshop), "Workshop не удалился через GameState");
        check(managedPort.getWorkshop() == null, "После удаления Workshop осталась dangling-ссылка порта");
        check(!state.getAllBuildings().contains(managedPort), "После удаления Workshop порт остался на карте");

        System.out.println("WorkshopInteriorFeatureTest: OK; gateway perimeter=" + allGatewayCells.size());
    }
}
