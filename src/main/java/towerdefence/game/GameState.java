package towerdefence.game;

import towerdefence.building.BuildableType;
import towerdefence.building.Building;
import towerdefence.building.ConstructionSite;
import towerdefence.building.Conveyor;
import towerdefence.building.Drill;
import towerdefence.building.FactoryPort;
import towerdefence.building.House;
import towerdefence.building.Wall;
import towerdefence.building.Workshop;
import towerdefence.combat.CombatTower;
import towerdefence.combat.Enemy;
import towerdefence.combat.EnemySpawnPoint;
import towerdefence.combat.EnemyType;
import towerdefence.combat.MachineGunTower;
import towerdefence.combat.SlowTower;
import towerdefence.combat.SniperTower;
import towerdefence.combat.TowerModuleType;
import towerdefence.combat.WaveManager;
import towerdefence.resource.Inventory;
import towerdefence.resource.ResourceType;
import towerdefence.unit.Unit;
import towerdefence.unit.Worker;
import towerdefence.world.Direction;
import towerdefence.world.GameMap;
import towerdefence.world.Pathfinder;
import towerdefence.world.Tile;
import towerdefence.world.TileType;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GameState {
    private static final int LOGISTICS_INTERVAL_TICKS = 15;

    private final List<Unit> allUnits = new ArrayList<>();
    private final List<Building> allBuildings = new ArrayList<>();
    private final List<Enemy> allEnemies = new ArrayList<>();
    private final List<ConstructionSite> constructionQueue = new ArrayList<>();
    private final Map<ConstructionSite, EnumMap<ResourceType, Integer>> reservationsBySite = new HashMap<>();
    private final GameMap map;

    private House mainBuilding;
    private EnemySpawnPoint enemySpawnPoint;
    private WaveManager waveManager;
    private GameStatus status = GameStatus.RUNNING;
    private int logisticsTick;
    private int destroyedEnemies;
    private int totalScrapCollected;

    public GameState(GameMap map) {
        if (map == null) throw new IllegalArgumentException("Карта не может быть null");
        this.map = map;
        spawnInitialEntities();
    }

    /** Стартовые координаты сохранены: рабочий (5,5), база (6,5), спавн (2,3). */
    private void spawnInitialEntities() {
        Tile workerTile = map.getTile(5, 5);
        Worker worker = new Worker(workerTile, map, this);
        addUnit(worker, workerTile);

        Tile houseTile = map.getTile(6, 5);
        mainBuilding = new House(houseTile, Color.RED);
        addBuildingInternal(mainBuilding, houseTile);

        Tile spawnTile = map.getTile(2, 3);
        spawnTile.setResource(null);
        enemySpawnPoint = new EnemySpawnPoint(spawnTile);
        waveManager = new WaveManager();
    }

    public void addUnit(Unit unit, Tile tile) {
        if (unit == null || tile == null || tile.hasUnit() || tile.hasBuilding()) return;
        allUnits.add(unit);
        tile.setUnit(unit);
        unit.setPosition(tile);
    }

    private boolean addBuildingInternal(Building building, Tile tile) {
        if (building == null || tile == null) return false;
        for (Tile occupied : getFootprintTiles(tile, building.getFootprintWidth(), building.getFootprintHeight())) {
            if (occupied == null || occupied.hasBuilding() || occupied.hasUnit()) return false;
        }
        allBuildings.add(building);
        building.setPosition(tile);
        for (Tile occupied : getFootprintTiles(tile, building.getFootprintWidth(), building.getFootprintHeight())) {
            occupied.setBuilding(building);
            occupied.setPassable(occupied.getType() != TileType.WATER && !building.blocksMovement());
        }
        if (building instanceof FactoryPort) {
            attachFactoryPort((FactoryPort) building);
        }
        return true;
    }

    public boolean addBuilding(Building building, Tile tile) {
        if (building == null || tile == null || !canUseTileCommon(tile)) return false;
        boolean added = addBuildingInternal(building, tile);
        if (added) markEnemyPathsDirty();
        return added;
    }

    public boolean removeBuilding(Building building) {
        if (status != GameStatus.RUNNING || building == null || building == mainBuilding) return false;
        if (building instanceof ConstructionSite) {
            return cancelConstruction((ConstructionSite) building, true);
        }
        if (building instanceof Workshop) {
            for (Building other : new ArrayList<>(allBuildings)) {
                if (other instanceof FactoryPort && ((FactoryPort) other).getWorkshop() == building) {
                    removeBuilding(other);
                }
            }
        }
        if (!allBuildings.remove(building)) return false;
        if (building instanceof FactoryPort) ((FactoryPort) building).detach();
        releaseBuildingTile(building);
        building.setAlive(false);
        resetLogisticsIndicators();
        markEnemyPathsDirty();
        return true;
    }

    /** Размещает чертёж. Стоимость резервируется, но остаётся на складе до приезда рабочего. */
    public boolean placeBuilding(BuildableType type, Tile tile, Direction direction) {
        if (status != GameStatus.RUNNING || type == null) return false;
        if (!canPlaceBuilding(type, tile) || !canAfford(type)) return false;

        ConstructionSite site = new ConstructionSite(tile, type,
                direction == null ? Direction.RIGHT : direction);
        if (!addBuildingInternal(site, tile)) return false;
        reserveForSite(site);
        constructionQueue.add(site);
        markEnemyPathsDirty();
        return true;
    }

    private Building createFinishedBuilding(BuildableType type, Tile tile, Direction direction) {
        switch (type) {
            case MACHINE_GUN_TOWER: return new MachineGunTower(tile);
            case SNIPER_TOWER: return new SniperTower(tile);
            case SLOW_TOWER: return new SlowTower(tile);
            case WALL: return new Wall(tile);
            case CONVEYOR: return new Conveyor(tile, direction == null ? Direction.RIGHT : direction);
            case DRILL: return new Drill(tile);
            case WORKSHOP: return new Workshop(tile);
            case FACTORY_INPUT_PORT: return new FactoryPort(tile, true);
            case FACTORY_OUTPUT_PORT: return new FactoryPort(tile, false);
            default: return null;
        }
    }

    public boolean completeConstruction(ConstructionSite site, Worker worker) {
        if (site == null || !site.isAlive() || !site.isComplete()
                || !constructionQueue.contains(site) || !allBuildings.contains(site)) return false;

        Tile tile = site.getPosition();
        constructionQueue.remove(site);
        reservationsBySite.remove(site);
        allBuildings.remove(site);
        // Освобождаем весь footprint чертежа (например, 3x3 у Workshop),
        // иначе оставшиеся клетки всё ещё содержат ConstructionSite и
        // готовое здание не может занять своё место.
        releaseBuildingTile(site);
        site.setAlive(false);
        site.setAssignedWorker(null);

        Building finished;
        try {
            finished = createFinishedBuilding(site.getTargetType(), tile, site.getDirection());
        } catch (RuntimeException exception) {
            mainBuilding.refund(site.getDeliveredInventory().getResources());
            if (tile != null) tile.setPassable(tile.getType() != TileType.WATER);
            return false;
        }
        if (finished == null || !addBuildingInternal(finished, tile)) {
            mainBuilding.refund(site.getDeliveredInventory().getResources());
            if (tile != null) tile.setPassable(tile.getType() != TileType.WATER);
            return false;
        }

        resetLogisticsIndicators();
        markEnemyPathsDirty();
        return true;
    }

    private boolean cancelConstruction(ConstructionSite site, boolean refundDelivered) {
        if (site == null || !allBuildings.remove(site)) return false;
        constructionQueue.remove(site);
        reservationsBySite.remove(site); // оставшиеся материалы снова становятся свободными
        if (refundDelivered && mainBuilding != null) {
            mainBuilding.refund(site.getDeliveredInventory().getResources());
        }
        for (Unit unit : allUnits) {
            if (unit instanceof Worker) ((Worker) unit).onConstructionCancelled(site);
        }
        releaseBuildingTile(site);
        site.setAlive(false);
        resetLogisticsIndicators();
        markEnemyPathsDirty();
        return true;
    }

    private void reserveForSite(ConstructionSite site) {
        EnumMap<ResourceType, Integer> reserved = new EnumMap<>(ResourceType.class);
        reserved.putAll(site.getRequired());
        reservationsBySite.put(site, reserved);
    }

    /** Загружает в рабочий инвентарь только ресурсы, зарезервированные за данным чертежом. */
    public int loadReservedMaterials(ConstructionSite site, Inventory workerInventory) {
        if (site == null || workerInventory == null || workerInventory.isFull()) return 0;
        EnumMap<ResourceType, Integer> reserved = reservationsBySite.get(site);
        if (reserved == null) return 0;

        int loaded = 0;
        for (ResourceType type : ResourceType.values()) {
            int outstanding = reserved.getOrDefault(type, 0);
            if (outstanding <= 0 || workerInventory.isFull()) continue;
            int amount = Math.min(outstanding, workerInventory.getFreeSpace());
            amount = Math.min(amount, mainBuilding.getInventory().getAmount(type));
            if (amount <= 0) continue;
            int removed = mainBuilding.getInventory().removeUpTo(type, amount);
            int accepted = workerInventory.addUpToCapacity(type, removed);
            if (accepted < removed) mainBuilding.getInventory().addUpToCapacity(type, removed - accepted);
            if (accepted > 0) {
                reserved.put(type, outstanding - accepted);
                loaded += accepted;
            }
        }
        return loaded;
    }

    public ConstructionSite claimNextConstruction(Worker worker) {
        if (worker == null) return null;
        for (ConstructionSite site : constructionQueue) {
            if (!site.isAlive() || site.isComplete()) continue;
            if (site.getAssignedWorker() == null || site.getAssignedWorker() == worker) {
                site.setAssignedWorker(worker);
                return site;
            }
        }
        return null;
    }

    public boolean hasPendingConstruction() {
        for (ConstructionSite site : constructionQueue) if (site.isAlive() && !site.isComplete()) return true;
        return false;
    }

    public boolean isConstructionPending(ConstructionSite site) {
        return site != null && site.isAlive() && constructionQueue.contains(site);
    }

    public boolean canAfford(BuildableType type) {
        return type != null && canAffordAvailable(type.getCost());
    }

    public boolean canAfford(TowerModuleType module) {
        return module != null && mainBuilding != null && mainBuilding.isAlive()
                && canAffordAvailable(module.getCost());
    }

    private boolean canAffordAvailable(Map<ResourceType, Integer> cost) {
        if (mainBuilding == null || !mainBuilding.isAlive() || cost == null) return false;
        for (Map.Entry<ResourceType, Integer> entry : cost.entrySet()) {
            if (getAvailableResource(entry.getKey()) < entry.getValue()) return false;
        }
        return true;
    }

    private boolean payAvailable(Map<ResourceType, Integer> cost) {
        if (!canAffordAvailable(cost)) return false;
        for (Map.Entry<ResourceType, Integer> entry : cost.entrySet()) {
            mainBuilding.getInventory().remove(entry.getKey(), entry.getValue());
        }
        return true;
    }

    public int getReservedResource(ResourceType type) {
        int total = 0;
        for (EnumMap<ResourceType, Integer> reservation : reservationsBySite.values()) {
            total += reservation.getOrDefault(type, 0);
        }
        return total;
    }

    public int getAvailableResource(ResourceType type) {
        if (mainBuilding == null) return 0;
        return Math.max(0, mainBuilding.getInventory().getAmount(type) - getReservedResource(type));
    }

    public boolean installTowerModule(CombatTower tower, TowerModuleType module) {
        if (status != GameStatus.RUNNING || tower == null || module == null
                || !tower.isAlive() || !allBuildings.contains(tower)
                || !tower.canInstallModule(module) || !canAfford(module)) return false;
        if (!payAvailable(module.getCost())) return false;
        if (!tower.installModule(module)) {
            mainBuilding.refund(module.getCost());
            return false;
        }
        return true;
    }

    public String getModuleFailureReason(CombatTower tower, TowerModuleType module) {
        if (status != GameStatus.RUNNING) return "Улучшения недоступны после завершения игры";
        if (tower == null || !tower.isAlive() || !allBuildings.contains(tower)) return "Башня не выбрана";
        if (!tower.canInstallModule(module)) return tower.getModuleFailureReason(module);
        if (!canAfford(module)) return "Не хватает свободных ресурсов: " + module.getCostText();
        return "Модуль сейчас установить нельзя";
    }

    public Map<ResourceType, Integer> getTowerSaleRefund(CombatTower tower) {
        EnumMap<ResourceType, Integer> refund = new EnumMap<>(ResourceType.class);
        if (tower == null) return refund;
        BuildableType baseType = BuildableType.fromBuilding(tower);
        if (baseType == null) return refund;
        addRefundPart(refund, baseType.getCost(), 0.60);
        for (TowerModuleType module : tower.getInstalledModules()) addRefundPart(refund, module.getCost(), 0.50);
        return refund;
    }

    private void addRefundPart(EnumMap<ResourceType, Integer> refund,
                               Map<ResourceType, Integer> cost, double fraction) {
        for (Map.Entry<ResourceType, Integer> entry : cost.entrySet()) {
            int amount = (int) Math.floor(entry.getValue() * fraction);
            if (amount > 0) refund.merge(entry.getKey(), amount, Integer::sum);
        }
    }

    public Map<ResourceType, Integer> sellTower(CombatTower tower) {
        if (status != GameStatus.RUNNING || tower == null || !tower.isAlive()
                || !allBuildings.contains(tower)) return Collections.emptyMap();
        Map<ResourceType, Integer> refund = getTowerSaleRefund(tower);
        if (!removeBuilding(tower)) return Collections.emptyMap();
        mainBuilding.refund(refund);
        return Collections.unmodifiableMap(new EnumMap<>(refund));
    }

    public boolean canPlaceBuilding(BuildableType type, Tile tile) {
        if (type == null || tile == null) return false;
        if (type == BuildableType.DRILL) {
            if (!canUseTileCommon(tile) || !tile.isPassable() || !tile.hasResource()) return false;
            ResourceType resourceType = tile.getResource().getType();
            return resourceType == ResourceType.METAL || resourceType == ResourceType.COAL;
        }
        if (type == BuildableType.FACTORY_INPUT_PORT || type == BuildableType.FACTORY_OUTPUT_PORT) {
            if (!canUseTileCommon(tile) || !tile.isPassable() || tile.hasResource()) return false;
            return findAdjacentWorkshop(tile) != null;
        }
        for (Tile footprintTile : getFootprintTiles(tile, type.getFootprintWidth(), type.getFootprintHeight())) {
            if (!canUseTileCommon(footprintTile) || !footprintTile.isPassable() || footprintTile.hasResource()) {
                return false;
            }
        }
        return true;
    }

    public boolean canBuildOn(Tile tile) {
        return canUseTileCommon(tile) && tile.isPassable() && !tile.hasResource();
    }

    private boolean canUseTileCommon(Tile tile) {
        return status == GameStatus.RUNNING && tile != null
                && (enemySpawnPoint == null || enemySpawnPoint.getPosition() != tile)
                && tile.getType() != TileType.WATER
                && !tile.hasBuilding() && !tile.hasUnit() && !isEnemyOnTile(tile);
    }

    public String getBuildFailureReason(BuildableType type, Tile tile) {
        if (status != GameStatus.RUNNING) return "Строительство недоступно после завершения игры";
        if (type == null || tile == null) return "Не выбрана клетка или постройка";
        if (!canAfford(type)) return "Не хватает свободных ресурсов: " + type.getCostText();
        if (type == BuildableType.DRILL) {
            if (enemySpawnPoint != null && enemySpawnPoint.getPosition() == tile) return "Нельзя строить на точке появления противников";
            if (tile.getType() == TileType.WATER) return "На воде строить нельзя";
            if (tile.hasBuilding() || tile.hasUnit() || isEnemyOnTile(tile)) return "Клетка занята";
            if (!tile.hasResource()) return "Бур устанавливается только на месторождение металла или угля";
            ResourceType resourceType = tile.getResource().getType();
            if (resourceType != ResourceType.METAL && resourceType != ResourceType.COAL) {
                return "Это месторождение пока нельзя разрабатывать";
            }
            return "Здесь строить нельзя";
        }
        if (type == BuildableType.FACTORY_INPUT_PORT || type == BuildableType.FACTORY_OUTPUT_PORT) {
            if (tile.getType() == TileType.WATER) return "На воде строить нельзя";
            if (tile.hasBuilding() || tile.hasUnit() || isEnemyOnTile(tile)) return "Клетка занята";
            if (tile.hasResource()) return "Порт нельзя ставить поверх месторождения";
            if (findAdjacentWorkshop(tile) == null) return "Порт можно ставить только вплотную к Workshop";
            return "Здесь строить нельзя";
        }
        for (Tile footprintTile : getFootprintTiles(tile, type.getFootprintWidth(), type.getFootprintHeight())) {
            if (footprintTile == null) return "Постройка не помещается на карте";
            if (enemySpawnPoint != null && enemySpawnPoint.getPosition() == footprintTile) return "Нельзя строить на точке появления противников";
            if (footprintTile.getType() == TileType.WATER) return "На воде строить нельзя";
            if (footprintTile.hasBuilding() || footprintTile.hasUnit() || isEnemyOnTile(footprintTile)) return "Часть площадки занята";
            if (footprintTile.hasResource()) return "Обычные здания не строятся поверх месторождения";
            if (!footprintTile.isPassable()) return "Часть площадки недоступна";
        }
        return "Здесь строить нельзя";
    }

    private boolean isEnemyOnTile(Tile tile) {
        for (Enemy enemy : allEnemies) {
            if (!enemy.isAlive()) continue;
            double dx = enemy.getRealX() - tile.getX();
            double dy = enemy.getRealY() - tile.getY();
            if (dx * dx + dy * dy < 0.58 * 0.58) return true;
        }
        return false;
    }

    /** Находит ближайшее доступное месторождение металла или угля. */
    public Tile findNearestMineableResource(Tile start, Tile preferred) {
        if (isMineable(preferred)) return preferred;
        Tile best = null;
        int bestDistance = Integer.MAX_VALUE;
        Pathfinder pathfinder = new Pathfinder(map);
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                if (!isMineable(tile)) continue;
                List<Tile> path = pathfinder.findPath(start, tile);
                if (path != null && path.size() < bestDistance) {
                    bestDistance = path.size();
                    best = tile;
                }
            }
        }
        return best;
    }

    private boolean isMineable(Tile tile) {
        if (tile == null || !tile.hasResource() || tile.hasBuilding()) return false;
        ResourceType type = tile.getResource().getType();
        return type == ResourceType.METAL || type == ResourceType.COAL;
    }

    /** Выбирает достижимую соседнюю клетку рядом со зданием или чертежом. */
    public Tile findBestAdjacentTile(Tile start, Tile target, Unit movingUnit) {
        if (start == null || target == null) return null;
        Pathfinder pathfinder = new Pathfinder(map);
        Tile best = null;
        int bestLength = Integer.MAX_VALUE;
        for (Tile candidate : getAdjacentTilesAroundTarget(target)) {
            if (candidate == null || !candidate.isPassable() || candidate.hasBuilding()) continue;
            if (candidate.hasUnit() && candidate.getUnit() != movingUnit) continue;
            if (candidate == start) return candidate;
            List<Tile> path = pathfinder.findPath(start, candidate);
            if (path != null && path.size() < bestLength) {
                best = candidate;
                bestLength = path.size();
            }
        }
        return best;
    }

    public boolean spawnTestEnemy() {
        EnemyType type = EnemyType.NORMAL;
        return spawnEnemy(type.getBaseHealth(), type.getBaseSpeed(), type.getBaseDamage(), type);
    }

    public boolean spawnWaveEnemy(int wave, EnemyType type) {
        EnemyType actualType = type == null ? EnemyType.NORMAL : type;
        float healthScale = 1.0f + Math.max(0, wave - 1) * 0.14f;
        float speedScale = 1.0f + Math.max(0, wave - 1) * 0.025f;
        int health = Math.max(1, Math.round(actualType.getBaseHealth() * healthScale));
        float speed = actualType.getBaseSpeed() * speedScale;
        int damage = actualType.getBaseDamage() + Math.max(0, wave - 1) * 2;
        return spawnEnemy(health, speed, damage, actualType);
    }

    private boolean spawnEnemy(int health, float speed, int damage, EnemyType type) {
        if (status != GameStatus.RUNNING || enemySpawnPoint == null || !mainBuilding.isAlive()) return false;
        Tile spawnTile = enemySpawnPoint.getPosition();
        for (Enemy enemy : allEnemies) {
            if (!enemy.isAlive()) continue;
            double dx = enemy.getRealX() - spawnTile.getX();
            double dy = enemy.getRealY() - spawnTile.getY();
            if (dx * dx + dy * dy < 0.45 * 0.45) return false;
        }
        allEnemies.add(new Enemy(health, speed, damage, map, spawnTile, mainBuilding, type));
        return true;
    }

    public void update() {
        if (status != GameStatus.RUNNING) return;

        for (Unit unit : new ArrayList<>(allUnits)) if (unit.isAlive()) unit.update();
        for (Building building : new ArrayList<>(allBuildings)) if (building.isAlive()) building.update();
        for (Enemy enemy : new ArrayList<>(allEnemies)) if (enemy.isAlive()) enemy.update();

        cleanupDestroyedBuildings();
        if (!mainBuilding.isAlive()) {
            status = GameStatus.DEFEAT;
            return;
        }

        updateTowerCombat();
        cleanupDestroyedEnemies();
        if (enemySpawnPoint != null) enemySpawnPoint.update();

        logisticsTick++;
        if (logisticsTick >= LOGISTICS_INTERVAL_TICKS) {
            logisticsTick = 0;
            updateLogistics();
        }
        waveManager.update(this);
    }

    private void updateTowerCombat() {
        for (Building building : new ArrayList<>(allBuildings)) {
            if (building instanceof CombatTower && building.isAlive()) {
                ((CombatTower) building).updateCombat(allEnemies);
            }
        }
    }

    private void cleanupDestroyedEnemies() {
        Iterator<Enemy> iterator = allEnemies.iterator();
        while (iterator.hasNext()) {
            Enemy enemy = iterator.next();
            if (enemy.isAlive()) continue;
            destroyedEnemies++;
            int reward = enemy.getScrapReward();
            int accepted = mainBuilding.getInventory().addUpToCapacity(ResourceType.SCRAP, reward);
            totalScrapCollected += accepted;
            iterator.remove();
        }
    }

    private void cleanupDestroyedBuildings() {
        boolean navigationChanged = false;
        for (Building building : new ArrayList<>(allBuildings)) {
            if (building.isAlive() || building == mainBuilding) continue;
            if (building instanceof ConstructionSite) {
                cancelConstruction((ConstructionSite) building, false);
            } else if (allBuildings.remove(building)) {
                if (building instanceof Workshop) {
                    for (Building other : new ArrayList<>(allBuildings)) {
                        if (other instanceof FactoryPort && ((FactoryPort) other).getWorkshop() == building) {
                            allBuildings.remove(other);
                            ((FactoryPort) other).detach();
                            releaseBuildingTile(other);
                        }
                    }
                } else if (building instanceof FactoryPort) {
                    ((FactoryPort) building).detach();
                }
                releaseBuildingTile(building);
                navigationChanged = true;
            }
        }
        if (navigationChanged) {
            resetLogisticsIndicators();
            markEnemyPathsDirty();
        }
    }

    private void releaseBuildingTile(Building building) {
        Tile tile = building.getPosition();
        if (tile == null) return;
        for (Tile occupied : getFootprintTiles(tile, building.getFootprintWidth(), building.getFootprintHeight())) {
            if (occupied != null && occupied.getBuilding() == building) {
                occupied.setBuilding(null);
                occupied.setPassable(occupied.getType() != TileType.WATER);
            }
        }
    }

    private List<Tile> getFootprintTiles(Tile anchor, int width, int height) {
        List<Tile> result = new ArrayList<>();
        if (anchor == null) return result;
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                result.add(map.getTile(anchor.getX() + dx, anchor.getY() + dy));
            }
        }
        return result;
    }

    private List<Tile> getAdjacentTilesAroundTarget(Tile target) {
        List<Tile> result = new ArrayList<>();
        if (target == null) return result;
        Building building = target.getBuilding();
        int minX = target.getX();
        int minY = target.getY();
        int maxX = target.getX();
        int maxY = target.getY();
        if (building != null && building.getPosition() != null) {
            minX = building.getPosition().getX();
            minY = building.getPosition().getY();
            maxX = minX + building.getFootprintWidth() - 1;
            maxY = minY + building.getFootprintHeight() - 1;
        }
        for (int x = minX; x <= maxX; x++) {
            addUniqueTile(result, map.getTile(x, minY - 1));
            addUniqueTile(result, map.getTile(x, maxY + 1));
        }
        for (int y = minY; y <= maxY; y++) {
            addUniqueTile(result, map.getTile(minX - 1, y));
            addUniqueTile(result, map.getTile(maxX + 1, y));
        }
        return result;
    }

    private void addUniqueTile(List<Tile> tiles, Tile tile) {
        if (tile != null && !tiles.contains(tile)) tiles.add(tile);
    }

    private Workshop findAdjacentWorkshop(Tile tile) {
        if (tile == null) return null;
        for (Tile neighbor : getOrthogonalNeighbors(tile)) {
            if (neighbor != null && neighbor.getBuilding() instanceof Workshop) {
                return (Workshop) neighbor.getBuilding();
            }
        }
        return null;
    }

    private void attachFactoryPort(FactoryPort port) {
        if (port == null || port.getPosition() == null) return;
        Tile tile = port.getPosition();
        Workshop workshop = findAdjacentWorkshop(tile);
        if (workshop == null) return;
        Direction side = null;
        Tile anchor = workshop.getPosition();
        if (anchor != null) {
            if (tile.getY() == anchor.getY() - 1 && tile.getX() >= anchor.getX() && tile.getX() < anchor.getX() + workshop.getFootprintWidth()) side = Direction.UP;
            else if (tile.getY() == anchor.getY() + workshop.getFootprintHeight() && tile.getX() >= anchor.getX() && tile.getX() < anchor.getX() + workshop.getFootprintWidth()) side = Direction.DOWN;
            else if (tile.getX() == anchor.getX() - 1 && tile.getY() >= anchor.getY() && tile.getY() < anchor.getY() + workshop.getFootprintHeight()) side = Direction.LEFT;
            else if (tile.getX() == anchor.getX() + workshop.getFootprintWidth() && tile.getY() >= anchor.getY() && tile.getY() < anchor.getY() + workshop.getFootprintHeight()) side = Direction.RIGHT;
        }
        port.attachTo(workshop, side);
    }

    private void markEnemyPathsDirty() { for (Enemy enemy : allEnemies) enemy.requestRepath(); }

    private void resetLogisticsIndicators() {
        for (Building building : allBuildings) {
            if (building instanceof Conveyor) ((Conveyor) building).setActive(false);
            else if (building instanceof CombatTower) ((CombatTower) building).setSupplied(false);
            else if (building instanceof Drill) ((Drill) building).setOutputConnected(false);
        }
    }

    private void updateLogistics() {
        resetLogisticsIndicators();
        if (mainBuilding == null || !mainBuilding.isAlive()) return;
        updateDrillLogistics();
        updateAmmoLogistics();
    }

    private void updateDrillLogistics() {
        for (Building building : new ArrayList<>(allBuildings)) {
            if (!(building instanceof Drill) || !building.isAlive()) continue;
            Drill drill = (Drill) building;
            for (Tile neighbor : getOrthogonalNeighbors(drill.getPosition())) {
                if (neighbor == null || !(neighbor.getBuilding() instanceof Conveyor)) continue;
                Conveyor first = (Conveyor) neighbor.getBuilding();
                if (first.getOutputTile(map) == drill.getPosition()) continue;
                if (traceResourcePathToBase(drill, first)) break;
            }
        }
    }

    private boolean traceResourcePathToBase(Drill drill, Conveyor first) {
        List<Conveyor> path = new ArrayList<>();
        Set<Conveyor> visited = new HashSet<>();
        Conveyor current = first;
        while (current != null && current.isAlive() && visited.add(current)) {
            path.add(current);
            Tile output = current.getOutputTile(map);
            if (output == null || !output.hasBuilding()) return false;
            Building outputBuilding = output.getBuilding();
            if (!outputBuilding.isAlive()) return false;
            if (outputBuilding == mainBuilding) {
                drill.setOutputConnected(true);
                for (Conveyor conveyor : path) conveyor.setActive(true);
                int item = drill.takeFromBuffer(1);
                if (item > 0) {
                    int accepted = mainBuilding.getInventory().addUpToCapacity(drill.getResourceType(), item);
                    if (accepted < item) drill.returnToBuffer(item - accepted);
                }
                return true;
            }
            if (!(outputBuilding instanceof Conveyor)) return false;
            current = (Conveyor) outputBuilding;
        }
        return false;
    }

    private void updateAmmoLogistics() {
        if (mainBuilding.getAmmoStock() <= 0) return;
        Set<CombatTower> suppliedTowers = new HashSet<>();
        for (Tile neighbor : getOrthogonalNeighbors(mainBuilding.getPosition())) {
            if (neighbor == null || !(neighbor.getBuilding() instanceof Conveyor)) continue;
            Conveyor first = (Conveyor) neighbor.getBuilding();
            if (first.getOutputTile(map) == mainBuilding.getPosition()) continue;
            traceAmmoPath(first, suppliedTowers);
        }
    }

    private void traceAmmoPath(Conveyor first, Set<CombatTower> suppliedTowers) {
        List<Conveyor> path = new ArrayList<>();
        Set<Conveyor> localVisited = new HashSet<>();
        Conveyor current = first;
        while (current != null && current.isAlive() && localVisited.add(current)) {
            path.add(current);
            Tile output = current.getOutputTile(map);
            if (output == null || !output.hasBuilding()) return;
            Building outputBuilding = output.getBuilding();
            if (!outputBuilding.isAlive()) return;
            if (outputBuilding instanceof CombatTower) {
                CombatTower tower = (CombatTower) outputBuilding;
                tower.setSupplied(true);
                for (Conveyor conveyor : path) conveyor.setActive(true);
                if (!suppliedTowers.contains(tower) && tower.getAmmo() < tower.getMaxAmmo()
                        && mainBuilding.takeAmmo(1)) {
                    tower.addAmmo(1);
                    suppliedTowers.add(tower);
                }
                return;
            }
            if (!(outputBuilding instanceof Conveyor)) return;
            current = (Conveyor) outputBuilding;
        }
    }

    private List<Tile> getOrthogonalNeighbors(Tile tile) {
        return Arrays.asList(
                map.getTile(tile.getX() + 1, tile.getY()),
                map.getTile(tile.getX() - 1, tile.getY()),
                map.getTile(tile.getX(), tile.getY() + 1),
                map.getTile(tile.getX(), tile.getY() - 1));
    }

    public void finishWithVictory() { if (status == GameStatus.RUNNING) status = GameStatus.VICTORY; }

    public void restart() {
        int configuredWaveDelay = waveManager == null ? -1 : waveManager.getWaveDelaySeconds();
        allUnits.clear();
        allBuildings.clear();
        allEnemies.clear();
        constructionQueue.clear();
        reservationsBySite.clear();
        map.clearDynamicOccupants();
        mainBuilding = null;
        enemySpawnPoint = null;
        waveManager = null;
        logisticsTick = 0;
        destroyedEnemies = 0;
        totalScrapCollected = 0;
        status = GameStatus.RUNNING;
        spawnInitialEntities();
        if (configuredWaveDelay > 0) waveManager.setWaveDelaySeconds(configuredWaveDelay);
    }

    public List<Building> getAllBuildings() { return Collections.unmodifiableList(allBuildings); }
    public List<Unit> getAllUnits() { return Collections.unmodifiableList(allUnits); }
    public List<Enemy> getAllEnemies() { return Collections.unmodifiableList(allEnemies); }
    public List<ConstructionSite> getConstructionQueue() { return Collections.unmodifiableList(constructionQueue); }
    public House getMainBuilding() { return mainBuilding; }
    public EnemySpawnPoint getEnemySpawnPoint() { return enemySpawnPoint; }
    public WaveManager getWaveManager() { return waveManager; }
    public GameStatus getStatus() { return status; }
    public int getDestroyedEnemies() { return destroyedEnemies; }
    public int getEscapedEnemies() { return 0; }
    public int getTotalScrapCollected() { return totalScrapCollected; }
    public Inventory getInventory() { return mainBuilding == null ? null : mainBuilding.getInventory(); }
}
