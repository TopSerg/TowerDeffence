package towerdefence.combat;

import towerdefence.game.GameState;
import towerdefence.game.GameStatus;

public class WaveManager {
    public static final int MIN_WAVE_DELAY_SECONDS = 1;
    public static final int MAX_WAVE_DELAY_SECONDS = 3600;

    private static final int TICKS_PER_SECOND = 60;
    private static final int TOTAL_WAVES = 5;
    private static final int INITIAL_PREPARATION_SECONDS = 45;
    private static final int DEFAULT_INTERMISSION_SECONDS = 5;
    private static final int SPAWN_INTERVAL_TICKS = 48;

    private enum Phase {
        PREPARATION,
        SPAWNING,
        WAITING_FOR_CLEAR,
        INTERMISSION,
        FINISHED
    }

    private Phase phase = Phase.PREPARATION;
    private int currentWave;
    private int phaseTicks;
    private int enemiesRemainingToSpawn;
    private int enemiesInCurrentWave;
    private int spawnedInCurrentWave;
    private int spawnCooldown;
    private int waveDelaySeconds = DEFAULT_INTERMISSION_SECONDS;

    public WaveManager() {
        phaseTicks = secondsToTicks(INITIAL_PREPARATION_SECONDS);
    }

    public void update(GameState state) {
        if (state == null || state.getStatus() != GameStatus.RUNNING || phase == Phase.FINISHED) return;

        switch (phase) {
            case PREPARATION:
            case INTERMISSION:
                if (phaseTicks > 0) phaseTicks--;
                if (phaseTicks <= 0) startNextWave();
                break;

            case SPAWNING:
                if (spawnCooldown > 0) spawnCooldown--;
                if (enemiesRemainingToSpawn > 0 && spawnCooldown <= 0) {
                    EnemyType type = chooseEnemyType(currentWave, spawnedInCurrentWave);
                    if (state.spawnWaveEnemy(currentWave, type)) {
                        enemiesRemainingToSpawn--;
                        spawnedInCurrentWave++;
                        spawnCooldown = SPAWN_INTERVAL_TICKS;
                    }
                }
                if (enemiesRemainingToSpawn <= 0) phase = Phase.WAITING_FOR_CLEAR;
                break;

            case WAITING_FOR_CLEAR:
                if (state.getAllEnemies().isEmpty()) {
                    if (currentWave >= TOTAL_WAVES) {
                        phase = Phase.FINISHED;
                        state.finishWithVictory();
                    } else {
                        phase = Phase.INTERMISSION;
                        phaseTicks = secondsToTicks(waveDelaySeconds);
                    }
                }
                break;

            default:
                break;
        }
    }

    /** Состав волн постепенно вводит все четыре роли роботов. */
    public EnemyType chooseEnemyType(int wave, int index) {
        switch (wave) {
            case 1:
                return index % 4 == 3 ? EnemyType.FAST : EnemyType.NORMAL;
            case 2:
                return index % 3 == 1 ? EnemyType.FAST : EnemyType.NORMAL;
            case 3:
                if (index % 4 == 3) return EnemyType.ARMORED;
                return index % 3 == 1 ? EnemyType.FAST : EnemyType.NORMAL;
            case 4:
                if (index % 6 == 5) return EnemyType.HEAVY;
                if (index % 3 == 2) return EnemyType.ARMORED;
                return index % 2 == 1 ? EnemyType.FAST : EnemyType.NORMAL;
            case 5:
            default:
                if (index % 5 == 4) return EnemyType.HEAVY;
                if (index % 3 == 2) return EnemyType.ARMORED;
                return index % 2 == 1 ? EnemyType.FAST : EnemyType.NORMAL;
        }
    }

    private void startNextWave() {
        currentWave++;
        enemiesInCurrentWave = 2 + currentWave * 2;
        enemiesRemainingToSpawn = enemiesInCurrentWave;
        spawnedInCurrentWave = 0;
        spawnCooldown = 0;
        phase = Phase.SPAWNING;
    }

    public int getCurrentWave() { return currentWave; }
    public int getTotalWaves() { return TOTAL_WAVES; }
    public int getEnemiesRemainingToSpawn() { return enemiesRemainingToSpawn; }
    public int getEnemiesInCurrentWave() { return enemiesInCurrentWave; }

    public boolean isCountingDownToWave() {
        return phase == Phase.PREPARATION || phase == Phase.INTERMISSION;
    }

    public int getSecondsUntilNextWave() {
        return isCountingDownToWave() ? ticksToSeconds(phaseTicks) : 0;
    }

    public int getWaveDelaySeconds() {
        return waveDelaySeconds;
    }

    /**
     * Настраивает паузу перед следующими волнами. Если обратный отсчёт уже
     * идёт, новое значение применяется к нему немедленно.
     */
    public void setWaveDelaySeconds(int seconds) {
        if (seconds < MIN_WAVE_DELAY_SECONDS || seconds > MAX_WAVE_DELAY_SECONDS) {
            throw new IllegalArgumentException("Задержка волны должна быть от "
                    + MIN_WAVE_DELAY_SECONDS + " до " + MAX_WAVE_DELAY_SECONDS + " секунд");
        }
        waveDelaySeconds = seconds;
        if (isCountingDownToWave()) phaseTicks = secondsToTicks(seconds);
    }

    public String getStatusText() {
        switch (phase) {
            case PREPARATION:
                return "Подготовка к первой волне: " + ticksToSeconds(phaseTicks) + " с";
            case INTERMISSION:
                return "Следующая волна через " + ticksToSeconds(phaseTicks) + " с";
            case SPAWNING:
                return "Волна " + currentWave + ": осталось создать " + enemiesRemainingToSpawn;
            case WAITING_FOR_CLEAR:
                return "Волна " + currentWave + ": уничтожьте оставшихся врагов";
            case FINISHED:
                return "Все волны пройдены";
            default:
                return "";
        }
    }

    public String getNextWaveCompositionText() {
        int wave = Math.max(1, currentWave == 0 ? 1 : Math.min(TOTAL_WAVES, currentWave + 1));
        switch (wave) {
            case 1: return "обычные + быстрые";
            case 2: return "обычные + быстрые";
            case 3: return "обычные + быстрые + бронированные";
            case 4: return "смешанная волна + тяжёлые";
            default: return "все типы роботов";
        }
    }

    private int ticksToSeconds(int ticks) {
        return Math.max(0, (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
    }

    private int secondsToTicks(int seconds) {
        return seconds * TICKS_PER_SECOND;
    }
}
