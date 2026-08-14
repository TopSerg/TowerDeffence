package towerdefence;

import towerdefence.building.Conveyor;
import towerdefence.building.Drill;
import towerdefence.building.FactoryPort;
import towerdefence.building.Workshop;
import towerdefence.building.WorkshopItem;
import towerdefence.game.WorkshopGameState;
import towerdefence.resource.Resource;
import towerdefence.resource.ResourceType;
import towerdefence.world.Direction;
import towerdefence.world.GameMap;
import towerdefence.world.Tile;
import towerdefence.world.TileType;

import java.awt.Point;

public class WorkshopThroughputTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Tile tile(int x, int y) {
        return new Tile(x, y, TileType.DIRT, null, null, true);
    }

    private static void buildStraightInterior(Workshop workshop, int y, Direction direction) {
        for (int x = 1; x <= 7; x++) {
            check(workshop.placeInteriorConveyor(x, y, direction),
                    "Не удалось построить внутренний конвейер " + x + "," + y);
        }
    }

    private static void coreGatewayThroughput() {
        Workshop workshop = new Workshop(tile(5, 5));
        FactoryPort input = new FactoryPort(tile(8, 6), true);
        FactoryPort output = new FactoryPort(tile(4, 6), false);
        check(input.attachTo(workshop, Direction.RIGHT), "INPUT не подключился");
        check(output.attachTo(workshop, Direction.LEFT), "OUTPUT не подключился");

        buildStraightInterior(workshop, 3, Direction.LEFT);
        buildStraightInterior(workshop, 4, Direction.LEFT);
        buildStraightInterior(workshop, 5, Direction.LEFT);

        input.beginLogisticsTick();
        check(input.acceptExternalResource(ResourceType.METAL), "Первый предмет не принят INPUT Port");
        check(!input.acceptExternalResource(ResourceType.COAL),
                "Один внешний порт принял два предмета за один логистический такт");
        workshop.advanceInteriorTransport();
        check(workshop.getInteriorItems().size() == 1, "Предмет не появился на Gateway");
        WorkshopItem first = workshop.getInteriorItems().get(0);
        check(first.getX() == 8 && first.getY() == 3, "Первый предмет должен попасть на первую из трёх линий");

        input.beginLogisticsTick();
        check(input.acceptExternalResource(ResourceType.COAL), "Второй такт не открыл входной порт");
        workshop.advanceInteriorTransport();
        WorkshopItem newest = workshop.getInteriorItems().get(workshop.getInteriorItems().size() - 1);
        check(newest.getX() == 8 && newest.getY() == 4, "Gateway не распределил второй предмет на следующую линию");

        input.beginLogisticsTick();
        check(input.acceptExternalResource(ResourceType.SCRAP), "Третий такт не открыл входной порт");
        workshop.advanceInteriorTransport();
        newest = workshop.getInteriorItems().get(workshop.getInteriorItems().size() - 1);
        check(newest.getX() == 8 && newest.getY() == 5, "Gateway не использовал третью линию");

        for (int tick = 0; tick < 12; tick++) workshop.advanceInteriorTransport();
        check(output.getBufferSize() == 3, "Три ресурса не дошли до OUTPUT Gateway");
        check(workshop.getInteriorItems().isEmpty(), "После выхода предметы остались во внутренней сетке");

        output.beginLogisticsTick();
        check(output.peekOutputResource() == ResourceType.METAL, "Порядок ресурсов в OUTPUT буфере повреждён");
        check(output.commitOutputToExternal() == ResourceType.METAL, "OUTPUT не выдал первый предмет");
        check(output.peekOutputResource() == null,
                "OUTPUT Port выдал больше одной единицы за один внешний логистический такт");
        output.beginLogisticsTick();
        check(output.peekOutputResource() == ResourceType.COAL, "После нового такта OUTPUT-не открылся");
    }

    private static void integrationPassThrough() {
        GameMap map = new GameMap(20, 20);
        WorkshopGameState state = new WorkshopGameState(map);
        state.getAllUnits().get(0).setAlive(false); // исключаем автодобычу Васи из проверки склада

        // Принудительно освобождаем детерминированный коридор теста от случайных месторождений.
        for (int y = 4; y <= 6; y++) {
            for (int x = 6; x <= 16; x++) {
                Tile t = map.getTile(x, y);
                if (t != null && t.getBuilding() == null && t.getUnit() == null) {
                    t.setType(TileType.DIRT);
                    t.setPassable(true);
                    t.setResource(null);
                }
            }
        }

        Tile workshopTile = map.getTile(10, 4);
        Workshop workshop = new Workshop(workshopTile);
        check(state.addBuilding(workshop, workshopTile), "Workshop не добавился в интеграционный тест");

        FactoryPort output = new FactoryPort(map.getTile(9, 5), false);
        FactoryPort input = new FactoryPort(map.getTile(13, 5), true);
        check(state.addBuilding(output, output.getPosition()), "OUTPUT Port не добавился");
        check(state.addBuilding(input, input.getPosition()), "INPUT Port не добавился");
        check(output.getWorkshop() == workshop && input.getWorkshop() == workshop,
                "Порты не привязались к Workshop");

        buildStraightInterior(workshop, 4, Direction.LEFT);

        check(state.addBuilding(new Conveyor(map.getTile(8, 5), Direction.LEFT), map.getTile(8, 5)),
                "Выходной conveyor #1 не добавился");
        check(state.addBuilding(new Conveyor(map.getTile(7, 5), Direction.LEFT), map.getTile(7, 5)),
                "Выходной conveyor #2 не добавился");
        check(state.addBuilding(new Conveyor(map.getTile(14, 5), Direction.LEFT), map.getTile(14, 5)),
                "Входной conveyor #1 не добавился");
        check(state.addBuilding(new Conveyor(map.getTile(15, 5), Direction.LEFT), map.getTile(15, 5)),
                "Входной conveyor #2 не добавился");

        Tile drillTile = map.getTile(16, 5);
        drillTile.setResource(new Resource(ResourceType.METAL, 50));
        Drill drill = new Drill(drillTile);
        check(state.addBuilding(drill, drillTile), "Drill не добавился");

        int metalBefore = state.getInventory().getAmount(ResourceType.METAL);
        boolean sawInteriorItem = false;
        for (int tick = 0; tick < 420; tick++) {
            state.update();
            if (!workshop.getInteriorItems().isEmpty()) sawInteriorItem = true;
        }
        int delivered = state.getInventory().getAmount(ResourceType.METAL) - metalBefore;
        check(sawInteriorItem, "Ресурс ни разу не появился внутри Workshop");
        check(delivered > 0, "Ресурс не прошёл цепочку Drill → Workshop → Storage");
        check(drill.isOutputConnected(), "Drill не видит путь до INPUT Port");
        check(input.isExternalConnected(), "INPUT Port не видит внешний conveyor");
        check(output.isExternalConnected(), "OUTPUT Port не видит путь до Storage");

        System.out.println("WorkshopThroughputTest: OK; delivered=" + delivered
                + ", inputBuffer=" + input.getBufferSize() + ", outputBuffer=" + output.getBufferSize());
    }

    public static void main(String[] args) {
        coreGatewayThroughput();
        integrationPassThrough();
    }
}
