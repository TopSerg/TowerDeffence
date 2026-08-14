package towerdefence.building;

import towerdefence.combat.MachineGunTower;
import towerdefence.combat.SlowTower;
import towerdefence.combat.SniperTower;
import towerdefence.resource.ResourceType;

import java.awt.Color;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public enum BuildableType {
    MACHINE_GUN_TOWER(
            "Пулемётная башня", "Оборона",
            "Быстро стреляет по ближайшему роботу. Требует постоянной подачи патронов.",
            new Color(72, 92, 120), 42, 5, 300,
            1, 1,
            MachineGunTower.DAMAGE, MachineGunTower.ATTACK_RANGE,
            MachineGunTower.FIRE_COOLDOWN_TICKS, MachineGunTower.MAX_AMMO,
            "Высокая скорострельность"),
    SNIPER_TOWER(
            "Снайперская башня", "Оборона",
            "Дальнобойная башня с мощным одиночным выстрелом.",
            new Color(83, 76, 145), 70, 12, 420,
            1, 1,
            SniperTower.DAMAGE, SniperTower.ATTACK_RANGE,
            SniperTower.FIRE_COOLDOWN_TICKS, SniperTower.MAX_AMMO,
            "Выбирает самого прочного врага"),
    SLOW_TOWER(
            "Замедляющая башня", "Оборона",
            "Наносит небольшой урон и снижает скорость роботов.",
            new Color(58, 145, 158), 55, 10, 360,
            1, 1,
            SlowTower.DAMAGE, SlowTower.ATTACK_RANGE,
            SlowTower.FIRE_COOLDOWN_TICKS, SlowTower.MAX_AMMO,
            "Замедляет цель на 45%"),
    WALL(
            "Стена", "Оборона",
            "Прочная преграда. Сначала размещается чертёж, затем её строит рабочий.",
            new Color(126, 130, 138), 8, 1, 90, 1, 1),
    CONVEYOR(
            "Конвейер", "Логистика",
            "Перевозит ресурсы и патроны. После размещения его собирает рабочий. R — повернуть.",
            new Color(190, 145, 45), 2, 0, 45, 1, 1),
    DRILL(
            "Бур", "Производство",
            "Устанавливается на металл или уголь и выдаёт добычу на конвейер.",
            new Color(92, 150, 160), 28, 4, 240, 1, 1),
    WORKSHOP(
            "Workshop", "Фабрики",
            "Первая фабрика. Снаружи занимает 3×3, внутри открывает пространство 9×9.",
            new Color(98, 108, 140), 110, 22, 720, 3, 3),
    FACTORY_INPUT_PORT(
            "Вход фабрики", "Фабрики",
            "Пристройка 1×1 к Workshop. Превращает внешний поток в внутренний gateway из трёх линий.",
            new Color(68, 145, 178), 12, 2, 150, 1, 1),
    FACTORY_OUTPUT_PORT(
            "Выход фабрики", "Фабрики",
            "Пристройка 1×1 к Workshop. Позволяет выводить обработанные ресурсы наружу.",
            new Color(178, 120, 68), 12, 2, 150, 1, 1);

    private final String displayName;
    private final String category;
    private final String description;
    private final Color previewColor;
    private final Map<ResourceType, Integer> cost;
    private final int buildTimeTicks;
    private final int footprintWidth;
    private final int footprintHeight;
    private final int damage;
    private final double range;
    private final int cooldownTicks;
    private final int ammoCapacity;
    private final String specialAction;

    BuildableType(String displayName, String category, String description,
                  Color previewColor, int metalCost, int coalCost, int buildTimeTicks,
                  int footprintWidth, int footprintHeight) {
        this(displayName, category, description, previewColor, metalCost, coalCost, buildTimeTicks,
                footprintWidth, footprintHeight, 0, 0, 0, 0, "");
    }

    BuildableType(String displayName, String category, String description,
                  Color previewColor, int metalCost, int coalCost, int buildTimeTicks,
                  int footprintWidth, int footprintHeight,
                  int damage, double range, int cooldownTicks, int ammoCapacity,
                  String specialAction) {
        this.displayName = displayName;
        this.category = category;
        this.description = description;
        this.previewColor = previewColor;
        this.buildTimeTicks = Math.max(1, buildTimeTicks);
        this.footprintWidth = Math.max(1, footprintWidth);
        this.footprintHeight = Math.max(1, footprintHeight);
        this.damage = damage;
        this.range = range;
        this.cooldownTicks = cooldownTicks;
        this.ammoCapacity = ammoCapacity;
        this.specialAction = specialAction == null ? "" : specialAction;
        EnumMap<ResourceType, Integer> values = new EnumMap<>(ResourceType.class);
        if (metalCost > 0) values.put(ResourceType.METAL, metalCost);
        if (coalCost > 0) values.put(ResourceType.COAL, coalCost);
        this.cost = Collections.unmodifiableMap(values);
    }

    public String getDisplayName() { return displayName; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public Color getPreviewColor() { return previewColor; }
    public Map<ResourceType, Integer> getCost() { return cost; }
    public int getBuildTimeTicks() { return buildTimeTicks; }
    public int getFootprintWidth() { return footprintWidth; }
    public int getFootprintHeight() { return footprintHeight; }
    public int getDamage() { return damage; }
    public double getRange() { return range; }
    public int getCooldownTicks() { return cooldownTicks; }
    public int getAmmoCapacity() { return ammoCapacity; }
    public String getSpecialAction() { return specialAction; }
    public boolean isTower() { return damage > 0 && range > 0 && cooldownTicks > 0; }
    public double getShotsPerSecond() { return isTower() ? 60.0 / cooldownTicks : 0.0; }

    public int getCost(ResourceType type) { return cost.getOrDefault(type, 0); }

    public String getCostText() {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<ResourceType, Integer> entry : cost.entrySet()) {
            if (result.length() > 0) result.append(" · ");
            result.append(entry.getKey().getDisplayName()).append(": ").append(entry.getValue());
        }
        return result.length() == 0 ? "Бесплатно" : result.toString();
    }

    public String getStatsText() {
        String build = "стройка: " + format(buildTimeTicks / 60.0) + " с";
        String footprint = footprintWidth > 1 || footprintHeight > 1
                ? " · размер: " + footprintWidth + "×" + footprintHeight : "";
        if (!isTower()) return build + footprint;
        return "Урон: " + damage + " · дальность: " + format(range)
                + " · скорость: " + format(getShotsPerSecond()) + " выстр./с"
                + " · магазин: " + ammoCapacity + footprint + " · " + build;
    }

    public static BuildableType fromBuilding(Building building) {
        if (building instanceof ConstructionSite) return ((ConstructionSite) building).getTargetType();
        if (building instanceof MachineGunTower) return MACHINE_GUN_TOWER;
        if (building instanceof SniperTower) return SNIPER_TOWER;
        if (building instanceof SlowTower) return SLOW_TOWER;
        if (building instanceof Wall) return WALL;
        if (building instanceof Conveyor) return CONVEYOR;
        if (building instanceof Drill) return DRILL;
        if (building instanceof Workshop) return WORKSHOP;
        if (building instanceof FactoryPort) return ((FactoryPort) building).isInput() ? FACTORY_INPUT_PORT : FACTORY_OUTPUT_PORT;
        return null;
    }

    private String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001) return Integer.toString((int) Math.rint(value));
        return String.format(java.util.Locale.US, "%.1f", value);
    }
}
