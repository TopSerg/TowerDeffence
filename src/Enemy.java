import java.awt.*;

public class Enemy extends Entity{
    Weapon weapon;
    GameMap map;

    private int viewArea;

    private float realX, realY; // Точные координаты для плавности

    public Enemy(int health, float speed, Tile startPosition, Color color) {
        super(health, speed, startPosition, color);
        weapon = new Weapon(WeaponType.MOUTH);
        viewArea = 5;
    }

    @Override
    public void render(Graphics g, int tileSize) {
        g.setColor(color);
        g.fillOval(
                (int)(position.getX() * tileSize) + 2,
                (int)(position.getY() * tileSize) + 2,
                tileSize - 4, tileSize - 4
        );
    }

    public Unit findNearestUnit(){
        int x = position.getX(), y = position.getY();
        for (int i = x-viewArea; i <= x + viewArea; i++){
            for (int j = y - viewArea; j <= y + viewArea; j++){
                if (map.getTile(i, j).hasUnit()){
                    return map.getTile(i, j).getUnit();
                }
            }
        }
        return null;
    }

    @Override
    public void update() {
        // Простейший ИИ: движение к ближайшему юниту
        Unit nearestUnit = findNearestUnit();
        if (nearestUnit != null) {
            float dx = nearestUnit.getPosition().getX() - realX;
            float dy = nearestUnit.getPosition().getY() - realY;
            float distance = (float)Math.sqrt(dx*dx + dy*dy);

            if (distance <= weapon.getRange()) {
                weapon.shoot(nearestUnit);
            } else {
                // Движение к цели
                realX += (dx / distance) * speed;
                realY += (dy / distance) * speed;
            }
        }
    }
}
