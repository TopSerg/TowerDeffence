package towerdefence.unit;

import towerdefence.building.ConstructionSite;
import towerdefence.game.GameState;
import towerdefence.resource.Resource;
import towerdefence.resource.ResourceType;
import towerdefence.world.GameMap;
import towerdefence.world.Tile;

import java.awt.*;
import java.util.Map;

/** Автономный рабочий: строит по очереди, а в свободное время добывает ближайший ресурс. */
public class Worker extends Unit {
    public static final int INVENTORY_CAPACITY = 24;
    private static final int MINING_INTERVAL_TICKS = 36;
    private static final int MINING_AMOUNT = 2;

    private final GameState state;
    private WorkerState workerState = WorkerState.IDLE;
    private ConstructionSite activeConstruction;
    private Tile preferredDeposit;
    private Tile activeDeposit;
    private int miningCooldown;
    private boolean constructionAfterUnload;

    public Worker(Tile position, GameMap map, GameState state) {
        super(position, UnitType.WORKER, new Color(50, 125, 235), map, INVENTORY_CAPACITY);
        if (state == null) throw new IllegalArgumentException("Рабочему требуется состояние игры");
        this.state = state;
        this.health = 40;
        this.speed = 0.085f;
    }

    @Override
    public void update() {
        super.update();
        if (!isAlive || isMoving()) return;

        if (activeConstruction != null
                && (!activeConstruction.isAlive() || !state.isConstructionPending(activeConstruction))) {
            activeConstruction = null;
            constructionAfterUnload = false;
            workerState = WorkerState.IDLE;
        }

        if (activeConstruction == null && state.hasPendingConstruction()) {
            ConstructionSite claimed = state.claimNextConstruction(this);
            if (claimed != null) {
                activeConstruction = claimed;
                if (!inventory.isEmpty()) {
                    constructionAfterUnload = true;
                    beginReturnToStorage();
                } else {
                    beginConstructionSupplyRun();
                }
                return;
            }
        }

        if (activeConstruction != null) {
            updateConstructionWork();
        } else {
            updateMiningWork();
        }
    }

    private void updateConstructionWork() {
        if (workerState == WorkerState.RETURNING || workerState == WorkerState.UNLOADING) {
            finishReturnAndUnload();
            return;
        }
        if (activeConstruction.hasAllMaterials()) {
            if (!isAdjacentTo(activeConstruction.getPosition())) {
                workerState = WorkerState.MOVING_TO_BUILD_SITE;
                if (!moveToAdjacent(activeConstruction.getPosition())) workerState = WorkerState.IDLE;
                return;
            }
            workerState = WorkerState.BUILDING;
            activeConstruction.work(1);
            if (activeConstruction.isComplete()) {
                ConstructionSite completed = activeConstruction;
                activeConstruction = null;
                workerState = WorkerState.IDLE;
                state.completeConstruction(completed, this);
            }
            return;
        }

        if (containsNeededMaterials(activeConstruction)) {
            if (!isAdjacentTo(activeConstruction.getPosition())) {
                workerState = WorkerState.MOVING_TO_BUILD_SITE;
                if (!moveToAdjacent(activeConstruction.getPosition())) workerState = WorkerState.IDLE;
                return;
            }
            workerState = WorkerState.DELIVERING;
            activeConstruction.deliverFrom(inventory);
            if (activeConstruction.hasAllMaterials()) return;
            beginConstructionSupplyRun();
            return;
        }

        if (!inventory.isEmpty()) {
            constructionAfterUnload = true;
            beginReturnToStorage();
            return;
        }

        if (!isAdjacentTo(state.getMainBuilding().getPosition())) {
            workerState = WorkerState.MOVING_TO_STORAGE;
            if (!moveToAdjacent(state.getMainBuilding().getPosition())) workerState = WorkerState.IDLE;
            return;
        }

        workerState = WorkerState.LOADING_CONSTRUCTION;
        int loaded = state.loadReservedMaterials(activeConstruction, inventory);
        if (loaded <= 0) {
            // Ресурсы могли временно оказаться в инвентаре другого рабочего.
            workerState = WorkerState.IDLE;
            return;
        }
        workerState = WorkerState.MOVING_TO_BUILD_SITE;
        if (!moveToAdjacent(activeConstruction.getPosition())) workerState = WorkerState.IDLE;
    }

    private void beginConstructionSupplyRun() {
        if (activeConstruction == null) return;
        if (!inventory.isEmpty()) {
            constructionAfterUnload = true;
            beginReturnToStorage();
            return;
        }
        if (isAdjacentTo(state.getMainBuilding().getPosition())) {
            workerState = WorkerState.LOADING_CONSTRUCTION;
        } else {
            workerState = WorkerState.MOVING_TO_STORAGE;
            if (!moveToAdjacent(state.getMainBuilding().getPosition())) workerState = WorkerState.IDLE;
        }
    }

    private boolean containsNeededMaterials(ConstructionSite site) {
        for (Map.Entry<ResourceType, Integer> entry : site.getRequired().entrySet()) {
            if (site.getRemainingNeeded(entry.getKey()) > 0 && inventory.getAmount(entry.getKey()) > 0) return true;
        }
        return false;
    }

