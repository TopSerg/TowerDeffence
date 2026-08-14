package towerdefence.resource;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Ограниченный и безопасный инвентарь для базы, рабочих и производственных объектов. */
public class Inventory {
    private final Map<ResourceType, Integer> resources = new EnumMap<>(ResourceType.class);
    private final int size;
    private int storedAmount;

    public Inventory(int size) {
        if (size <= 0) throw new IllegalArgumentException("Размер инвентаря должен быть положительным");
        this.size = size;
        for (ResourceType type : ResourceType.values()) resources.put(type, 0);
    }

    public boolean addItem(Resource resource) {
        return resource != null && add(resource.getType(), resource.getAmount());
    }

    /** Добавляет всё количество либо не изменяет инвентарь. */
    public boolean add(ResourceType type, int amount) {
        if (type == null || amount <= 0 || amount > getFreeSpace()) return false;
        resources.put(type, getAmount(type) + amount);
        storedAmount += amount;
        return true;
    }

    /** Добавляет столько, сколько помещается, и возвращает принятое количество. */
    public int addUpToCapacity(ResourceType type, int amount) {
        if (type == null || amount <= 0) return 0;
        int accepted = Math.min(amount, getFreeSpace());
        if (accepted <= 0) return 0;
        resources.put(type, getAmount(type) + accepted);
        storedAmount += accepted;
        return accepted;
    }

    public boolean has(ResourceType type, int amount) {
        return type != null && amount >= 0 && getAmount(type) >= amount;
    }

    /** Удаляет всё количество либо не изменяет инвентарь. */
    public boolean remove(ResourceType type, int amount) {
        if (type == null || amount <= 0 || !has(type, amount)) return false;
        resources.put(type, getAmount(type) - amount);
        storedAmount -= amount;
        return true;
    }

    /** Удаляет не больше доступного количества и возвращает фактически удалённое. */
    public int removeUpTo(ResourceType type, int amount) {
        if (type == null || amount <= 0) return 0;
        int removed = Math.min(amount, getAmount(type));
        if (removed <= 0) return 0;
        resources.put(type, getAmount(type) - removed);
        storedAmount -= removed;
        return removed;
    }

    /** Безопасно переносит ресурс между инвентарями. */
    public int transferTo(Inventory target, ResourceType type, int amount) {
        if (target == null || target == this || type == null || amount <= 0) return 0;
        int transferable = Math.min(amount, Math.min(getAmount(type), target.getFreeSpace()));
        if (transferable <= 0) return 0;
        if (!remove(type, transferable)) return 0;
        if (!target.add(type, transferable)) {
            add(type, transferable);
            return 0;
        }
        return transferable;
    }

    public int transferAllTo(Inventory target) {
        if (target == null || target == this) return 0;
        int moved = 0;
        for (ResourceType type : ResourceType.values()) {
            moved += transferTo(target, type, getAmount(type));
        }
        return moved;
    }

    public int getAmount(ResourceType type) {
        if (type == null) return 0;
        return resources.getOrDefault(type, 0);
    }

    public Map<ResourceType, Integer> getResources() {
        return Collections.unmodifiableMap(resources);
    }

    public int getSize() { return size; }
    public int getStoredAmount() { return storedAmount; }
    public int getFreeSpace() { return Math.max(0, size - storedAmount); }
    public boolean isEmpty() { return storedAmount == 0; }
    public boolean isFull() { return storedAmount >= size; }

    public void clear() {
        for (ResourceType type : ResourceType.values()) resources.put(type, 0);
        storedAmount = 0;
    }
}
