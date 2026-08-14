package towerdefence.building;

import towerdefence.resource.Inventory;
import towerdefence.resource.ResourceType;
import towerdefence.unit.Worker;
import towerdefence.world.Direction;
import towerdefence.world.Tile;

import java.awt.*;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Чертёж и недостроенное здание. Материалы доставляются рабочим физически. */
public class ConstructionSite extends Building {
    public static final int MAX_HEALTH = 90;

    private final BuildableType targetType;
    private final Direction direction;
    private final Map<ResourceType, Integer> required;
    private final Inventory delivered;
    private final int buildTimeTicks;
    private int buildProgress;
    private Worker assignedWorker;

    public ConstructionSite(Tile position, BuildableType targetType, Direction direction) {
        super(MAX_HEALTH, 0, position, new Color(90, 180, 235));
        if (targetType == null) throw new IllegalArgumentException("Не указан тип будущего здания");
        this.type = BuildingType.CONSTRUCTION_SITE;
        this.targetType = targetType;
        this.direction = direction == null ? Direction.RIGHT : direction;
        this.required = Collections.unmodifiableMap(new EnumMap<>(targetType.getCost()));
        int capacity = Math.max(1, required.values().stream().mapToInt(Integer::intValue).sum());
        this.delivered = new Inventory(capacity);
        this.buildTimeTicks = targetType.getBuildTimeTicks();
    }

    public int deliverFrom(Inventory source) {
        if (source == null) return 0;
        int moved = 0;
        for (ResourceType resourceType : ResourceType.values()) {
            int remaining = getRemainingNeeded(resourceType);
            if (remaining > 0) moved += source.transferTo(delivered, resourceType, remaining);
        }
        return moved;
    }

    public int work(int ticks) {
        if (!hasAllMaterials() || ticks <= 0 || isComplete()) return buildProgress;
        buildProgress = Math.min(buildTimeTicks, buildProgress + ticks);
        return buildProgress;
    }

    public boolean hasAllMaterials() {
        for (Map.Entry<ResourceType, Integer> entry : required.entrySet()) {
            if (delivered.getAmount(entry.getKey()) < entry.getValue()) return false;
        }
        return true;
    }

    public boolean isComplete() { return buildProgress >= buildTimeTicks; }
    public int getRemainingNeeded(ResourceType type) {
        return Math.max(0, required.getOrDefault(type, 0) - delivered.getAmount(type));
    }
    public int getTotalRemainingMaterials() {
        int remaining = 0;
        for (ResourceType type : ResourceType.values()) remaining += getRemainingNeeded(type);
        return remaining;
    }
    public ResourceType getNextMissingResource() {
        for (ResourceType type : ResourceType.values()) if (getRemainingNeeded(type) > 0) return type;
        return null;
    }

    public BuildableType getTargetType() { return targetType; }
    public Direction getDirection() { return direction; }
    public Map<ResourceType, Integer> getRequired() { return required; }
    public Inventory getDeliveredInventory() { return delivered; }
    public int getBuildProgress() { return buildProgress; }
    public int getBuildTimeTicks() { return buildTimeTicks; }
    public double getProgressFraction() { return buildTimeTicks <= 0 ? 1.0 : (double) buildProgress / buildTimeTicks; }
    public Worker getAssignedWorker() { return assignedWorker; }
    public void setAssignedWorker(Worker assignedWorker) { this.assignedWorker = assignedWorker; }

    @Override public void update() { }

    @Override public int getFootprintWidth() { return targetType.getFootprintWidth(); }
    @Override public int getFootprintHeight() { return targetType.getFootprintHeight(); }

    @Override
    public void render(Graphics g, int tileSize) {
        int x = position.getX() * tileSize;
        int y = position.getY() * tileSize;
        int widthPx = getFootprintWidth() * tileSize;
        int heightPx = getFootprintHeight() * tileSize;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(targetType.getPreviewColor().getRed(), targetType.getPreviewColor().getGreen(),
                targetType.getPreviewColor().getBlue(), 75));
        g2.fillRect(x + 3, y + 3, widthPx - 6, heightPx - 6);
        g2.setColor(new Color(125, 215, 255));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRect(x + 3, y + 3, widthPx - 7, heightPx - 7);
        g2.drawLine(x + 4, y + 4, x + widthPx - 4, y + heightPx - 4);
        g2.drawLine(x + widthPx - 4, y + 4, x + 4, y + heightPx - 4);

        int barWidth = widthPx - 6;
        int materialWidth = (int) Math.round(barWidth * materialFraction());
        int progressWidth = (int) Math.round(barWidth * getProgressFraction());
        g2.setColor(new Color(35, 38, 45));
        g2.fillRect(x + 3, y + heightPx - 7, barWidth, 4);
        g2.setColor(hasAllMaterials() ? new Color(255, 190, 75) : new Color(90, 175, 245));
        g2.fillRect(x + 3, y + heightPx - 7, hasAllMaterials() ? progressWidth : materialWidth, 4);
        g2.dispose();
    }

    private double materialFraction() {
        int total = required.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) return 1.0;
        int have = 0;
        for (Map.Entry<ResourceType, Integer> entry : required.entrySet()) {
            have += Math.min(entry.getValue(), delivered.getAmount(entry.getKey()));
        }
        return (double) have / total;
    }
}
