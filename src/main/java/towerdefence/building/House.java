package towerdefence.building;

import towerdefence.resource.Inventory;
import towerdefence.resource.ResourceType;
import towerdefence.world.Tile;

import java.awt.*;

public class House extends Building implements ResourceStorage {
    public static final int MAX_HEALTH = 300;
    public static final int STARTING_AMMO = 500;
    public static final int STARTING_METAL = 220;
    public static final int STARTING_COAL = 80;
    public static final int STARTING_SCRAP = 15;
    public static final int STORAGE_CAPACITY = 10000;

    private int ammoStock = STARTING_AMMO;
    private final Inventory inventory = new Inventory(STORAGE_CAPACITY);

    public House(Tile position, Color color) {
        super(MAX_HEALTH, 0, position, color);
        this.type = BuildingType.MAIN_BUILDING;
        inventory.add(ResourceType.METAL, STARTING_METAL);
        inventory.add(ResourceType.COAL, STARTING_COAL);
        inventory.add(ResourceType.SCRAP, STARTING_SCRAP);
    }

    public int getAmmoStock() { return ammoStock; }
    public int getMaxHealth() { return MAX_HEALTH; }
    @Override public Inventory getInventory() { return inventory; }

    public boolean takeAmmo(int amount) {
        if (amount <= 0 || ammoStock < amount) return false;
        ammoStock -= amount;
        return true;
    }

    public void addAmmo(int amount) { if (amount > 0) ammoStock += amount; }

    public boolean canAfford(BuildableType type) { return type != null && canAfford(type.getCost()); }

    public boolean canAfford(java.util.Map<ResourceType, Integer> cost) {
        if (cost == null) return false;
        for (java.util.Map.Entry<ResourceType, Integer> entry : cost.entrySet()) {
            if (!inventory.has(entry.getKey(), entry.getValue())) return false;
        }
        return true;
    }

    public boolean payFor(BuildableType type) { return type != null && payFor(type.getCost()); }

    public boolean payFor(java.util.Map<ResourceType, Integer> cost) {
        if (!canAfford(cost)) return false;
        for (java.util.Map.Entry<ResourceType, Integer> entry : cost.entrySet()) {
            inventory.remove(entry.getKey(), entry.getValue());
        }
        return true;
    }

    public void refund(BuildableType type) { if (type != null) refund(type.getCost()); }

    public void refund(java.util.Map<ResourceType, Integer> resources) {
        if (resources == null) return;
        for (java.util.Map.Entry<ResourceType, Integer> entry : resources.entrySet()) {
            inventory.addUpToCapacity(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void render(Graphics g, int tileSize) {
        int x = position.getX() * tileSize;
        int y = position.getY() * tileSize;

        g.setColor(new Color(115, 48, 45));
        g.fillRect(x + 3, y + 8, tileSize - 6, tileSize - 11);
        g.setColor(new Color(155, 65, 58));
        int[] roofX = {x + 2, x + tileSize / 2, x + tileSize - 2};
        int[] roofY = {y + 11, y + 2, y + 11};
        g.fillPolygon(roofX, roofY, 3);

        g.setColor(new Color(240, 210, 145));
        g.fillRect(x + tileSize / 2 - 3, y + tileSize - 12, 7, 9);

        int barWidth = tileSize - 6;
        int healthWidth = Math.max(0, barWidth * health / MAX_HEALTH);
        g.setColor(new Color(55, 20, 20));
        g.fillRect(x + 3, y + 1, barWidth, 4);
        g.setColor(new Color(90, 220, 105));
        g.fillRect(x + 3, y + 1, healthWidth, 4);

        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, tileSize / 5)));
        g.drawString(Integer.toString(ammoStock), x + 3, y + tileSize - 3);
    }

    @Override public void update() { }
}
