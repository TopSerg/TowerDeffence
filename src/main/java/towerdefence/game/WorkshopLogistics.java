package towerdefence.game;

import towerdefence.building.Building;
import towerdefence.building.Conveyor;
import towerdefence.building.Drill;
import towerdefence.building.FactoryPort;
import towerdefence.building.House;
import towerdefence.building.Workshop;
import towerdefence.resource.ResourceType;
import towerdefence.world.GameMap;
import towerdefence.world.Tile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Мост внешней и внутренней логистики Workshop.
 * Один внешний порт пропускает максимум один физический предмет за логистический такт.
 */
public final class WorkshopLogistics {
    private WorkshopLogistics() { }

    public static void update(List<Building> buildings, GameMap map, House mainBuilding) {
        if (buildings == null || map == null || mainBuilding == null || !mainBuilding.isAlive()) return;

        for (Building building : buildings) {
            if (building instanceof FactoryPort && building.isAlive()) ((FactoryPort) building).beginLogisticsTick();
        }

        for (Building building : new ArrayList<>(buildings)) {
            if (building instanceof Workshop && ((Workshop) building).isOperational()) {
                ((Workshop) building).advanceInteriorTransport();
            }
        }

        for (Building building : new ArrayList<>(buildings)) {
            if (building instanceof FactoryPort && building.isAlive()) {
                FactoryPort port = (FactoryPort) building;
                if (!port.isInput()) drainOutputPort(port, map, mainBuilding);
            }
        }

        for (Building building : new ArrayList<>(buildings)) {
            if (building instanceof Drill && building.isAlive()) feedWorkshopFromDrill((Drill) building, map);
        }
    }

    private static void feedWorkshopFromDrill(Drill drill, GameMap map) {
        if (drill == null || drill.getPosition() == null || drill.isOutputConnected()) return;
        for (Tile neighbor : orthogonalNeighbors(map, drill.getPosition())) {
            if (neighbor == null || !(neighbor.getBuilding() instanceof Conveyor)) continue;
            Conveyor first = (Conveyor) neighbor.getBuilding();
            if (first.getOutputTile(map) == drill.getPosition()) continue;
            if (traceDrillToInputPort(drill, first, map)) return;
        }
    }

    private static boolean traceDrillToInputPort(Drill drill, Conveyor first, GameMap map) {
        List<Conveyor> path = new ArrayList<>();
        Set<Conveyor> visited = new HashSet<>();
        Conveyor current = first;
        while (current != null && current.isAlive() && visited.add(current)) {
            path.add(current);
            Tile output = current.getOutputTile(map);
            if (output == null || !output.hasBuilding()) return false;
            Building endpoint = output.getBuilding();
            if (!endpoint.isAlive()) return false;
            if (endpoint instanceof FactoryPort) {
                FactoryPort port = (FactoryPort) endpoint;
                if (!port.isInput() || port.getWorkshop() == null || !port.getWorkshop().isOperational()) return false;
                drill.setOutputConnected(true);
                port.setExternalConnected(true);
                for (Conveyor conveyor : path) conveyor.setActive(true);
                if (drill.getBuffer() > 0 && port.acceptExternalResource(drill.getResourceType())) drill.takeFromBuffer(1);
                return true;
            }
            if (!(endpoint instanceof Conveyor)) return false;
            current = (Conveyor) endpoint;
        }
        return false;
    }

    private static void drainOutputPort(FactoryPort port, GameMap map, House mainBuilding) {
        if (port.getWorkshop() == null || !port.getWorkshop().isOperational() || port.getPosition() == null) return;
        for (Tile neighbor : orthogonalNeighbors(map, port.getPosition())) {
            if (neighbor == null || !(neighbor.getBuilding() instanceof Conveyor)) continue;
            Conveyor first = (Conveyor) neighbor.getBuilding();
            if (first.getOutputTile(map) == port.getPosition()) continue;
            if (traceOutputPortToStorage(port, first, map, mainBuilding)) return;
        }
    }

    private static boolean traceOutputPortToStorage(FactoryPort port, Conveyor first,
                                                     GameMap map, House mainBuilding) {
        List<Conveyor> path = new ArrayList<>();
        Set<Conveyor> visited = new HashSet<>();
        Conveyor current = first;
        while (current != null && current.isAlive() && visited.add(current)) {
            path.add(current);
            Tile output = current.getOutputTile(map);
            if (output == null || !output.hasBuilding()) return false;
            Building endpoint = output.getBuilding();
            if (!endpoint.isAlive()) return false;
            if (endpoint == mainBuilding) {
                port.setExternalConnected(true);
                for (Conveyor conveyor : path) conveyor.setActive(true);
                ResourceType type = port.peekOutputResource();
                if (type == null) return true;

                boolean accepted;
                if (type == ResourceType.AMMO) {
                    mainBuilding.addAmmo(1);
                    accepted = true;
                } else if (type.isLiquid()) {
                    // Жидкости должны идти по pipe-layer, а не попадать в обычный склад.
                    accepted = false;
                } else {
                    accepted = mainBuilding.getInventory().addUpToCapacity(type, 1) == 1;
                }
                if (accepted) port.commitOutputToExternal();
                return true;
            }
            if (!(endpoint instanceof Conveyor)) return false;
            current = (Conveyor) endpoint;
        }
        return false;
    }

    private static List<Tile> orthogonalNeighbors(GameMap map, Tile tile) {
        if (map == null || tile == null) return java.util.Collections.emptyList();
        return Arrays.asList(
                map.getTile(tile.getX() + 1, tile.getY()),
                map.getTile(tile.getX() - 1, tile.getY()),
                map.getTile(tile.getX(), tile.getY() + 1),
                map.getTile(tile.getX(), tile.getY() - 1));
    }
}
