package towerdefence.building;

import towerdefence.resource.ResourceType;
import towerdefence.world.Direction;
import towerdefence.world.Tile;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Пристройка 1x1 к Workshop. Создаёт внутренний gateway на соответствующей стороне. */
public class FactoryPort extends Building {
    public static final int BUFFER_CAPACITY = 12;
    private static final ResourceType[] FILTER_CYCLE = {
            null, ResourceType.METAL, ResourceType.COAL, ResourceType.SCRAP,
            ResourceType.OIL, ResourceType.PLATE, ResourceType.COMPONENT,
            ResourceType.AMMO, ResourceType.ROBOT_KIT
    };

    private final boolean input;
    private final Deque<ResourceType> gatewayBuffer = new ArrayDeque<>();
    private final ResourceType[] laneFilters = new ResourceType[Workshop.INTERIOR_SCALE];
    private Direction attachedSide;
    private Workshop workshop;
    private boolean externalTransferUsed;
    private boolean externalConnected;
    private int gatewayCursor;

    public FactoryPort(Tile position, boolean input) {
        super(120, 0, position, input ? new Color(68, 145, 178) : new Color(178, 120, 68));
        this.input = input;
        this.type = input ? BuildingType.FACTORY_INPUT_PORT : BuildingType.FACTORY_OUTPUT_PORT;
    }

    public boolean isInput() { return input; }
    public Direction getAttachedSide() { return attachedSide; }
    public Workshop getWorkshop() { return workshop; }
    public int getBufferSize() { return gatewayBuffer.size(); }
    public int getBufferCapacity() { return BUFFER_CAPACITY; }
    public boolean isExternalConnected() { return externalConnected; }

    public boolean attachTo(Workshop workshop, Direction side) {
        if (workshop == null || side == null) return false;
        if (this.workshop != null && this.workshop != workshop) this.workshop.unregisterPort(this);
        this.workshop = workshop;
        this.attachedSide = side;
        workshop.registerPort(this);
        return true;
    }

    public List<Point> getGatewayCells() {
        return workshop == null ? Collections.<Point>emptyList() : workshop.getGatewayCells(this);
    }

    public ResourceType getLaneFilter(int lane) {
        return lane >= 0 && lane < laneFilters.length ? laneFilters[lane] : null;
    }

    /** null = ANY. */
    public void setLaneFilter(int lane, ResourceType filter) {
        if (lane >= 0 && lane < laneFilters.length) laneFilters[lane] = filter;
    }

    public boolean acceptsLane(int lane, ResourceType type) {
        if (lane < 0 || lane >= laneFilters.length || type == null) return false;
        ResourceType filter = laneFilters[lane];
        return filter == null || filter == type;
    }

    /** Переключает ANY → Metal → Coal → Scrap → Oil → Plate → Component → Ammo → Robot → ANY. */
    public ResourceType cycleLaneFilter(int lane) {
        if (lane < 0 || lane >= laneFilters.length) return null;
        ResourceType current = laneFilters[lane];
        int index = 0;
        for (int i = 0; i < FILTER_CYCLE.length; i++) {
            if (FILTER_CYCLE[i] == current) { index = i; break; }
        }
        laneFilters[lane] = FILTER_CYCLE[(index + 1) % FILTER_CYCLE.length];
        return laneFilters[lane];
    }

    /** Сбрасывает ограничение одной внешней передачи на новый логистический такт. */
    public void beginLogisticsTick() {
        externalTransferUsed = false;
        externalConnected = false;
    }

    public void setExternalConnected(boolean connected) { externalConnected = connected; }

    /** Внешний conveyor может положить во входной Gateway максимум один предмет за такт. */
    public boolean acceptExternalResource(ResourceType type) {
        if (!input || type == null || externalTransferUsed || gatewayBuffer.size() >= BUFFER_CAPACITY
                || workshop == null || !workshop.isOperational()) return false;
        gatewayBuffer.addLast(type);
        externalTransferUsed = true;
        return true;
    }

    public ResourceType peekInputResource() { return input ? gatewayBuffer.peekFirst() : null; }
    public ResourceType commitInputToInterior() { return input ? gatewayBuffer.pollFirst() : null; }

    /** Внутренняя линия складывает готовый к выходу предмет в буфер OUTPUT Gateway. */
    public boolean offerInteriorOutput(ResourceType type) {
        if (input || type == null || gatewayBuffer.size() >= BUFFER_CAPACITY
                || workshop == null || !workshop.isOperational()) return false;
        gatewayBuffer.addLast(type);
        return true;
    }

    public ResourceType peekOutputResource() {
        return !input && !externalTransferUsed ? gatewayBuffer.peekFirst() : null;
    }

    public ResourceType commitOutputToExternal() {
        if (input || externalTransferUsed || gatewayBuffer.isEmpty()) return null;
        externalTransferUsed = true;
        return gatewayBuffer.pollFirst();
    }

    public int getGatewayCursor() { return gatewayCursor; }
    public void setGatewayCursor(int gatewayCursor) {
        this.gatewayCursor = Math.floorMod(gatewayCursor, Workshop.INTERIOR_SCALE);
    }

    public void detach() {
        if (workshop != null) workshop.unregisterPort(this);
        workshop = null;
        attachedSide = null;
        gatewayBuffer.clear();
        externalTransferUsed = false;
        externalConnected = false;
        gatewayCursor = 0;
        for (int i = 0; i < laneFilters.length; i++) laneFilters[i] = null;
    }

    @Override
    public void render(Graphics g, int tileSize) {
        if (position == null) return;
        int x = position.getX() * tileSize;
        int y = position.getY() * tileSize;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(input ? new Color(46, 92, 125) : new Color(125, 82, 46));
        g2.fillRoundRect(x + 4, y + 4, tileSize - 8, tileSize - 8, 7, 7);
        g2.setColor(color);
        g2.fillRoundRect(x + 7, y + 7, tileSize - 14, tileSize - 14, 6, 6);
        g2.setColor(Color.WHITE);
        int cx = x + tileSize / 2;
        int cy = y + tileSize / 2;
        int len = tileSize / 4;
        if (attachedSide == null) {
            g2.drawString(input ? "IN" : "OUT", x + 4, y + tileSize - 5);
        } else {
            int dx = attachedSide.getDx();
            int dy = attachedSide.getDy();
            if (!input) { dx = -dx; dy = -dy; }
            g2.drawLine(cx - dx * len, cy - dy * len, cx + dx * len, cy + dy * len);
            g2.drawLine(cx + dx * len, cy + dy * len, cx + dx * len - dy * 4, cy + dy * len - dx * 4);
            g2.drawLine(cx + dx * len, cy + dy * len, cx + dx * len + dy * 4, cy + dy * len + dx * 4);
        }

        g2.setColor(externalConnected ? new Color(80, 230, 125) : new Color(235, 95, 85));
        g2.fillOval(x + tileSize - 9, y + 3, 6, 6);
        if (!gatewayBuffer.isEmpty()) {
            g2.setColor(Color.WHITE);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, tileSize / 5)));
            g2.drawString(Integer.toString(gatewayBuffer.size()), x + 3, y + tileSize - 3);
        }
        g2.dispose();
    }

    @Override public void update() { }
}
