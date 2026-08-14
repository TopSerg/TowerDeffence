package towerdefence.game;

import towerdefence.world.GameMap;

/** GameState с изолированным логистическим мостом Workshop. */
public class WorkshopGameState extends GameState {
    private static final int WORKSHOP_LOGISTICS_INTERVAL_TICKS = 15;

    private final GameMap workshopMap;
    private int workshopLogisticsTick;

    public WorkshopGameState(GameMap map) {
        super(map);
        this.workshopMap = map;
    }

    @Override
    public void update() {
        super.update();
        if (getStatus() != GameStatus.RUNNING) return;
        workshopLogisticsTick++;
        if (workshopLogisticsTick >= WORKSHOP_LOGISTICS_INTERVAL_TICKS) {
            workshopLogisticsTick = 0;
            WorkshopLogistics.update(getAllBuildings(), workshopMap, getMainBuilding());
        }
    }

    @Override
    public void restart() {
        super.restart();
        workshopLogisticsTick = 0;
    }
}
