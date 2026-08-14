package towerdefence;

import towerdefence.building.FactoryPort;
import towerdefence.building.Workshop;
import towerdefence.game.RoadmapGameState;
import towerdefence.resource.ResourceType;
import towerdefence.roadmap.RoadmapRuntime;
import towerdefence.world.Direction;
import towerdefence.world.GameMap;
import towerdefence.world.Tile;
import towerdefence.world.TileType;

public class RoadmapMegaFeatureTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Tile tile(int x, int y) {
        return new Tile(x, y, TileType.GRASS, null, null, true);
    }

    public static void main(String[] args) {
        Workshop workshop = new Workshop(tile(5, 5));
        check(workshop.getInteriorWidth() == 9 && workshop.getInteriorHeight() == 9,
                "Workshop scale 3x3 -> 9x9 broken");
        check(workshop.placeInteriorConveyor(4, 4, Direction.RIGHT), "Interior conveyor was not placed");
        workshop.takeDamage(Workshop.MAX_HEALTH + 10);
        check(workshop.isRuined() && workshop.isAlive(), "Ruined factory must keep its interior entity");
        check(workshop.getInteriorConveyor(4, 4).getDamage() == 100,
                "Ruined factory must fully damage interior equipment");
        workshop.repairShell(12);
        check(!workshop.isRuined() && workshop.getHealth() > 0, "Factory shell repair did not revive access");

        FactoryPort input = new FactoryPort(tile(5, 4), true);
        check(input.attachTo(workshop, Direction.UP), "Factory input did not attach");
        check(input.getGatewayCells().size() == 3, "External port must map to three internal lanes");
        check(input.cycleLaneFilter(0) == ResourceType.METAL, "First filter must become METAL");
        check(input.acceptsLane(0, ResourceType.METAL) && !input.acceptsLane(0, ResourceType.COAL),
                "Gateway lane filter does not sort resources");

        check(RoadmapRuntime.MachineType.AMMO_PRESS.getOutput() == ResourceType.AMMO
                        && RoadmapRuntime.MachineType.AMMO_PRESS.getOutputAmount() == 10,
                "Ammo Press recipe must produce 10 ammo");
        check(RoadmapRuntime.MachineType.ROBOT_ASSEMBLER.getOutput() == ResourceType.ROBOT_KIT,
                "Robotics recipe missing");
        check(RoadmapRuntime.MachineType.FUEL_REFINERY.getOutput() == ResourceType.FUEL,
                "Fuel chemistry missing");
        check(RoadmapRuntime.MachineType.LUBRICANT_REACTOR.getOutput() == ResourceType.LUBRICANT,
                "Lubricant chemistry missing");
        check(RoadmapRuntime.MachineType.EXPLOSIVES_REACTOR.getOutput() == ResourceType.EXPLOSIVES,
                "Explosives chemistry missing");
        check(ResourceType.BEAM.isManufactured() && ResourceType.ALLOY.isManufactured(),
                "Foundry intermediate products missing");

        GameMap map = new GameMap(40, 30);
        check(map.getTile(14, 10).hasResource()
                        && map.getTile(14, 10).getResource().getType() == ResourceType.OIL,
                "Mega map must contain deterministic oil for the pipe/chemistry loop");

        RoadmapGameState state = new RoadmapGameState(map);
        RoadmapRuntime runtime = state.getRoadmap();
        check(runtime.getVasya() != null, "Vasya was not bootstrapped");
        check(runtime.getVasyaRover() != null, "Vasya rover was not bootstrapped");
        check(runtime.getConstructionRovers().size() == 1, "Initial Construction Rover missing");
        check(runtime.getCombatRobots().isEmpty(), "Combat robots should be produced/deployed later");
        check(runtime.toggleWire(10, 10) && runtime.hasWire(10, 10), "Power layer is not editable");
        check(runtime.togglePipe(11, 10) && runtime.hasPipe(11, 10), "Fluid layer is not editable");
        check(runtime.getExploredPercent() > 0 && runtime.getExploredPercent() < 100,
                "Exploration fog must start partially revealed");

        System.out.println("RoadmapMegaFeatureTest: OK; machines="
                + RoadmapRuntime.MachineType.values().length
                + ", facilities=" + RoadmapRuntime.FacilityKind.values().length);
    }
}
