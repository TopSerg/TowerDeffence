import java.util.List;

public class MainGameLoop {
    private GameMap map;
    private GameState state;
    private List<Unit> units;

    public void run() {
        while (true) {
            update(); // Обновление юнитов, зданий
            //render(); // Отрисовка (если есть GUI)
        }
    }

    private void update() {
        for (Unit unit : units) {
//            if (unit.getTask() != null) {
//                unit.getTask().execute(unit);
//            }
        }
    }
}