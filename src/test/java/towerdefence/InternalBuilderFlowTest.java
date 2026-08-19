package towerdefence;

import towerdefence.building.Workshop;
import towerdefence.game.GameStatus;
import towerdefence.game.RoadmapGameState;
import towerdefence.roadmap.RoadmapRuntime;
import towerdefence.world.Direction;
import towerdefence.world.GameMap;
import towerdefence.world.Tile;

import java.awt.Point;
import java.util.function.BooleanSupplier;

/** Регрессия физической внутренней стройки: Вася размечает, бот приезжает и только затем строит. */
public class InternalBuilderFlowTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void runUntil(RoadmapGameState state, int maxTicks,
                                 BooleanSupplier condition, String failureMessage) {
        for (int i = 0; i < maxTicks; i++) {
            if (condition.getAsBoolean()) return;
            state.update();
            check(state.getStatus() == GameStatus.RUNNING,
                    "Game stopped while running internal builder regression");
        }
        if (!condition.getAsBoolean()) throw new AssertionError(failureMessage + " after " + maxTicks + " ticks");
    }

    public static void main(String[] args) {
        GameMap map = new GameMap(40, 30);
        RoadmapGameState state = new RoadmapGameState(map);
        state.getWaveManager().setWaveDelaySeconds(3600);
        RoadmapRuntime runtime = state.getRoadmap();

        // Deterministic empty 3x3 Workshop footprint and one-cell perimeter.
        for (int y = 14; y <= 18; y++) {
            for (int x = 19; x <= 23; x++) {
                Tile tile = map.getTile(x, y);
                tile.setResource(null);
                tile.setPassable(true);
            }
        }

        Tile workshopOrigin = map.getTile(20, 15);
        Workshop workshop = new Workshop(workshopOrigin);
        check(state.addBuilding(workshop, workshopOrigin),
                "Workshop for internal builder regression was not added");

        // Use the real game loop. Do not teleport Vasya: Unit.move() does not clear an old path,
        // so teleporting immediately before state.update() makes the test depend on stale movement state.
        runtime.requestEnterWorkshop(workshop);
        runUntil(state, 3_000,
                () -> runtime.getVasyaInsideWorkshop() == workshop,
                "Vasya did not physically enter Workshop");

        check(runtime.getFactoryState(workshop) != null,
                "Workshop was not registered in RoadmapRuntime");
        check(state.getInternalBuilderPositions(workshop).size() == 1,
                "Workshop must start with one physical internal builder");
        Point botStart = state.getInternalBuilderPositions(workshop).get(0);

        check(runtime.queueInteriorConveyor(workshop, 8, 8, Direction.LEFT),
                "Interior conveyor blueprint was not queued");
        RoadmapRuntime.InteriorBuildTask task = runtime.getFactoryState(workshop).getTasks().get(0);

        // Vasya marks the plan. The bot should get the job and start travelling,
        // but construction must still be blocked until the bot reaches a work cell.
        for (int i = 0; i < 30; i++) state.update();

        Point botAfterMarking = state.getInternalBuilderPositions(workshop).get(0);
        check(!botAfterMarking.equals(botStart),
                "Internal builder must physically move toward the marked blueprint");
        check(task.getPhase() == RoadmapRuntime.BlueprintPhase.WAITING_FOR_RESOURCES,
                "Build timer must remain blocked while internal builder is travelling; actual=" + task.getPhase());
        check(workshop.getInteriorConveyor(8, 8) == null,
                "Conveyor must not appear before internal builder reaches the job");

        runUntil(state, 300,
                () -> task.getPhase() == RoadmapRuntime.BlueprintPhase.DONE,
                "Internal builder did not finish the construction task; phase=" + task.getPhase());

        check(workshop.getInteriorConveyor(8, 8) != null,
                "Finished internal builder task must place the conveyor");

        System.out.println("InternalBuilderFlowTest: OK");
    }
}
