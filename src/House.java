import java.awt.*;

public class House extends Building {
    public House(Tile position, Color color) {
        super(100, 0, position, color);
    }

    @Override
    public void update() {
        if (health < 100) health++;
    }
}
