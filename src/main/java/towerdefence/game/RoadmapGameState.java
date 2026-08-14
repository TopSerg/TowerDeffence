package towerdefence.game;

import towerdefence.combat.CombatTower;
import towerdefence.combat.TowerModuleType;
import towerdefence.resource.ResourceType;
import towerdefence.roadmap.RoadmapRuntime;
import towerdefence.world.GameMap;
import towerdefence.world.Tile;

/** GameState экспериментальной реализации всего текущего roadmap. */
public class RoadmapGameState extends WorkshopGameState {
    private final RoadmapRuntime roadmap;

    public RoadmapGameState(GameMap map) {
        super(map);
        roadmap = new RoadmapRuntime(this, map);
        roadmap.bootstrap();
    }

    public RoadmapRuntime getRoadmap() { return roadmap; }

    /** Вася больше не забирает строительные задачи у Construction Rover. */
    @Override
    public boolean hasPendingConstruction() {
        return false;
    }

    /** В mega-mode стартовый Worker — Вася, а не автономный шахтёр. */
    @Override
    public Tile findNearestMineableResource(Tile start, Tile preferred) {
        if (roadmap != null && roadmap.isVasyaTile(start)) return null;
        return super.findNearestMineableResource(start, preferred);
    }

    @Override
    public void update() {
        super.update();
        if (getStatus() == GameStatus.RUNNING) roadmap.update();
    }

    @Override
    public void restart() {
        super.restart();
        if (roadmap != null) roadmap.resetAfterStateRestart();
    }

    @Override
    public boolean canAfford(TowerModuleType module) {
        ResourceType product = productFor(module);
        return product != null && getMainBuilding().getInventory().has(product, 1);
    }

    @Override
    public boolean installTowerModule(CombatTower tower, TowerModuleType module) {
        if (getStatus() != GameStatus.RUNNING || tower == null || module == null
                || !tower.isAlive() || !getAllBuildings().contains(tower)
                || !tower.canInstallModule(module)) return false;
        ResourceType product = productFor(module);
        if (product == null || !getMainBuilding().getInventory().remove(product, 1)) return false;
        if (!tower.installModule(module)) {
            getMainBuilding().getInventory().add(product, 1);
            return false;
        }
        return true;
    }

    @Override
    public String getModuleFailureReason(CombatTower tower, TowerModuleType module) {
        if (tower == null || module == null) return "Башня или модуль не выбраны";
        if (!tower.canInstallModule(module)) return tower.getModuleFailureReason(module);
        ResourceType product = productFor(module);
        if (product == null || !getMainBuilding().getInventory().has(product, 1)) {
            return "Сначала изготовьте " + module.getDisplayName() + " в Workshop";
        }
        return "Модуль сейчас установить нельзя";
    }

    private ResourceType productFor(TowerModuleType module) {
        if (module == null) return null;
        switch (module) {
            case STABILIZER_I: return ResourceType.STABILIZER_I;
            case COOLING_I: return ResourceType.COOLING_MODULE;
            case STABILIZER_II: return ResourceType.STABILIZER_II;
            default: return null;
        }
    }
}
