package towerdefence.resource;

public class Resource {
    private final ResourceType type;
    private int amount;

    public Resource(ResourceType type, int amount) {
        if (type == null) throw new IllegalArgumentException("Тип ресурса не может быть null");
        this.type = type;
        this.amount = Math.max(0, amount);
    }

    public int getAmount() {
        return amount;
    }

    public ResourceType getType() {
        return type;
    }

    public int extract(int requestedAmount) {
        if (requestedAmount <= 0 || amount <= 0) return 0;
        int extracted = Math.min(requestedAmount, amount);
        amount -= extracted;
        return extracted;
    }

    public boolean isDepleted() {
        return amount <= 0;
    }

    public Resource copy() {
        return new Resource(type, amount);
    }
}
