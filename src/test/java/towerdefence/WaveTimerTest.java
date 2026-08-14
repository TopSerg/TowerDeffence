package towerdefence;

import towerdefence.combat.WaveManager;
import towerdefence.game.GameState;
import towerdefence.world.GameMap;

public class WaveTimerTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        WaveManager standalone = new WaveManager();
        check(standalone.isCountingDownToWave(), "Начальный обратный отсчёт не запущен");
        check(standalone.getSecondsUntilNextWave() == 45, "Неверное начальное время");

        standalone.setWaveDelaySeconds(12);
        check(standalone.getWaveDelaySeconds() == 12, "Интервал между волнами не сохранился");
        check(standalone.getSecondsUntilNextWave() == 12, "Текущий таймер не обновился");

        GameState state = new GameState(new GameMap(20, 20));
        WaveManager manager = state.getWaveManager();
        manager.setWaveDelaySeconds(1);
        for (int tick = 0; tick < 59; tick++) manager.update(state);
        check(manager.getCurrentWave() == 0, "Волна стартовала раньше окончания таймера");
        check(manager.getSecondsUntilNextWave() == 1, "Таймер неверно округляет секунды");
        manager.update(state);
        check(manager.getCurrentWave() == 1, "Волна не стартовала после окончания таймера");
        check(!manager.isCountingDownToWave(), "Таймер остался активным во время волны");

        state.restart();
        check(state.getWaveManager().getWaveDelaySeconds() == 1,
                "Настройка таймера потерялась после перезапуска");

        boolean rejected = false;
        try {
            standalone.setWaveDelaySeconds(0);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "Нулевая задержка должна быть запрещена");

        System.out.println("WaveTimerTest: OK");
    }
}
