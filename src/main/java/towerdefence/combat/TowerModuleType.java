package towerdefence.combat;

import towerdefence.resource.ResourceType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Модули устанавливаются прямо на выбранную боевую башню. */
public enum TowerModuleType {
    STABILIZER_I(
            "Стабилизатор I",
            "Ускоряет наведение и подачу патронов на 18%. После установки башня начинает нагреваться.",
            18, 4,
            null,
            0.82, 10.0f, 0.0f, 0.0f
    ),
    COOLING_I(
            "Жидкостное охлаждение",
            "Увеличивает предел нагрева и ускоряет охлаждение. Требуется для второго стабилизатора.",
            24, 6,
            STABILIZER_I,
            1.0, 0.0f, 45.0f, 0.95f
    ),
    STABILIZER_II(
            "Стабилизатор II",
            "Ещё сильнее ускоряет стрельбу, но добавляет дополнительный нагрев каждого выстрела.",
            34, 9,
            COOLING_I,
            0.78, 7.0f, 0.0f, 0.0f
    );

    private final String displayName;
    private final String description;
    private final Map<ResourceType, Integer> cost;
    private final TowerModuleType prerequisite;
    private final double cooldownMultiplier;
    private final float heatPerShot;
    private final float maxHeatBonus;
    private final float coolingBonus;

    TowerModuleType(String displayName, String description,
                    int metalCost, int scrapCost,
                    TowerModuleType prerequisite,
                    double cooldownMultiplier,
                    float heatPerShot,
                    float maxHeatBonus,
                    float coolingBonus) {
        this.displayName = displayName;
        this.description = description;
        this.prerequisite = prerequisite;
        this.cooldownMultiplier = cooldownMultiplier;
        this.heatPerShot = heatPerShot;
        this.maxHeatBonus = maxHeatBonus;
        this.coolingBonus = coolingBonus;

        EnumMap<ResourceType, Integer> values = new EnumMap<>(ResourceType.class);
        if (metalCost > 0) values.put(ResourceType.METAL, metalCost);
        if (scrapCost > 0) values.put(ResourceType.SCRAP, scrapCost);
        this.cost = Collections.unmodifiableMap(values);
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Map<ResourceType, Integer> getCost() { return cost; }
    public TowerModuleType getPrerequisite() { return prerequisite; }
    public double getCooldownMultiplier() { return cooldownMultiplier; }
    public float getHeatPerShot() { return heatPerShot; }
    public float getMaxHeatBonus() { return maxHeatBonus; }
    public float getCoolingBonus() { return coolingBonus; }

    public String getCostText() {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<ResourceType, Integer> entry : cost.entrySet()) {
            if (result.length() > 0) result.append(" · ");
            result.append(entry.getKey().getDisplayName()).append(": ").append(entry.getValue());
        }
        return result.toString();
    }
}