    private void updateMiningWork() {
        if (workerState == WorkerState.RETURNING || workerState == WorkerState.UNLOADING) {
            finishReturnAndUnload();
            return;
        }

        if (inventory.isFull()) {
            beginReturnToStorage();
            return;
        }

        if (!isValidDeposit(activeDeposit)) {
            activeDeposit = state.findNearestMineableResource(position, preferredDeposit);
            if (activeDeposit == null) {
                workerState = WorkerState.IDLE;
                return;
            }
        }

        if (position != activeDeposit) {
            workerState = WorkerState.MOVING_TO_RESOURCE;
            if (!setTarget(activeDeposit)) {
                activeDeposit = null;
                workerState = WorkerState.IDLE;
            }
            return;
        }

        workerState = WorkerState.MINING;
        if (state.hasPendingConstruction()) {
            constructionAfterUnload = true;
            beginReturnToStorage();
            return;
        }
        if (miningCooldown > 0) {
            miningCooldown--;
            return;
        }

        Resource resource = activeDeposit.getResource();
        if (resource == null || resource.isDepleted()) {
            activeDeposit.setResource(null);
            activeDeposit = null;
            if (!inventory.isEmpty()) beginReturnToStorage();
            else workerState = WorkerState.IDLE;
            return;
        }

        int room = inventory.getFreeSpace();
        int extracted = resource.extract(Math.min(MINING_AMOUNT, room));
        if (extracted > 0) inventory.add(resource.getType(), extracted);
        miningCooldown = MINING_INTERVAL_TICKS;
        if (resource.isDepleted()) {
            activeDeposit.setResource(null);
            activeDeposit = null;
        }
        if (inventory.isFull() || activeDeposit == null) beginReturnToStorage();
    }

    private void beginReturnToStorage() {
        if (inventory.isEmpty()) {
            workerState = WorkerState.IDLE;
            return;
        }
        if (isAdjacentTo(state.getMainBuilding().getPosition())) {
            workerState = WorkerState.UNLOADING;
        } else {
            workerState = WorkerState.RETURNING;
            if (!moveToAdjacent(state.getMainBuilding().getPosition())) workerState = WorkerState.IDLE;
        }
    }

    private void finishReturnAndUnload() {
        if (!isAdjacentTo(state.getMainBuilding().getPosition())) {
            workerState = WorkerState.RETURNING;
            if (!moveToAdjacent(state.getMainBuilding().getPosition())) workerState = WorkerState.IDLE;
            return;
        }
        workerState = WorkerState.UNLOADING;
        inventory.transferAllTo(state.getMainBuilding().getInventory());
        if (!inventory.isEmpty()) return; // склад заполнен

        if (constructionAfterUnload) {
            constructionAfterUnload = false;
            if (activeConstruction == null) activeConstruction = state.claimNextConstruction(this);
            if (activeConstruction != null) beginConstructionSupplyRun();
            else workerState = WorkerState.IDLE;
        } else {
            workerState = WorkerState.IDLE;
        }
    }

    private boolean moveToAdjacent(Tile target) {
        Tile adjacent = state.findBestAdjacentTile(position, target, this);
        return adjacent != null && setTarget(adjacent);
    }

    private boolean isAdjacentTo(Tile tile) {
        if (tile == null || position == null) return false;
        if (tile.getBuilding() != null && tile.getBuilding().getPosition() != null) {
            towerdefence.building.Building building = tile.getBuilding();
            int minX = building.getPosition().getX();
            int minY = building.getPosition().getY();
            int maxX = minX + building.getFootprintWidth() - 1;
            int maxY = minY + building.getFootprintHeight() - 1;
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    if (Math.abs(position.getX() - x) + Math.abs(position.getY() - y) == 1) return true;
                }
            }
            return false;
        }
        return Math.abs(position.getX() - tile.getX()) + Math.abs(position.getY() - tile.getY()) == 1;
    }

    private boolean isValidDeposit(Tile tile) {
        if (tile == null || !tile.hasResource() || tile.hasBuilding()) return false;
        ResourceType type = tile.getResource().getType();
        return type == ResourceType.METAL || type == ResourceType.COAL;
    }

    public boolean assignPreferredDeposit(Tile tile) {
        if (!isValidDeposit(tile)) return false;
        preferredDeposit = tile;
        activeDeposit = tile;
        if (activeConstruction == null) {
            workerState = WorkerState.MOVING_TO_RESOURCE;
            setTarget(tile);
        }
        return true;
    }

    public void clearPreferredDeposit() { preferredDeposit = null; }
    public WorkerState getWorkerState() { return workerState; }
    public ConstructionSite getActiveConstruction() { return activeConstruction; }
    public Tile getPreferredDeposit() { return preferredDeposit; }
    public Tile getActiveDeposit() { return activeDeposit; }

    public void onConstructionCancelled(ConstructionSite site) {
        if (activeConstruction == site) {
            activeConstruction = null;
            constructionAfterUnload = !inventory.isEmpty();
            if (constructionAfterUnload) beginReturnToStorage();
            else workerState = WorkerState.IDLE;
        }
    }

    @Override
    public void render(Graphics g, int tileSize) {
        int x = Math.round(realX * tileSize);
        int y = Math.round(realY * tileSize);
        g.setColor(new Color(28, 65, 125));
        g.fillOval(x + 2, y + 2, tileSize - 4, tileSize - 4);
        g.setColor(color);
        g.fillOval(x + 6, y + 5, tileSize - 12, tileSize - 12);
        g.setColor(new Color(245, 205, 80));
        g.fillRect(x + tileSize / 2 - 2, y + 3, 4, 8);
        g.drawLine(x + tileSize / 2, y + 4, x + tileSize - 4, y + tileSize / 2);

        if (!inventory.isEmpty()) {
            g.setColor(new Color(25, 28, 32, 220));
            g.fillRoundRect(x + 2, y + tileSize - 11, tileSize - 4, 9, 5, 5);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
            String text = inventory.getStoredAmount() + "/" + inventory.getSize();
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, x + (tileSize - fm.stringWidth(text)) / 2, y + tileSize - 4);
        }
        renderPath(g, tileSize);
    }
}
