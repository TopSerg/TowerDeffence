package towerdefence.game;

import towerdefence.building.Workshop;
import towerdefence.roadmap.RoadmapRuntime;

import java.awt.Point;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Делает внутренних строительных ботов физическими участниками interior 9x9.
 *
 * Mega-runtime пока хранит только количество ботов и сам увеличивает build-timer.
 * Этот слой удерживает размеченную задачу до тех пор, пока конкретный бот
 * физически не придёт на соседнюю с чертежом клетку. Только после этого
 * FactoryState получает WAITING_FOR_BUILDER и может начать BUILDING.
 */
final class InternalBuilderController {
    private static final int MOVE_COOLDOWN_TICKS = 3;
    private static final int SIZE = Workshop.INTERIOR_SIZE;

    private final RoadmapRuntime roadmap;
    private final Map<Workshop, List<BotState>> botsByWorkshop = new IdentityHashMap<>();
    private final Field taskPhaseField = field(RoadmapRuntime.InteriorBuildTask.class, "phase");
    private final Field taskBuildField = field(RoadmapRuntime.InteriorBuildTask.class, "build");

    InternalBuilderController(RoadmapRuntime roadmap) {
        this.roadmap = roadmap;
    }

    void afterRuntimeUpdate() {
        Set<Workshop> live = new HashSet<>(roadmap.getFactoryStates().keySet());
        botsByWorkshop.keySet().removeIf(workshop -> !live.contains(workshop));

        for (RoadmapRuntime.FactoryState factory : roadmap.getFactoryStates().values()) {
            List<BotState> bots = botsByWorkshop.computeIfAbsent(factory.getWorkshop(), ignored -> new ArrayList<>());
            syncBotCount(factory, bots);
            updateFactory(factory, bots);
        }
    }

    void reset() {
        botsByWorkshop.clear();
    }

    List<Point> getPositions(Workshop workshop) {
        List<BotState> bots = botsByWorkshop.get(workshop);
        if (bots == null || bots.isEmpty()) return Collections.emptyList();
        List<Point> result = new ArrayList<>(bots.size());
        for (BotState bot : bots) result.add(new Point(bot.x, bot.y));
        return Collections.unmodifiableList(result);
    }

    private void syncBotCount(RoadmapRuntime.FactoryState factory, List<BotState> bots) {
        while (bots.size() < factory.getInternalBots()) {
            Point spawn = findSpawn(factory, bots);
            if (spawn == null) return;
            bots.add(new BotState(spawn.x, spawn.y));
        }
    }

