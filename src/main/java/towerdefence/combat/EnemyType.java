package towerdefence.combat;

import java.awt.Color;

/** Четыре роли роботов, требующие разной оборонительной тактики. */
public enum EnemyType {
    NORMAL(
            "Обычный робот",
            82, 0.036f, 18,
            2, 0,
            new Color(218, 72, 68)
    ),
    FAST(
            "Быстрый робот",
            52, 0.060f, 12,
            1, 0,
            new Color(238, 145, 55)
    ),
    ARMORED(
            "Бронированный робот",
            150, 0.029f, 22,
            4, 5,
            new Color(92, 116, 138)
    ),
    HEAVY(
            "Тяжёлый робот",
            270, 0.021f, 36,
            8, 2,
            new Color(142, 52, 76)
    );

    private final String displayName;
    private final int baseHealth;
    private final float baseSpeed;
    private final int baseDamage;
    private final int scrapReward;
    private final int flatArmor;
    private final Color color;

    EnemyType(String displayName, int baseHealth, float baseSpeed, int baseDamage,
              int scrapReward, int flatArmor, Color color) {
        this.displayName = displayName;
        this.baseHealth = baseHealth;
        this.baseSpeed = baseSpeed;
        this.baseDamage = baseDamage;
        this.scrapReward = scrapReward;
        this.flatArmor = flatArmor;
        this.color = color;
    }

    public String getDisplayName() { return displayName; }
    public int getBaseHealth() { return baseHealth; }
    public float getBaseSpeed() { return baseSpeed; }
    public int getBaseDamage() { return baseDamage; }
    public int getScrapReward() { return scrapReward; }
    public int getFlatArmor() { return flatArmor; }
    public Color getColor() { return color; }

    public String getStatsText() {
        return "HP " + baseHealth
                + " · скорость " + String.format(java.util.Locale.US, "%.3f", baseSpeed)
                + " · урон базе " + baseDamage
                + " · лом " + scrapReward
                + (flatArmor > 0 ? " · броня " + flatArmor : "");
    }
}
