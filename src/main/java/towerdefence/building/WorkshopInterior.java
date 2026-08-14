package towerdefence.building;

import towerdefence.world.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Отдельная строительная сетка внутреннего пространства Workshop. */
public final class WorkshopInterior {
    private final int width;
    private final int height;
    private final InteriorConveyor[][] conveyors;

    public WorkshopInterior(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Размер интерьера должен быть положительным");
        this.width = width;
        this.height = height;
        this.conveyors = new InteriorConveyor[height][width];
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public boolean contains(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public InteriorConveyor getConveyor(int x, int y) {
        return contains(x, y) ? conveyors[y][x] : null;
    }

    public boolean isOccupied(int x, int y) {
        return getConveyor(x, y) != null;
    }

    public boolean placeConveyor(int x, int y, Direction direction) {
        if (!contains(x, y) || isOccupied(x, y)) return false;
        conveyors[y][x] = new InteriorConveyor(x, y, direction);
        return true;
    }

    public boolean removeConveyor(int x, int y) {
        if (!contains(x, y) || conveyors[y][x] == null) return false;
        conveyors[y][x] = null;
        return true;
    }

    public List<InteriorConveyor> getConveyors() {
        List<InteriorConveyor> result = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (conveyors[y][x] != null) result.add(conveyors[y][x]);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