    private Point findSpawn(RoadmapRuntime.FactoryState factory, List<BotState> bots) {
        int cx = SIZE / 2;
        int cy = SIZE / 2;
        for (int radius = 0; radius < SIZE * 2; radius++) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (Math.abs(x - cx) + Math.abs(y - cy) != radius) continue;
                    if (isWalkable(factory, bots, null, x, y)) return new Point(x, y);
                }
            }
        }
        return null;
    }

    private void updateFactory(RoadmapRuntime.FactoryState factory, List<BotState> bots) {
        for (BotState bot : bots) {
            if (bot.task != null && isFinished(bot.task)) bot.task = null;
        }

        Set<RoadmapRuntime.InteriorBuildTask> claimed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BotState bot : bots) if (bot.task != null) claimed.add(bot.task);

        for (BotState bot : bots) {
            if (bot.task != null) continue;
            RoadmapRuntime.InteriorBuildTask task = findNextTask(factory, claimed);
            if (task == null) continue;
            bot.task = task;
            claimed.add(task);
            // FactoryState успевает сделать один build-tick в тот же кадр, когда
            // Вася закончил MARKING. Отменяем его: строительство начнётся только после приезда бота.
            setBuild(task, 0);
            setPhase(task, RoadmapRuntime.BlueprintPhase.WAITING_FOR_RESOURCES);
        }

        for (BotState bot : bots) updateBot(factory, bots, bot);
    }

    private RoadmapRuntime.InteriorBuildTask findNextTask(
            RoadmapRuntime.FactoryState factory,
            Set<RoadmapRuntime.InteriorBuildTask> claimed) {
        for (RoadmapRuntime.InteriorBuildTask task : factory.getTasks()) {
            if (claimed.contains(task) || isFinished(task)) continue;
            RoadmapRuntime.BlueprintPhase phase = task.getPhase();
            if (phase == RoadmapRuntime.BlueprintPhase.WAITING_FOR_BUILDER
                    || phase == RoadmapRuntime.BlueprintPhase.BUILDING
                    || phase == RoadmapRuntime.BlueprintPhase.WAITING_FOR_RESOURCES) {
                return task;
            }
        }
        return null;
    }

    private void updateBot(RoadmapRuntime.FactoryState factory, List<BotState> bots, BotState bot) {
        RoadmapRuntime.InteriorBuildTask task = bot.task;
        if (task == null) return;

        if (isAtWorkPosition(bot, task)) {
            if (task.getPhase() == RoadmapRuntime.BlueprintPhase.WAITING_FOR_RESOURCES) {
                setPhase(task, RoadmapRuntime.BlueprintPhase.WAITING_FOR_BUILDER);
            }
            return;
        }

        // Внутренние ресурсы пока не реализованы. На этом этапе WAITING_FOR_RESOURCES
        // используется как нейтральное состояние "бот назначен и едет", которое
        // старый FactoryState не строит автоматически.
        if (task.getPhase() != RoadmapRuntime.BlueprintPhase.WAITING_FOR_RESOURCES) {
            setPhase(task, RoadmapRuntime.BlueprintPhase.WAITING_FOR_RESOURCES);
        }

        if (bot.moveCooldown > 0) {
            bot.moveCooldown--;
            return;
        }

        Point next = findNextStep(factory, bots, bot, task);
        if (next == null) return;
        bot.x = next.x;
        bot.y = next.y;
        bot.moveCooldown = MOVE_COOLDOWN_TICKS;

        if (isAtWorkPosition(bot, task)) {
            setPhase(task, RoadmapRuntime.BlueprintPhase.WAITING_FOR_BUILDER);
        }
    }

    private Point findNextStep(
            RoadmapRuntime.FactoryState factory,
            List<BotState> bots,
            BotState bot,
            RoadmapRuntime.InteriorBuildTask task) {
        boolean[][] visited = new boolean[SIZE][SIZE];
        Point[][] firstStep = new Point[SIZE][SIZE];
        Deque<Point> queue = new ArrayDeque<>();
        Point start = new Point(bot.x, bot.y);
        queue.add(start);
        visited[start.y][start.x] = true;

        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            Point current = queue.removeFirst();
            for (int[] direction : directions) {
                int nx = current.x + direction[0];
                int ny = current.y + direction[1];
                if (nx < 0 || ny < 0 || nx >= SIZE || ny >= SIZE || visited[ny][nx]) continue;
                if (!isWalkable(factory, bots, bot, nx, ny)) continue;

                visited[ny][nx] = true;
                Point next = new Point(nx, ny);
                firstStep[ny][nx] = current.equals(start) ? next : firstStep[current.y][current.x];
                if (isWorkCell(nx, ny, task)) return firstStep[ny][nx];
                queue.addLast(next);
            }
        }
        return null;
    }

    private boolean isWalkable(
            RoadmapRuntime.FactoryState factory,
            List<BotState> bots,
            BotState movingBot,
            int x,
            int y) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return false;
        if (factory.getMachine(x, y) != null) return false;
        Workshop workshop = factory.getWorkshop();
        if (workshop.isGatewayCell(x, y) || workshop.isInteriorReserved(x, y)) return false;
        for (BotState other : bots) {
            if (other != movingBot && other.x == x && other.y == y) return false;
        }
        // Внутренние conveyors намеренно проходимы для строительных ботов.
        return true;
    }

    private boolean isAtWorkPosition(BotState bot, RoadmapRuntime.InteriorBuildTask task) {
        return isWorkCell(bot.x, bot.y, task);
    }

    private boolean isWorkCell(int x, int y, RoadmapRuntime.InteriorBuildTask task) {
        return Math.abs(x - task.getX()) + Math.abs(y - task.getY()) == 1;
    }

    private boolean isFinished(RoadmapRuntime.InteriorBuildTask task) {
        RoadmapRuntime.BlueprintPhase phase = task.getPhase();
        return phase == RoadmapRuntime.BlueprintPhase.DONE || phase == RoadmapRuntime.BlueprintPhase.DAMAGED;
    }

    private void setPhase(RoadmapRuntime.InteriorBuildTask task, RoadmapRuntime.BlueprintPhase phase) {
        try {
            taskPhaseField.set(task, phase);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Не удалось обновить фазу внутренней стройки", exception);
        }
    }

    private void setBuild(RoadmapRuntime.InteriorBuildTask task, int build) {
        try {
            taskBuildField.setInt(task, Math.max(0, build));
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Не удалось обновить прогресс внутренней стройки", exception);
        }
    }

    private static Field field(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Mega-runtime изменился: нет поля " + type.getSimpleName() + "." + name, exception);
        }
    }

    private static final class BotState {
        private int x;
        private int y;
        private int moveCooldown;
        private RoadmapRuntime.InteriorBuildTask task;

        private BotState(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
