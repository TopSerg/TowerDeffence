
public class Tile {
    private int x, y;
    private TileType type;  // Тип: земля, вода, камень и т.д. (enum)
    private Resource resource;  // Ресурс на тайле (металлолом, руда)
    private Building building;  // Постройка на тайле (null, если пусто)
    private Unit unit;
    private boolean isPassable; // Можно ли ходить/строить

    public Tile(int x, int y, TileType type, Resource resource, Building building, boolean isPassable){
        this.x = x;
        this.y = y;
        this.type = type;
        this.resource = resource;
        this.building = building;
        this.isPassable = isPassable;
    }

    // Конструктор, геттеры, сеттеры

    public boolean hasUnit() { return unit != null; }
    public void setUnit(Unit unit) { this.unit = unit; }
    public boolean hasResource() { return resource != null; }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public boolean hasBuilding(){return building != null;}

    public void setBuilding(Building building) {
        this.building = building;
    }

    public TileType getType() {
        return type;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setType(TileType type) {
        this.type = type;
    }

    public Unit getUnit() {
        return unit;
    }

    public boolean isPassable() {
        return isPassable;
    }

    public void setPassable(boolean passable) {
        isPassable = passable;
    }
}