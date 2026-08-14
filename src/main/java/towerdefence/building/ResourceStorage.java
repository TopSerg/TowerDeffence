package towerdefence.building;

import towerdefence.resource.Inventory;
import towerdefence.world.Tile;

/** Расширяемый интерфейс склада. Сейчас его реализует база, позже — отдельные схроны. */
public interface ResourceStorage {
    Inventory getInventory();
    Tile getPosition();
    boolean isAlive();
}
