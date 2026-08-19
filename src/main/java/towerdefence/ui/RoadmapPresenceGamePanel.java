package towerdefence.ui;

import towerdefence.building.Workshop;
import towerdefence.game.RoadmapGameState;
import towerdefence.roadmap.RoadmapRuntime;
import towerdefence.world.GameMap;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.List;

/** Добавляет явное присутствие стартового внутреннего строителя и Васи в interior-view. */
public class RoadmapPresenceGamePanel extends RoadmapGamePanel {
    private static final Field VIEWED_WORKSHOP_FIELD = viewedWorkshopField();
    private final RoadmapGameState state;

    public RoadmapPresenceGamePanel(GameMap map, RoadmapGameState state) {
        super(map, state);
        this.state = state;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Workshop viewed = getViewedWorkshop();
        if (viewed == null) return;
        RoadmapRuntime.FactoryState factory = state.getRoadmap().getFactoryState(viewed);
        if (factory == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        Rectangle bounds = interiorBounds();
        int cell = bounds.width / 9;
        drawBuilderBots(g2, bounds, cell, state.getInternalBuilderPositions(viewed));
        if (state.getRoadmap().getVasyaInsideWorkshop() == viewed) {
            drawVasya(g2, bounds, cell);
        } else if (state.getRequestedWorkshop() == viewed) {
            g2.setColor(new Color(70, 170, 245));
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            g2.drawString("ВАСЯ БЕЖИТ К ФАБРИКЕ →", bounds.x + 6, bounds.y - 10);
        }
        g2.dispose();
    }

    private void drawBuilderBots(Graphics2D g2, Rectangle bounds, int cell, List<Point> bots) {
        for (Point bot : bots) {
            int px = bounds.x + bot.x * cell;
            int py = bounds.y + bot.y * cell;
            int size = Math.max(12, cell / 2);
            int x = px + (cell - size) / 2;
            int y = py + (cell - size) / 2;

            g2.setColor(new Color(32, 45, 58, 235));
            g2.fillRoundRect(x, y, size, size, 6, 6);
            g2.setColor(new Color(85, 225, 235));
            g2.drawRoundRect(x, y, size, size, 6, 6);
            g2.fillOval(x + size / 4, y + size / 3, 3, 3);
            g2.fillOval(x + size * 3 / 4 - 3, y + size / 3, 3, 3);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 7));
            g2.drawString("BOT", x + 2, y + size - 2);
        }
    }

    private void drawVasya(Graphics2D g2, Rectangle bounds, int cell) {
        int cx = bounds.x + 4 * cell + cell / 2;
        int cy = bounds.y + 6 * cell + cell / 2;
        int size = Math.max(12, cell / 2);
        g2.setColor(new Color(45, 115, 230));
        g2.fillOval(cx - size / 2, cy - size / 2, size, size);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
        g2.drawString("V", cx - 3, cy + 3);
    }

    private Workshop getViewedWorkshop() {
        try {
            return (Workshop) VIEWED_WORKSHOP_FIELD.get(this);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Не удалось прочитать interior-view Workshop", exception);
        }
    }

    private Rectangle interiorBounds() {
        int availableW = Math.max(9 * 22, getWidth() - 248);
        int availableH = Math.max(9 * 22, getHeight() - 118);
        int cell = Math.max(22, Math.min(48, Math.min(availableW / 9, availableH / 9)));
        int size = cell * 9;
        return new Rectangle(Math.max(12, (getWidth() - size - 212) / 2), 56, size, size);
    }

    private static Field viewedWorkshopField() {
        try {
            Field field = RoadmapGamePanel.class.getDeclaredField("viewedWorkshop");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("RoadmapGamePanel изменился: нет viewedWorkshop", exception);
        }
    }
}
