package towerdefence;

import towerdefence.building.BuildableType;
import towerdefence.building.ConstructionSite;
import towerdefence.building.Wall;
import towerdefence.game.GameState;
import towerdefence.resource.Inventory;
import towerdefence.resource.Resource;
import towerdefence.resource.ResourceType;
import towerdefence.unit.Worker;
import towerdefence.world.Direction;
import towerdefence.world.GameMap;
import towerdefence.world.Tile;

public class Stage3FeatureTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Tile findFree(GameState state, GameMap map, BuildableType type,
                                 Worker worker, int skip) {
        int seen = 0;
        for (int radius = 1; radius < 12; radius++) {
            for (int y = 1; y < map.getHeight() - 1; y++) {
                for (int x = 1; x < map.getWidth() - 1; x++) {
                    Tile tile = map.getTile(x, y);
                    int distance = Math.abs(x - worker.getPosition().getX())
                            + Math.abs(y - worker.getPosition().getY());
                    if (distance > radius || !state.canPlaceBuilding(type, tile)) continue;
                    if (state.findBestAdjacentTile(worker.getPosition(), tile, worker) == null) continue;
                    if (seen++ >= skip) return tile;
                }
            }
        }
        throw new AssertionError("Нет достижимой клетки для " + type);
    }

    public static void main(String[] args) {
        Inventory inventory = new Inventory(5);
        check(inventory.add(ResourceType.METAL, 3), "Безопасное добавление не работает");
        check(!inventory.add(ResourceType.COAL, 3), "Инвентарь переполнился");
        check(inventory.addUpToCapacity(ResourceType.COAL, 3) == 2, "Частичное добавление неверно");
        check(inventory.removeUpTo(ResourceType.METAL, 10) == 3, "Частичное удаление неверно");
        check(inventory.getStoredAmount() == 2, "Счётчик заполнения повреждён");

        GameMap map = new GameMap(20, 20);
        GameState state = new GameState(map);
        check(state.getAllUnits().get(0) instanceof Worker, "Стартовый юнит не рабочий");
        Worker worker = (Worker) state.getAllUnits().get(0);
        check(worker.getInventory().getSize() == Worker.INVENTORY_CAPACITY, "Неверная вместимость рабочего");

        int metalBeforeMining = state.getInventory().getAmount(ResourceType.METAL);
        int coalBeforeMining = state.getInventory().getAmount(ResourceType.COAL);
        for (int i = 0; i < 900; i++) state.update();
        int gathered = state.getInventory().getAmount(ResourceType.METAL) - metalBeforeMining
                + state.getInventory().getAmount(ResourceType.COAL) - coalBeforeMining;
        check(gathered > 0, "Рабочий не добыл и не разгрузил ресурсы");

        Tile first = findFree(state, map, BuildableType.WALL, worker, 0);
        Tile second = findFree(state, map, BuildableType.WALL, worker, 1);
        int availableMetalBefore = state.getAvailableResource(ResourceType.METAL);
        check(state.placeBuilding(BuildableType.WALL, first, Direction.RIGHT), "Первый чертёж не размещён");
        check(state.placeBuilding(BuildableType.WALL, second, Direction.RIGHT), "Второй чертёж не размещён");
        check(first.getBuilding() instanceof ConstructionSite, "Здание появилось мгновенно вместо чертежа");
        check(state.getConstructionQueue().size() == 2, "Очередь работ не создана");
        check(state.getAvailableResource(ResourceType.METAL) == availableMetalBefore - 16,
                "Стоимость чертежей не зарезервирована");

        int firstCompleteTick = -1;
        int secondCompleteTick = -1;
        for (int tick = 0; tick < 1800 && secondCompleteTick < 0; tick++) {
            state.update();
            if (firstCompleteTick < 0 && first.getBuilding() instanceof Wall) firstCompleteTick = tick;
            if (secondCompleteTick < 0 && second.getBuilding() instanceof Wall) secondCompleteTick = tick;
        }
        check(firstCompleteTick >= 0, "Первый объект не построен");
        check(secondCompleteTick >= 0, "Второй объект не построен");
        check(firstCompleteTick <= secondCompleteTick, "План работ выполнен не по очереди");
        check(state.getConstructionQueue().isEmpty(), "Завершённые работы остались в очереди");

        Tile deposit = state.findNearestMineableResource(worker.getPosition(), null);
        check(deposit != null && deposit.hasResource(), "На карте нет полноценного месторождения");
        check(worker.assignPreferredDeposit(deposit), "Рабочему нельзя назначить месторождение");

        Resource finite = new Resource(ResourceType.COAL, 3);
        check(finite.extract(2) == 2 && finite.getAmount() == 1, "Извлечение ресурса неверно");
        check(finite.extract(5) == 1 && finite.isDepleted(), "Истощение ресурса неверно");

        System.out.println("Stage3FeatureTest: OK; mining=" + gathered
                + ", first=" + firstCompleteTick + ", second=" + secondCompleteTick);
    }
}
