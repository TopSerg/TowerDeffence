import java.awt.*;

public abstract class Entity {
    protected int health;
    protected float speed;
    protected boolean isAlive = true;

    protected Tile position;

    protected Color color;

    public Entity(int health, float speed, Tile startPosition, Color color){
        this.health = health;
        this.speed = speed;
        this.position = startPosition;
        this.color = color;
    }

    public abstract void render(Graphics g, int tileSize);

    public void takeDamage(int damage){
        health -= damage;
    }

    public abstract void update();

    public Tile getPosition() {
        return position;
    }

    public int getHealth() {
        return health;
    }

    public void setHealth(int health) {
        this.health = health;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public boolean isAlive() {
        return isAlive;
    }

    public void setAlive(boolean alive) {
        isAlive = alive;
    }

    public void setPosition(Tile position) {
        this.position = position;
    }
}
