public class Resource {
    private ResourceType type; // Металл, органика, электроника
    private int amount;        // Количество

    // Конструктор, геттеры

    public Resource(ResourceType type, int amount){
        this.type = type;
        this.amount = amount;
    }

    public int getAmount() {
        return amount;
    }

    public ResourceType getType() {
        return type;
    }
}