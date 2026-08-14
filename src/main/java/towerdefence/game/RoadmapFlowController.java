package towerdefence.game;

import towerdefence.building.ConstructionSite;
import towerdefence.building.Workshop;
import towerdefence.roadmap.RoadmapRuntime;
import towerdefence.unit.Worker;
import towerdefence.world.Tile;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Небольшой UX-слой поверх mega-runtime.
 * Внешние чертежи появляются сразу, а Вася нужен только для ощущения присутствия внутри Workshop.
 */
final class RoadmapFlowController {
    private static final float VASYA_NORMAL_SPEED = 0.085f;
    private static final float VASYA_WORKSHOP_RUSH_SPEED = 0.24f;
    private static final float ROVER_WORKSHOP_RUSH_SPEED = 0.30f;

    private final RoadmapGameState state;
    private final RoadmapRuntime roadmap;
    private final Field constructionPhasesField;
    private final Field facilityPhasesField;
    private final Field targetWorkshopField;

    RoadmapFlowController(RoadmapGameState state, RoadmapRuntime roadmap) {
        this.state = state;
        this.roadmap = roadmap;
        this.constructionPhasesField = field("constructionPhases");
        this.facilityPhasesField = field("facilityPhases");
        this.targetWorkshopField = field("targetWorkshop");
    }

    /** Вызывается до RoadmapRuntime.update(), чтобы Rover мог взять новый чертёж в тот же тик. */
    void beforeRuntimeUpdate() {
        promoteExternalBlueprints();
        applyWorkshopRush();
    }

    /** Runtime может поменять маршрут Васи, поэтому после него ещё раз закрепляем быстрый маршрут к Workshop. */
    void afterRuntimeUpdate() {
        promoteExternalBlueprints();
        applyWorkshopRush();
    }

    Workshop getRequestedWorkshop() {
        try {
            return (Workshop) targetWorkshopField.get(roadmap);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Не удалось прочитать целевой Workshop Васи", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private void promoteExternalBlueprints() {
        try {
            Map<ConstructionSite, RoadmapRuntime.BlueprintPhase> construction =
                    (Map<ConstructionSite, RoadmapRuntime.BlueprintPhase>) constructionPhasesField.get(roadmap);
            for (ConstructionSite site : state.getConstructionQueue()) {
                construction.putIfAbsent(site, RoadmapRuntime.BlueprintPhase.WAITING_FOR_BUILDER);
            }
            for (Map.Entry<ConstructionSite, RoadmapRuntime.BlueprintPhase> entry : construction.entrySet()) {
                if (entry.getValue() == RoadmapRuntime.BlueprintPhase.PLANNED
                        || entry.getValue() == RoadmapRuntime.BlueprintPhase.MARKING) {
                    entry.setValue(RoadmapRuntime.BlueprintPhase.WAITING_FOR_BUILDER);
                }
            }

            Map<RoadmapRuntime.FacilityBlueprint, RoadmapRuntime.BlueprintPhase> facilities =
                    (Map<RoadmapRuntime.FacilityBlueprint, RoadmapRuntime.BlueprintPhase>) facilityPhasesField.get(roadmap);
            for (Map.Entry<RoadmapRuntime.FacilityBlueprint, RoadmapRuntime.BlueprintPhase> entry : facilities.entrySet()) {
                if (entry.getValue() == RoadmapRuntime.BlueprintPhase.PLANNED
                        || entry.getValue() == RoadmapRuntime.BlueprintPhase.MARKING) {
                    entry.setValue(RoadmapRuntime.BlueprintPhase.WAITING_FOR_BUILDER);
                }
            }
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Не удалось обновить lifecycle внешних чертежей", exception);
        }
    }

    private void applyWorkshopRush() {
        Workshop target = getRequestedWorkshop();
        boolean rushing = target != null && roadmap.getVasyaInsideWorkshop() == null;
        Worker vasya = roadmap.getVasya();
        RoadmapRuntime.VasyaRover rover = roadmap.getVasyaRover();

        if (!rushing) {
            if (vasya != null && vasya.isAlive()) vasya.setSpeed(VASYA_NORMAL_SPEED);
            if (rover != null && rover.isAlive()) {
                rover.setSpeed(0.14f + Math.max(0, rover.getTier() - 1) * 0.035f);
            }
            return;
        }

        if (rover != null && rover.isAlive()) rover.setSpeed(ROVER_WORKSHOP_RUSH_SPEED);
        if (vasya == null || !vasya.isAlive() || vasya.getPosition() == null) return;

        vasya.setSpeed(VASYA_WORKSHOP_RUSH_SPEED);
        Tile destination = state.findBestAdjacentTile(vasya.getPosition(), target.getPosition(), vasya);
        if (destination != null && (!vasya.isMoving() || vasya.getMovementTarget() != destination)) {
            vasya.setTarget(destination);
        }
    }

    private static Field field(String name) {
        try {
            Field field = RoadmapRuntime.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Mega-runtime изменился: нет поля " + name, exception);
        }
    }
}
