import java.awt.*;

public abstract class Building extends Entity{
    protected BuildingType type;

    public Building(int health, float speed, Tile startPosition, Color color) {
        super(health, speed, startPosition, color);
    }

    public void render(Graphics g, int tileSize) {
        g.setColor(color);
        g.fillRect(
                position.getX() * tileSize,
                position.getY() * tileSize,
                tileSize, tileSize
        );
    }

    public void setPosition(Tile position) {
        this.position = position;
    }

    public abstract void update(); // Логика работы (например, генерация ресурсов)
}


