package towerdefence.resource;

import java.awt.Color;

/**
 * Унифицированные физические предметы экономики. Первые пять значений — сырьё,
 * остальные — продукты производства; Inventory и конвейеры могут хранить их одинаково.
 */
public enum ResourceType {
    METAL("Металл", new Color(165, 175, 185)),
    SCRAP("Металлолом", new Color(195, 120, 70)),
    ORGANIC("Органика", new Color(90, 165, 85)),
    COAL("Уголь", new Color(65, 65, 70)),
    OIL("Нефть", new Color(45, 40, 55)),
    WATER("Вода", new Color(75, 150, 225)),
    PLATE("Пластина", new Color(185, 195, 205)),
    BEAM("Балка", new Color(145, 160, 175)),
    ALLOY("Сплав", new Color(170, 190, 200)),
    COMPONENT("Компонент", new Color(180, 145, 210)),
    AMMO("Боеприпас", new Color(225, 190, 80)),
    STABILIZER_I("Стабилизатор I", new Color(235, 205, 75)),
    COOLING_MODULE("Модуль охлаждения", new Color(80, 205, 235)),
    STABILIZER_II("Стабилизатор II", new Color(245, 165, 70)),
    ROBOT_KIT("Комплект робота", new Color(110, 205, 145)),
    COOLANT("Охладитель", new Color(80, 220, 225)),
    FUEL("Топливо", new Color(210, 135, 65)),
    LUBRICANT("Смазка", new Color(155, 125, 80)),
    EXPLOSIVES("Взрывчатка", new Color(220, 85, 75));

    private final String displayName;
    private final Color color;

    ResourceType(String displayName, Color color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() { return displayName; }
    public Color getColor() { return color; }

    public boolean isLiquid() {
        return this == WATER || this == OIL || this == COOLANT || this == FUEL || this == LUBRICANT;
    }

    public boolean isManufactured() {
        return ordinal() >= PLATE.ordinal();
    }
}
