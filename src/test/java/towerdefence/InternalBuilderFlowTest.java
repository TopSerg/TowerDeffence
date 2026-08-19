package towerdefence;

import towerdefence.building.Workshop;
import towerdefence.game.RoadmapGameState;
import towerdefence.roadmap.RoadmapRuntime;
import towerdefence.world.Direction;
import towerdefence.world.GameMap;
import towerdefence.world.Tile;

import java.awt.Point;

/** Регрессия физической внутренней стройки: Вася размечает, бот приезжает и только затем строит. */
public class InternalBuilderFlowTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        GameMap map = new GameMap(40, 30);
        RoadmapGameState state = new RoadmapGameState(map);
        RoadmapRuntime runtime = state.getRoadmap();

        for (int y = 15; y <= 17; y++) {
            for (int x = 20; x <= 22; x++) map.getTile(x, y).setResource(null);
        }

        Tile workshopOrigin = map.getTile(20, 15);
        Workshop workshop = new Workshop(workshopOrigin);
        check(state.addBuilding(workshop, workshopOrigin), "Workshop for internal builder regression was not added");
        runtime.update(); // discover Workshop and create its FactoryState

        Tile rightSide = map.getTile(23, 16);
        rightSide.setResource(null);
        runtime.getVasya().move(rightSide);
        runtime.requestEnterWorkshop(workshop);
        state.update();

        check(runtime.getVasyaInsideWorkshop() == workshop,
                "Vasya must be inside Workshop before interior marking starts");
        check(state.getInternalBuilderPositions(workshop).size() == 1,
                "Workshop must start with one physical internal builder");
        Point botStart = state.getInternalBuilderPositions(workshop).get(0);

        check(runtime.queueInteriorConveyor(workshop, 8, 8, Direction.LEFT),
                "Interior conveyor blueprint was not queued");
        RoadmapRuntime.InteriorBuildTask task = runtime.getFactoryState(workshop).getTasks().get(0);

        for (int i = 0; i < 30; i++) state.update();

        Point botAfterMarking = state.getInternalBuilderPositions(workshop).get(0);
        check(!botAfterMarking.equals(botStart),
                "Internal builder must physically move toward the marked blueprint");
        check(task.getPhase() == RoadmapRuntime.BlueprintPhase.WAITING_FOR_RESOURCES,
                "Build timer must remain blocked while internal builder is travelling");
        check(workshop.getInteriorConveyor(8, 8) == null,
                "Conveyor must not appear before internal builder reaches the job");

        int safetyTicks = 220;
        while (task.getPhase() != RoadmapRuntime.BlueprintPhase.DONE && safetyTicks-- > 0) {
            state.update();
        }

        check(task.getPhase() == RoadmapRuntime.BlueprintPhase.DONE,
                "Internal builder did not finish the construction task");
        check(workshop.getInteriorConveyor(8, 8) != null,
                "Finished internal builder task must place the conveyor");

        System.out.println("InternalBuilderFlowTest: OK");
    }
}
