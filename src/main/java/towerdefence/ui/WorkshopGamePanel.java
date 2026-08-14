package towerdefence.ui;

import towerdefence.building.Building;
import towerdefence.building.FactoryPort;
import towerdefence.building.InteriorConveyor;
import towerdefence.building.Workshop;
import towerdefence.game.GameState;
import towerdefence.game.GameStatus;
import towerdefence.world.Direction;
import towerdefence.world.GameMap;
import towerdefence.world.Tile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;

/**
 * Расширяет существующий GamePanel только механикой внутренней геометрии Workshop.
 * Внешняя карта, бой, строительство и логистика остаются в исходном GamePanel без переписывания.
 */
public class WorkshopGamePanel extends GamePanel {
    private static final int INTERIOR_CELLS = 9;
    private static final int INFO_WIDTH = 200;
    private static final int PANEL_GAP = 12;

    private final GameMap worldMap;
    private final GameState gameState;

    private final Action originalToggleBuild;
    private final Action originalRotate;
    private final Action originalCancel;
    private final Action originalDelete;
    private final Action originalToggleWorkshop;

    private Workshop selectedWorkshop;
    private Workshop activeWorkshop;
    private Point hoveredInteriorCell;
    private Point selectedInteriorCell;
    private boolean conveyorBuildMode;
    private Direction conveyorDirection = Direction.RIGHT;
    private String interiorStatus = "";

    public WorkshopGamePanel(GameMap map, GameState state) {
        super(map, state);
        this.worldMap = map;
        this.gameState = state;

        ActionMap actionMap = getActionMap();
        originalToggleBuild = actionMap.get("toggleBuildMenu");
        originalRotate = actionMap.get("rotateBuilding");
        originalCancel = actionMap.get("cancelBuilding");
        originalDelete = actionMap.get("deleteBuilding");
        originalToggleWorkshop = actionMap.get("toggleWorkshopView");

        actionMap.put("toggleBuildMenu", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (activeWorkshop != null) toggleInteriorConveyorMode();
                else invoke(originalToggleBuild, e);
            }
        });
        actionMap.put("rotateBuilding", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (activeWorkshop != null) rotateInteriorConveyor();
                else invoke(originalRotate, e);
            }
        });
        actionMap.put("cancelBuilding", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (activeWorkshop != null) {
                    if (conveyorBuildMode) cancelInteriorConveyorMode();
                    else exitEnhancedInterior(originalCancel, e);
                } else {
                    invoke(originalCancel, e);
                }
            }
        });
        actionMap.put("deleteBuilding", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (activeWorkshop != null) deleteInteriorConveyor();
                else invoke(originalDelete, e);
            }
        });
        actionMap.put("toggleWorkshopView", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (activeWorkshop != null) {
                    exitEnhancedInterior(originalToggleWorkshop, e);
                    return;
                }
                if (isWorkshopSelectable(selectedWorkshop)) {
                    invoke(originalToggleWorkshop, e);
                    enterEnhancedInterior(selectedWorkshop);
                } else {
                    invoke(originalToggleWorkshop, e);
                }
            }
        });
    }

    private void invoke(Action action, ActionEvent event) {
        if (action != null) action.actionPerformed(event);
    }

    private boolean isWorkshopSelectable(Workshop workshop) {
        return workshop != null && workshop.isAlive() && gameState.getAllBuildings().contains(workshop);
    }

    private void enterEnhancedInterior(Workshop workshop) {
        if (!isWorkshopSelectable(workshop)) return;
        activeWorkshop = workshop;
        hoveredInteriorCell = null;
        selectedInteriorCell = null;
        conveyorBuildMode = false;
        interiorStatus = "Внутри Workshop · B — внутренний конвейер · R — повернуть · E/Esc — выйти";
        repaint();
    }

    private void exitEnhancedInterior(Action originalExitAction, ActionEvent event) {
        activeWorkshop = null;
        hoveredInteriorCell = null;
        selectedInteriorCell = null;
        conveyorBuildMode = false;
        invoke(originalExitAction, event);
        repaint();
    }

    private void toggleInteriorConveyorMode() {
        if (activeWorkshop == null || gameState.getStatus() != GameStatus.RUNNING) return;
        conveyorBuildMode = !conveyorBuildMode;
        selectedInteriorCell = null;
        interiorStatus = conveyorBuildMode
                ? "Внутренний конвейер · ЛКМ поставить · R повернуть · Esc отменить"
                : "Внутреннее строительство отменено · B — начать снова";
        repaint();
    }

    private void cancelInteriorConveyorMode() {
        conveyorBuildMode = false;
        interiorStatus = "Внутреннее строительство отменено · B — начать снова · E/Esc — выйти";
        repaint();
    }

    private void rotateInteriorConveyor() {
        if (activeWorkshop == null || gameState.getStatus() != GameStatus.RUNNING) return;
        if (conveyorBuildMode) {
            conveyorDirection = conveyorDirection.rotateClockwise();
            interiorStatus = "Внутренний конвейер · направление: " + conveyorDirection.getDisplayName();
            repaint();
            return;
        }
        Point cell = selectedInteriorCell != null ? selectedInteriorCell : hoveredInteriorCell;
        if (cell == null) {
            interiorStatus = "Выберите внутренний конвейер или нажмите B для строительства";
            repaint();
            return;
        }
        InteriorConveyor conveyor = activeWorkshop.getInteriorConveyor(cell.x, cell.y);
        if (conveyor == null) {
            interiorStatus = "В выбранной клетке нет внутреннего конвейера";
        } else {
            conveyor.rotateClockwise();
            selectedInteriorCell = new Point(cell);
            interiorStatus = "Внутренний конвейер повёрнут: " + conveyor.getDirection().getDisplayName();
        }
        repaint();
    }

    private void deleteInteriorConveyor() {
        if (activeWorkshop == null || gameState.getStatus() != GameStatus.RUNNING) return;
        Point cell = selectedInteriorCell != null ? selectedInteriorCell : hoveredInteriorCell;
        if (cell != null && activeWorkshop.removeInteriorConveyor(cell.x, cell.y)) {
            selectedInteriorCell = null;
            interiorStatus = "Внутренний конвейер удалён";
        } else {
            interiorStatus = "В выбранной внутренней клетке нет конвейера";
        }
        repaint();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        requestFocusInWindow();
        if (activeWorkshop != null) {
            handleInteriorClick(e);
            return;
        }

        Tile target = getWorldTileAt(e.getPoint());
        Workshop clickedWorkshop = target != null && target.getBuilding() instanceof Workshop
                ? (Workshop) target.getBuilding() : null;

        super.mouseClicked(e);

        if (SwingUtilities.isRightMouseButton(e)) {
            selectedWorkshop = null;
            return;
        }
        if (!SwingUtilities.isLeftMouseButton(e)) return;
        selectedWorkshop = clickedWorkshop;
        if (e.getClickCount() >= 2 && isWorkshopSelectable(clickedWorkshop)) {
            // super.mouseClicked уже включил штатный interior; поверх него включаем
            // расширенную редактируемую сетку, не вмешиваясь в остальную логику GamePanel.
            enterEnhancedInterior(clickedWorkshop);
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (activeWorkshop == null) {
            super.mouseMoved(e);
            return;
        }
        hoveredInteriorCell = getInteriorCellAt(e.getPoint());
        if (hoveredInteriorCell != null) {
            if (conveyorBuildMode) {
                interiorStatus = canPlaceInteriorConveyor(hoveredInteriorCell)
                        ? "Можно поставить внутренний конвейер · " + conveyorDirection.getDisplayName()
                        : getInteriorBuildFailureReason(hoveredInteriorCell);
            } else {
                InteriorConveyor conveyor = activeWorkshop.getInteriorConveyor(
                        hoveredInteriorCell.x, hoveredInteriorCell.y);
                if (conveyor != null) {
                    interiorStatus = "Внутренний конвейер · " + conveyor.getDirection().getDisplayName()
                            + " · ЛКМ выбрать · R повернуть · Delete удалить";
                } else if (activeWorkshop.isGatewayCell(hoveredInteriorCell.x, hoveredInteriorCell.y)) {
                    interiorStatus = "Gateway внешнего порта · внутренняя клетка "
                            + (hoveredInteriorCell.x + 1) + "," + (hoveredInteriorCell.y + 1);
                } else {
                    interiorStatus = "Внутри Workshop · ячейка " + (hoveredInteriorCell.x + 1) + ","
                            + (hoveredInteriorCell.y + 1) + " · сектор " + getInteriorSectorName(hoveredInteriorCell);
                }
            }
        }
        repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (activeWorkshop != null) mouseMoved(e);
        else super.mouseDragged(e);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (activeWorkshop != null) {
            hoveredInteriorCell = null;
            repaint();
        } else {
            super.mouseExited(e);
        }
    }

    private void handleInteriorClick(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e)) {
            if (conveyorBuildMode) {
                cancelInteriorConveyorMode();
            } else {
                exitEnhancedInterior(originalCancel,
                        new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "exitWorkshop"));
            }
            return;
        }
        if (!SwingUtilities.isLeftMouseButton(e) || gameState.getStatus() != GameStatus.RUNNING) return;

        Point cell = getInteriorCellAt(e.getPoint());
        if (cell == null) return;
        hoveredInteriorCell = cell;

        if (conveyorBuildMode) {
            if (activeWorkshop.placeInteriorConveyor(cell.x, cell.y, conveyorDirection)) {
                selectedInteriorCell = new Point(cell);
                interiorStatus = "Внутренний конвейер установлен · " + conveyorDirection.getDisplayName()
                        + " · режим строительства остаётся активным";
            } else {
                interiorStatus = getInteriorBuildFailureReason(cell);
            }
            repaint();
            return;
        }

        selectedInteriorCell = new Point(cell);
        InteriorConveyor conveyor = activeWorkshop.getInteriorConveyor(cell.x, cell.y);
        if (conveyor != null) {
            interiorStatus = "Внутренний конвейер выбран · " + conveyor.getDirection().getDisplayName()
                    + " · R повернуть · Delete удалить";
        } else if (activeWorkshop.isGatewayCell(cell.x, cell.y)) {
            interiorStatus = "Gateway внешнего порта · сектор " + getInteriorSectorName(cell);
        } else {
            interiorStatus = "Workshop · сектор " + getInteriorSectorName(cell)
                    + " · B — проложить внутренний конвейер";
        }
        repaint();
    }

    private Tile getWorldTileAt(Point point) {
        if (point == null) return null;
        int tileSize = 32;
        return worldMap.getTile(point.x / tileSize, point.y / tileSize);
    }

    private boolean canPlaceInteriorConveyor(Point cell) {
        return activeWorkshop != null && cell != null
                && !activeWorkshop.isGatewayCell(cell.x, cell.y)
                && activeWorkshop.getInteriorConveyor(cell.x, cell.y) == null;
    }

    private String getInteriorBuildFailureReason(Point cell) {
        if (activeWorkshop == null || cell == null) return "Внутренняя клетка не выбрана";
        if (activeWorkshop.isGatewayCell(cell.x, cell.y)) return "Эта клетка занята Gateway внешнего порта";
        if (activeWorkshop.getInteriorConveyor(cell.x, cell.y) != null) return "Внутренняя клетка уже занята конвейером";
        return "Здесь нельзя построить внутренний конвейер";
    }

    private Rectangle getInteriorBounds() {
        int availableWidth = Math.max(INTERIOR_CELLS * 22, getWidth() - INFO_WIDTH - PANEL_GAP - 36);
        int availableHeight = Math.max(INTERIOR_CELLS * 22, getHeight() - 118);
        int cellSize = Math.min(48, Math.min(availableWidth / INTERIOR_CELLS, availableHeight / INTERIOR_CELLS));
        cellSize = Math.max(22, cellSize);
        int size = cellSize * INTERIOR_CELLS;
        int totalWidth = size + PANEL_GAP + INFO_WIDTH;
        int x = Math.max(12, (getWidth() - totalWidth) / 2);
        int y = 56;
        return new Rectangle(x, y, size, size);
    }

    private Point getInteriorCellAt(Point point) {
        if (activeWorkshop == null || point == null) return null;
        Rectangle bounds = getInteriorBounds();
        if (!bounds.contains(point)) return null;
        int cellSize = bounds.width / INTERIOR_CELLS;
        int x = Math.max(0, Math.min(INTERIOR_CELLS - 1, (point.x - bounds.x) / cellSize));
        int y = Math.max(0, Math.min(INTERIOR_CELLS - 1, (point.y - bounds.y) / cellSize));
        return new Point(x, y);
    }

    private String getInteriorSectorName(Point cell) {
        if (cell == null) return "?";
        char column = (char) ('A' + cell.x / 3);
        int row = cell.y / 3 + 1;
        return column + Integer.toString(row);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (activeWorkshop == null || gameState.getStatus() != GameStatus.RUNNING) return;
        renderEnhancedWorkshopInterior(g);
    }

    private void renderEnhancedWorkshopInterior(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(18, 24, 32));
        g2.fillRect(0, 0, getWidth(), getHeight());

        Rectangle bounds = getInteriorBounds();
        int cellSize = bounds.width / INTERIOR_CELLS;

        g2.setColor(new Color(205, 214, 230));
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g2.drawString("Workshop interior · 9×9", bounds.x, 28);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g2.drawString("1 внешняя клетка = 3×3 внутренних. Gateway каждого порта занимает свои три клетки стены.",
                bounds.x, 46);
        g2.drawString("B — внутренний конвейер · R — повернуть · Delete — удалить · E/Esc — выйти",
                bounds.x, bounds.y + bounds.height + 24);

        for (int y = 0; y < INTERIOR_CELLS; y++) {
            for (int x = 0; x < INTERIOR_CELLS; x++) {
                int px = bounds.x + x * cellSize;
                int py = bounds.y + y * cellSize;
                Color fill = ((x / 3 + y / 3) % 2 == 0) ? new Color(44, 53, 66) : new Color(52, 62, 76);
                int sectorDamage = activeWorkshop.getSectorDamage(y / 3, x / 3);
                if (sectorDamage > 0) fill = new Color(Math.min(255, 44 + sectorDamage), 50, 58);
                g2.setColor(fill);
                g2.fillRect(px, py, cellSize, cellSize);
                g2.setColor(new Color(90, 105, 126));
                g2.drawRect(px, py, cellSize, cellSize);
            }
        }

        g2.setColor(new Color(145, 170, 205));
        g2.setStroke(new BasicStroke(3f));
        for (int i = 0; i <= 3; i++) {
            int offset = i * cellSize * 3;
            g2.drawLine(bounds.x + offset, bounds.y, bounds.x + offset, bounds.y + bounds.height);
            g2.drawLine(bounds.x, bounds.y + offset, bounds.x + bounds.width, bounds.y + offset);
        }

        for (FactoryPort port : activeWorkshop.getPorts()) drawGateway(g2, bounds, cellSize, port);
        for (InteriorConveyor conveyor : activeWorkshop.getInterior().getConveyors()) {
            drawConveyor(g2, bounds, cellSize, conveyor);
        }

        if (selectedInteriorCell != null) {
            drawCellOutline(g2, bounds, cellSize, selectedInteriorCell, new Color(105, 205, 255), 2f);
        }
        if (hoveredInteriorCell != null) {
            Color hover = conveyorBuildMode
                    ? (canPlaceInteriorConveyor(hoveredInteriorCell) ? new Color(100, 235, 125) : new Color(245, 95, 95))
                    : new Color(255, 235, 90);
            drawCellOutline(g2, bounds, cellSize, hoveredInteriorCell, hover, 3f);
            if (conveyorBuildMode && canPlaceInteriorConveyor(hoveredInteriorCell)) {
                drawArrow(g2,
                        bounds.x + hoveredInteriorCell.x * cellSize,
                        bounds.y + hoveredInteriorCell.y * cellSize,
                        cellSize, conveyorDirection, new Color(235, 245, 255, 190));
            }
        }

        renderInfoPanel(g2, bounds, cellSize);
        g2.dispose();
    }

    private void drawGateway(Graphics2D g2, Rectangle bounds, int cellSize, FactoryPort port) {
        if (port == null || port.getAttachedSide() == null) return;
        g2.setColor(port.isInput() ? new Color(85, 195, 225) : new Color(230, 175, 95));
        int strip = Math.max(3, cellSize / 3);
        for (Point cell : port.getGatewayCells()) {
            int px = bounds.x + cell.x * cellSize;
            int py = bounds.y + cell.y * cellSize;
            switch (port.getAttachedSide()) {
                case UP: g2.fillRect(px, py, cellSize, strip); break;
                case DOWN: g2.fillRect(px, py + cellSize - strip, cellSize, strip); break;
                case LEFT: g2.fillRect(px, py, strip, cellSize); break;
                case RIGHT: g2.fillRect(px + cellSize - strip, py, strip, cellSize); break;
            }
        }
    }

    private void drawConveyor(Graphics2D g2, Rectangle bounds, int cellSize, InteriorConveyor conveyor) {
        int px = bounds.x + conveyor.getX() * cellSize;
        int py = bounds.y + conveyor.getY() * cellSize;
        g2.setColor(new Color(82, 67, 43));
        g2.fillRoundRect(px + 3, py + 5, cellSize - 6, cellSize - 10, 5, 5);
        g2.setColor(new Color(190, 145, 45));
        g2.fillRoundRect(px + 5, py + 7, cellSize - 10, cellSize - 14, 4, 4);
        drawArrow(g2, px, py, cellSize, conveyor.getDirection(), new Color(38, 34, 28));
    }

    private void drawArrow(Graphics2D g2, int x, int y, int size, Direction direction, Color color) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        int length = Math.max(5, size / 4);
        int endX = cx + direction.getDx() * length;
        int endY = cy + direction.getDy() * length;
        int startX = cx - direction.getDx() * length;
        int startY = cy - direction.getDy() * length;
        int side = Math.max(3, size / 7);
        Stroke oldStroke = g2.getStroke();
        g2.setColor(color);
        g2.setStroke(new BasicStroke(Math.max(2f, size / 12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(startX, startY, endX, endY);
        if (direction == Direction.RIGHT || direction == Direction.LEFT) {
            int sign = direction == Direction.RIGHT ? 1 : -1;
            g2.drawLine(endX, endY, endX - sign * side, endY - side);
            g2.drawLine(endX, endY, endX - sign * side, endY + side);
        } else {
            int sign = direction == Direction.DOWN ? 1 : -1;
            g2.drawLine(endX, endY, endX - side, endY - sign * side);
            g2.drawLine(endX, endY, endX + side, endY - sign * side);
        }
        g2.setStroke(oldStroke);
    }

    private void drawCellOutline(Graphics2D g2, Rectangle bounds, int cellSize,
                                 Point cell, Color color, float stroke) {
        int px = bounds.x + cell.x * cellSize;
        int py = bounds.y + cell.y * cellSize;
        g2.setColor(color);
        g2.setStroke(new BasicStroke(stroke));
        g2.drawRect(px + 1, py + 1, cellSize - 2, cellSize - 2);
    }

    private void renderInfoPanel(Graphics2D g2, Rectangle bounds, int cellSize) {
        int x = bounds.x + bounds.width + PANEL_GAP;
        int y = bounds.y;
        int height = Math.min(bounds.height, 236);
        g2.setColor(new Color(24, 30, 38, 232));
        g2.fillRoundRect(x, y, INFO_WIDTH, height, 12, 12);
        g2.setColor(new Color(120, 160, 205));
        g2.drawRoundRect(x, y, INFO_WIDTH, height, 12, 12);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g2.drawString("Сводка", x + 10, y + 20);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g2.drawString("Входов: " + activeWorkshop.getInputPorts().size(), x + 10, y + 43);
        g2.drawString("Выходов: " + activeWorkshop.getOutputPorts().size(), x + 10, y + 61);
        g2.drawString("Конвейеров: " + activeWorkshop.getInterior().getConveyors().size(), x + 10, y + 79);
        g2.drawString("Gateway: 1 внеш. → 3 внутр.", x + 10, y + 101);
        g2.drawString("Сектора повреждений:", x + 10, y + 125);
        int lineY = y + 143;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                String text = "" + (char) ('A' + col) + (row + 1) + ": "
                        + activeWorkshop.getSectorDamage(row, col) + "%";
                g2.drawString(text, x + 10 + col * 60, lineY + row * 18);
            }
        }
        g2.setColor(new Color(188, 205, 224));
        g2.drawString("Предметы/throughput — следующий шаг", x + 10, y + height - 16);
    }

    @Override
    public String getStatusMessage() {
        return activeWorkshop != null ? interiorStatus : super.getStatusMessage();
    }

    @Override
    public String getSelectedObjectText() {
        if (activeWorkshop == null) return super.getSelectedObjectText();
        if (selectedInteriorCell != null) {
            InteriorConveyor conveyor = activeWorkshop.getInteriorConveyor(
                    selectedInteriorCell.x, selectedInteriorCell.y);
            if (conveyor != null) {
                return "Workshop interior · конвейер " + (selectedInteriorCell.x + 1) + ","
                        + (selectedInteriorCell.y + 1) + " · " + conveyor.getDirection().getDisplayName();
            }
            if (activeWorkshop.isGatewayCell(selectedInteriorCell.x, selectedInteriorCell.y)) {
                return "Workshop interior · Gateway · сектор " + getInteriorSectorName(selectedInteriorCell);
            }
        }
        return "Workshop interior 9×9 · входов " + activeWorkshop.getInputPorts().size()
                + " · выходов " + activeWorkshop.getOutputPorts().size()
                + " · конвейеров " + activeWorkshop.getInterior().getConveyors().size();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        if (activeWorkshop != null && (!activeWorkshop.isAlive()
                || !gameState.getAllBuildings().contains(activeWorkshop))) {
            // Штатный GamePanel тоже должен выйти из своего приватного interior-state.
            invoke(originalCancel, new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "destroyedWorkshop"));
            activeWorkshop = null;
            hoveredInteriorCell = null;
            selectedInteriorCell = null;
            conveyorBuildMode = false;
        }
        if (gameState.getStatus() != GameStatus.RUNNING) conveyorBuildMode = false;
    }
}
