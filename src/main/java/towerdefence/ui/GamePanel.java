package towerdefence.ui;

import towerdefence.building.BuildableType;
import towerdefence.building.Building;
import towerdefence.building.ConstructionSite;
import towerdefence.building.Conveyor;
import towerdefence.building.Drill;
import towerdefence.building.FactoryPort;
import towerdefence.building.House;
import towerdefence.building.Wall;
import towerdefence.building.Workshop;
import towerdefence.combat.CombatTower;
import towerdefence.combat.Enemy;
import towerdefence.combat.EnemySpawnPoint;
import towerdefence.combat.MachineGunTower;
import towerdefence.combat.SlowTower;
import towerdefence.combat.SniperTower;
import towerdefence.combat.TowerModuleType;
import towerdefence.game.GameState;
import towerdefence.game.GameStatus;
import towerdefence.resource.Inventory;
import towerdefence.resource.Resource;
import towerdefence.resource.ResourceType;
import towerdefence.unit.Unit;
import towerdefence.unit.Worker;
import towerdefence.world.Direction;
import towerdefence.world.GameMap;
import towerdefence.world.Tile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.EnumMap;
import java.util.Map;

public class GamePanel extends JPanel implements ActionListener, MouseListener, MouseMotionListener {
    private final GameMap map;
    private final GameState state;
    private final int tileSize = 32;

    private Unit selectedUnit;
    private Building selectedBuilding;
    private BuildMenuDialog buildMenu;
    private BuildableType selectedBuildType;
    private Direction buildDirection = Direction.RIGHT;
    private Tile hoveredTile;
    private String statusMessage = "B — открыть строительство";
    private Rectangle restartButtonBounds;
    private Rectangle sellTowerButtonBounds;
    private final EnumMap<TowerModuleType, Rectangle> moduleButtonBounds = new EnumMap<>(TowerModuleType.class);
    private GameStatus previousStatus = GameStatus.RUNNING;
    private Workshop activeWorkshopInterior;
    private Point hoveredInteriorCell;

    public GamePanel(GameMap map, GameState state) {
        this.map = map;
        this.state = state;

        setPreferredSize(new Dimension(map.getWidth() * tileSize, map.getHeight() * tileSize));
        setBackground(Color.BLACK);
        setFocusable(true);
        addMouseListener(this);
        addMouseMotionListener(this);
        installKeyBindings();

        new Timer(1000 / 60, this).start();
    }

    private void installKeyBindings() {
        InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, 0), "toggleBuildMenu");
        actionMap.put("toggleBuildMenu", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { toggleBuildMenu(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "rotateBuilding");
        actionMap.put("rotateBuilding", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { rotateSelectedBuilding(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelBuilding");
        actionMap.put("cancelBuilding", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (activeWorkshopInterior != null) exitWorkshopInterior();
                else cancelBuildingMode();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteBuilding");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "deleteBuilding");
        actionMap.put("deleteBuilding", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { deleteSelectedBuilding(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, 0), "toggleWorkshopView");
        actionMap.put("toggleWorkshopView", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { toggleWorkshopInterior(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, 0), "spawnTestEnemy");
        actionMap.put("spawnTestEnemy", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (state.getStatus() != GameStatus.RUNNING) return;
                if (state.spawnTestEnemy()) {
                    statusMessage = "Создан дополнительный тестовый противник";
                } else {
                    statusMessage = "Точка спавна занята — повторите через секунду";
                }
                repaint();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "restartGame");
        actionMap.put("restartGame", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (state.getStatus() != GameStatus.RUNNING) restartGame();
            }
        });
    }

    private void toggleBuildMenu() {
        if (state.getStatus() != GameStatus.RUNNING) {
            statusMessage = "Сначала начните новую игру";
            return;
        }
        if (buildMenu == null) {
            Window owner = SwingUtilities.getWindowAncestor(this);
            buildMenu = new BuildMenuDialog(owner, this::selectBuildingType, this::cancelBuildingMode);
            updateBuildMenuResources();
        }
        if (buildMenu.isVisible()) buildMenu.setVisible(false);
        else {
            updateBuildMenuResources();
            buildMenu.showNear(this);
        }
    }

    private void selectBuildingType(BuildableType type) {
        if (state.getStatus() != GameStatus.RUNNING) return;
        selectedBuildType = type;
        selectedBuilding = null;
        selectedUnit = null;
        statusMessage = "Строительство: " + type.getDisplayName() + " · " + type.getCostText();
        if (type == BuildableType.CONVEYOR) {
            statusMessage += " · направление: " + buildDirection.getDisplayName();
        }
        if (buildMenu != null) {
            boolean affordable = state.canAfford(type);
            String feedback = affordable
                    ? "Выбрано: " + type.getDisplayName() + " · выберите клетку на карте"
                    : "Не хватает ресурсов: " + type.getCostText();
            buildMenu.setFeedback(feedback, !affordable);
            updateBuildMenuResources();
        }
        requestFocusInWindow();
        repaint();
    }

    private void rotateSelectedBuilding() {
        if (selectedBuildType != BuildableType.CONVEYOR || state.getStatus() != GameStatus.RUNNING) return;
        buildDirection = buildDirection.rotateClockwise();
        statusMessage = "Конвейер · направление: " + buildDirection.getDisplayName();
        if (buildMenu != null) buildMenu.setFeedback(statusMessage, false);
        repaint();
    }

    private void cancelBuildingMode() {
        if (selectedBuildType == null) return;
        selectedBuildType = null;
        hoveredTile = null;
        statusMessage = "Строительство отменено · B — открыть меню";
        if (buildMenu != null) {
            buildMenu.clearSelection();
            buildMenu.setFeedback("Режим строительства отменён", false);
        }
        repaint();
    }

    private void deleteSelectedBuilding() {
        if (state.getStatus() != GameStatus.RUNNING) return;
        if (selectedBuildType != null) {
            statusMessage = "Сначала отмените режим строительства клавишей Esc";
            repaint();
            return;
        }
        if (selectedBuilding == null) {
            statusMessage = "Для удаления сначала выберите постройку кликом";
            repaint();
            return;
        }
        if (selectedBuilding == state.getMainBuilding()) {
            statusMessage = "Главное здание удалить нельзя";
            repaint();
            return;
        }

        String name = getBuildingName(selectedBuilding);
        if (state.removeBuilding(selectedBuilding)) {
            selectedBuilding = null;
            statusMessage = name + " удалён(а)";
        } else {
            statusMessage = "Не удалось удалить постройку";
        }
        repaint();
    }

    private void restartGame() {
        if (buildMenu != null) buildMenu.setVisible(false);
        selectedBuildType = null;
        selectedBuilding = null;
        selectedUnit = null;
        hoveredTile = null;
        activeWorkshopInterior = null;
        hoveredInteriorCell = null;
        state.restart();
        previousStatus = GameStatus.RUNNING;
        restartButtonBounds = null;
        statusMessage = "Новая игра · B — открыть строительство";
        if (buildMenu != null) {
            buildMenu.clearSelection();
            updateBuildMenuResources();
        }
        repaint();
    }

    private void toggleWorkshopInterior() {
        if (activeWorkshopInterior != null) {
            exitWorkshopInterior();
            return;
        }
        if (selectedBuilding instanceof Workshop) {
            activeWorkshopInterior = (Workshop) selectedBuilding;
            hoveredInteriorCell = null;
            selectedBuildType = null;
            statusMessage = "Внутри Workshop · Esc или E — выйти";
            if (buildMenu != null) buildMenu.setVisible(false);
            repaint();
            return;
        }
        statusMessage = "Чтобы войти, сначала выберите Workshop";
        repaint();
    }

    private void exitWorkshopInterior() {
        activeWorkshopInterior = null;
        hoveredInteriorCell = null;
        statusMessage = "Выход из Workshop";
        repaint();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        requestFocusInWindow();

        if (activeWorkshopInterior != null) {
            handleInteriorClick(e);
            return;
        }

        if (state.getStatus() != GameStatus.RUNNING) {
            if (restartButtonBounds != null && restartButtonBounds.contains(e.getPoint())) restartGame();
            return;
        }

        if (SwingUtilities.isLeftMouseButton(e)
                && selectedBuildType == null
                && handleTowerActionClick(e.getPoint())) {
            repaint();
            return;
        }

        int tileX = e.getX() / tileSize;
        int tileY = e.getY() / tileSize;
        Tile targetTile = map.getTile(tileX, tileY);
        if (targetTile == null) return;

        if (SwingUtilities.isRightMouseButton(e)) {
            if (selectedBuildType != null) cancelBuildingMode();
            else {
                selectedBuilding = null;
                selectedUnit = null;
                statusMessage = "Выбор снят";
                repaint();
            }
            return;
        }
        if (!SwingUtilities.isLeftMouseButton(e)) return;

        if (selectedBuildType != null) {
            if (state.placeBuilding(selectedBuildType, targetTile, buildDirection)) {
                statusMessage = "Чертёж: " + selectedBuildType.getDisplayName()
                        + " добавлен в очередь · зарезервировано: " + selectedBuildType.getCostText()
                        + " · рабочий построит его по порядку";
                if (buildMenu != null) buildMenu.setFeedback(statusMessage, false);
            } else {
                statusMessage = state.getBuildFailureReason(selectedBuildType, targetTile);
                if (buildMenu != null) buildMenu.setFeedback(statusMessage, true);
            }
            updateBuildMenuResources();
            repaint();
            return;
        }

        if (targetTile.hasBuilding()) {
            selectedBuilding = targetTile.getBuilding();
            selectedUnit = null;
            statusMessage = describeSelectedBuilding(selectedBuilding);
            if (e.getClickCount() >= 2 && selectedBuilding instanceof Workshop) {
                toggleWorkshopInterior();
                return;
            }
            repaint();
            return;
        }

        if (targetTile.hasUnit()) {
            selectedUnit = targetTile.getUnit();
            selectedBuilding = null;
            statusMessage = describeSelectedUnit(selectedUnit);
            repaint();
            return;
        }

        if (selectedUnit instanceof Worker && targetTile.hasResource()) {
            Worker worker = (Worker) selectedUnit;
            if (worker.assignPreferredDeposit(targetTile)) {
                statusMessage = "Рабочему назначено месторождение "
                        + targetTile.getResource().getType().getDisplayName()
                        + " · запас " + targetTile.getResource().getAmount();
            } else {
                statusMessage = "Это месторождение пока нельзя добывать рабочим";
            }
        } else if (selectedUnit != null && targetTile.isPassable() && !targetTile.hasBuilding()) {
            if (selectedUnit.setTarget(targetTile)) {
                statusMessage = "Юнит временно отправлен в клетку " + tileX + ", " + tileY;
            } else {
                statusMessage = "Юнит не может добраться до выбранной клетки";
            }
        } else {
            selectedBuilding = null;
        }
        repaint();
    }

    @Override public void mouseMoved(MouseEvent e) {
        if (activeWorkshopInterior != null) {
            hoveredInteriorCell = getInteriorCellAt(e.getPoint());
            if (hoveredInteriorCell != null) {
                statusMessage = "Внутри Workshop · ячейка " + (hoveredInteriorCell.x + 1) + "," + (hoveredInteriorCell.y + 1)
                        + " · сектор " + getInteriorSectorName(hoveredInteriorCell);
            }
            repaint();
            return;
        }
        hoveredTile = map.getTile(e.getX() / tileSize, e.getY() / tileSize);
        if (selectedBuildType != null && hoveredTile != null) {
            boolean valid = state.canPlaceBuilding(selectedBuildType, hoveredTile)
                    && state.canAfford(selectedBuildType);
            String message = valid
                    ? "Можно построить: " + selectedBuildType.getDisplayName()
                    : state.getBuildFailureReason(selectedBuildType, hoveredTile);
            statusMessage = message;
            if (buildMenu != null) buildMenu.setFeedback(message, !valid);
        }
        repaint();
    }
    @Override public void mouseDragged(MouseEvent e) { mouseMoved(e); }
    @Override public void mousePressed(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) { requestFocusInWindow(); }
    @Override public void mouseExited(MouseEvent e) { hoveredTile = null; hoveredInteriorCell = null; repaint(); }

    private void handleInteriorClick(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e)) {
            exitWorkshopInterior();
            return;
        }
        Point cell = getInteriorCellAt(e.getPoint());
        if (cell == null) return;
        hoveredInteriorCell = cell;
        statusMessage = "Workshop · сектор " + getInteriorSectorName(cell)
                + " · тут позже будут ставиться внутренние модули и конвейеры";
        repaint();
    }

    private Rectangle getInteriorBounds() {
        int size = Math.min(Math.min(getWidth() - 80, getHeight() - 110), 9 * 48);
        size = Math.max(9 * 28, size);
        int x = (getWidth() - size) / 2;
        int y = 56;
        return new Rectangle(x, y, size, size);
    }

    private Point getInteriorCellAt(Point point) {
        if (activeWorkshopInterior == null || point == null) return null;
        Rectangle bounds = getInteriorBounds();
        if (!bounds.contains(point)) return null;
        int cellSize = bounds.width / activeWorkshopInterior.getInteriorWidth();
        int x = Math.max(0, Math.min(activeWorkshopInterior.getInteriorWidth() - 1, (point.x - bounds.x) / cellSize));
        int y = Math.max(0, Math.min(activeWorkshopInterior.getInteriorHeight() - 1, (point.y - bounds.y) / cellSize));
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
        if (activeWorkshopInterior != null) {
            renderWorkshopInterior(g);
            renderEndOverlay(g);
            return;
        }
        renderMap(g);
        renderResources(g);
        renderSpawnPoint(g);
        renderSelectedTowerRange(g);
        renderBuildings(g);
        renderUnits(g);
        renderSelectedUnit(g);
        renderEnemies(g);
        renderCombatEffects(g);
        renderSelectedBuilding(g);
        renderBuildPreview(g);
        renderTowerActionPanel(g);
        renderEndOverlay(g);
    }

    private void renderWorkshopInterior(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(18, 24, 32));
        g2.fillRect(0, 0, getWidth(), getHeight());

        Rectangle bounds = getInteriorBounds();
        int cells = activeWorkshopInterior.getInteriorWidth();
        int cellSize = bounds.width / cells;

        g2.setColor(new Color(205, 214, 230));
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g2.drawString("Workshop interior · 9×9", bounds.x, 28);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g2.drawString("Внешний цех 3×3 масштабируется до внутреннего пространства 9×9. Мир снаружи продолжает работать.", bounds.x, 46);
        g2.drawString("E / Esc — выйти · ЛКМ — выбрать ячейку · ПКМ — быстрый выход", bounds.x, bounds.y + bounds.height + 24);

        for (int y = 0; y < cells; y++) {
            for (int x = 0; x < cells; x++) {
                int px = bounds.x + x * cellSize;
                int py = bounds.y + y * cellSize;
                Color fill = ((x / 3 + y / 3) % 2 == 0) ? new Color(44, 53, 66) : new Color(52, 62, 76);
                int sectorDamage = activeWorkshopInterior.getSectorDamage(y / 3, x / 3);
                if (sectorDamage > 0) {
                    fill = new Color(Math.min(255, 44 + sectorDamage), 50, 58);
                }
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

        for (FactoryPort port : activeWorkshopInterior.getPorts()) {
            drawInteriorGateway(g2, bounds, cellSize, port);
        }

        if (hoveredInteriorCell != null) {
            int px = bounds.x + hoveredInteriorCell.x * cellSize;
            int py = bounds.y + hoveredInteriorCell.y * cellSize;
            g2.setColor(new Color(255, 235, 90));
            g2.setStroke(new BasicStroke(3f));
            g2.drawRect(px + 1, py + 1, cellSize - 2, cellSize - 2);
            g2.setColor(Color.WHITE);
            g2.drawString(getInteriorSectorName(hoveredInteriorCell), px + 4, py + 16);
        }

        // Подсказка по портам.
        int infoY = bounds.y;
        int infoX = bounds.x + bounds.width + 20;
        g2.setColor(new Color(24, 30, 38, 225));
        g2.fillRoundRect(infoX, infoY, 220, 180, 12, 12);
        g2.setColor(new Color(120, 160, 205));
        g2.drawRoundRect(infoX, infoY, 220, 180, 12, 12);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g2.drawString("Сводка", infoX + 10, infoY + 20);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g2.drawString("Входов: " + activeWorkshopInterior.getInputPorts().size(), infoX + 10, infoY + 44);
        g2.drawString("Выходов: " + activeWorkshopInterior.getOutputPorts().size(), infoX + 10, infoY + 62);
        g2.drawString("Секторов с уроном:", infoX + 10, infoY + 86);
        int lineY = infoY + 104;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                String txt = "" + (char)('A' + col) + (row + 1) + ": " + activeWorkshopInterior.getSectorDamage(row, col) + "%";
                g2.drawString(txt, infoX + 10 + (col * 66), lineY + row * 18);
            }
        }
        g2.drawString("Вася позже сможет размечать тут", infoX + 10, infoY + 164);
        g2.dispose();
    }

    private void drawInteriorGateway(Graphics2D g2, Rectangle bounds, int cellSize, FactoryPort port) {
        if (port == null || port.getAttachedSide() == null) return;
        g2.setColor(port.isInput() ? new Color(85, 195, 225) : new Color(230, 175, 95));
        switch (port.getAttachedSide()) {
            case UP:
                for (int i = 0; i < 3; i++) g2.fillRect(bounds.x + i * cellSize, bounds.y, cellSize, cellSize / 3);
                break;
            case DOWN:
                for (int i = 0; i < 3; i++) g2.fillRect(bounds.x + i * cellSize, bounds.y + bounds.height - cellSize / 3, cellSize, cellSize / 3);
                break;
            case LEFT:
                for (int i = 0; i < 3; i++) g2.fillRect(bounds.x, bounds.y + i * cellSize, cellSize / 3, cellSize);
                break;
            case RIGHT:
                for (int i = 0; i < 3; i++) g2.fillRect(bounds.x + bounds.width - cellSize / 3, bounds.y + i * cellSize, cellSize / 3, cellSize);
                break;
        }
    }

    private void renderMap(Graphics g) {
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                g.setColor(getTileColor(tile));
                g.fillRect(x * tileSize, y * tileSize, tileSize, tileSize);
                g.setColor(new Color(0, 0, 0, 28));
                g.drawRect(x * tileSize, y * tileSize, tileSize, tileSize);
            }
        }
    }

    private void renderResources(Graphics g) {
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                if (!tile.hasResource()) continue;
                int px = x * tileSize;
                int py = y * tileSize;
                Resource resource = tile.getResource();
                g.setColor(resource.getType().getColor());
                g.fillOval(px + 6, py + 6, tileSize - 12, tileSize - 12);
                g.setColor(new Color(235, 238, 240));
                g.drawOval(px + 6, py + 6, tileSize - 12, tileSize - 12);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
                String amount = Integer.toString(resource.getAmount());
                FontMetrics fm = g.getFontMetrics();
                g.drawString(amount, px + (tileSize - fm.stringWidth(amount)) / 2, py + tileSize - 3);
            }
        }
    }

    private void renderSpawnPoint(Graphics g) {
        EnemySpawnPoint spawnPoint = state.getEnemySpawnPoint();
        if (spawnPoint != null) spawnPoint.render(g, tileSize);
    }

    private Color getTileColor(Tile tile) {
        if (tile == null) return Color.BLACK;
        switch (tile.getType()) {
            case DIRT: return new Color(125, 78, 43);
            case GRASS: return new Color(72, 135, 62);
            case ROCK: return Color.GRAY;
            case WATER: return new Color(42, 105, 180);
            case SAND: return new Color(208, 184, 130);
            default: return Color.BLACK;
        }
    }

    private void renderUnits(Graphics g) {
        for (Unit unit : state.getAllUnits()) unit.render(g, tileSize);
    }

    private void renderSelectedUnit(Graphics g) {
        if (selectedUnit == null || !selectedUnit.isAlive() || selectedUnit.getPosition() == null) return;
        Tile tile = selectedUnit.getPosition();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(255, 235, 80));
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(tile.getX() * tileSize + 1, tile.getY() * tileSize + 1,
                tileSize - 3, tileSize - 3);
        g2.dispose();
    }

    private void renderEnemies(Graphics g) {
        for (Enemy enemy : state.getAllEnemies()) enemy.render(g, tileSize);
    }

    private void renderBuildings(Graphics g) {
        for (Building building : state.getAllBuildings()) building.render(g, tileSize);
    }

    private void renderCombatEffects(Graphics g) {
        for (Building building : state.getAllBuildings()) {
            if (building instanceof CombatTower) {
                ((CombatTower) building).renderShotEffect(g, tileSize);
            }
        }
    }

    private void renderSelectedTowerRange(Graphics g) {
        if (!(selectedBuilding instanceof CombatTower)
                || !selectedBuilding.isAlive()
                || selectedBuilding.getPosition() == null) return;

        CombatTower tower = (CombatTower) selectedBuilding;
        Tile tile = tower.getPosition();
        int centerX = tile.getX() * tileSize + tileSize / 2;
        int centerY = tile.getY() * tileSize + tileSize / 2;
        int radius = (int) Math.round(tower.getAttackRange() * tileSize);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(85, 185, 255, 34));
        g2.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
        g2.setColor(new Color(100, 205, 255, 180));
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
        g2.dispose();
    }

    private void renderSelectedBuilding(Graphics g) {
        if (selectedBuilding == null || !selectedBuilding.isAlive() || selectedBuilding.getPosition() == null) return;

        Tile tile = selectedBuilding.getPosition();
        int x = tile.getX() * tileSize;
        int y = tile.getY() * tileSize;
        int widthPx = selectedBuilding.getFootprintWidth() * tileSize;
        int heightPx = selectedBuilding.getFootprintHeight() * tileSize;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(255, 235, 80));
        g2.setStroke(new BasicStroke(3f));
        g2.drawRect(x + 2, y + 2, widthPx - 5, heightPx - 5);

        if (selectedBuilding instanceof CombatTower) {
            Enemy target = ((CombatTower) selectedBuilding).getCurrentTarget();
            if (target != null && target.isAlive()) {
                int tx = Math.round(target.getRealX() * tileSize);
                int ty = Math.round(target.getRealY() * tileSize);
                g2.setColor(new Color(255, 238, 105));
                g2.drawOval(tx + 1, ty + 1, tileSize - 2, tileSize - 2);
            }
        }
        g2.dispose();
    }

    private void renderBuildPreview(Graphics g) {
        if (selectedBuildType == null || hoveredTile == null || state.getStatus() != GameStatus.RUNNING) return;

        int x = hoveredTile.getX() * tileSize;
        int y = hoveredTile.getY() * tileSize;
        int previewWidth = selectedBuildType.getFootprintWidth() * tileSize;
        int previewHeight = selectedBuildType.getFootprintHeight() * tileSize;
        boolean valid = state.canPlaceBuilding(selectedBuildType, hoveredTile) && state.canAfford(selectedBuildType);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.62f));
        g2.setColor(valid ? new Color(75, 220, 105) : new Color(230, 70, 70));
        g2.fillRect(x + 1, y + 1, previewWidth - 2, previewHeight - 2);
        g2.setComposite(AlphaComposite.SrcOver);
        g2.setColor(selectedBuildType.getPreviewColor());

        if (selectedBuildType == BuildableType.MACHINE_GUN_TOWER) {
            g2.fillOval(x + 7, y + 7, tileSize - 14, tileSize - 14);
            g2.fillRect(x + tileSize / 2 - 2, y + 2, 4, tileSize / 2);
        } else if (selectedBuildType == BuildableType.SNIPER_TOWER) {
            g2.fillRect(x + 8, y + 8, tileSize - 16, tileSize - 16);
            g2.setStroke(new BasicStroke(3f));
            g2.drawLine(x + tileSize / 2, y + tileSize / 2, x + tileSize - 2, y + 2);
        } else if (selectedBuildType == BuildableType.SLOW_TOWER) {
            g2.fillOval(x + 6, y + 6, tileSize - 12, tileSize - 12);
            g2.setColor(new Color(170, 245, 250));
            g2.drawOval(x + 10, y + 10, tileSize - 20, tileSize - 20);
        } else if (selectedBuildType == BuildableType.WALL) {
            g2.fillRect(x + 4, y + 5, tileSize - 8, tileSize - 10);
            g2.setColor(new Color(70, 74, 80));
            g2.drawLine(x + 4, y + tileSize / 2, x + tileSize - 4, y + tileSize / 2);
        } else if (selectedBuildType == BuildableType.DRILL) {
            g2.fillOval(x + 6, y + 6, tileSize - 12, tileSize - 12);
            g2.setColor(new Color(35, 50, 52));
            g2.fillOval(x + tileSize / 2 - 4, y + tileSize / 2 - 4, 8, 8);
            g2.drawLine(x + tileSize / 2, y + 3, x + tileSize / 2, y + tileSize - 3);
            g2.drawLine(x + 3, y + tileSize / 2, x + tileSize - 3, y + tileSize / 2);
        } else if (selectedBuildType == BuildableType.WORKSHOP) {
            g2.fillRoundRect(x + 4, y + 4, previewWidth - 8, previewHeight - 8, 10, 10);
            g2.setColor(new Color(25, 30, 42));
            g2.drawLine(x + tileSize, y + 4, x + tileSize, y + previewHeight - 4);
            g2.drawLine(x + tileSize * 2, y + 4, x + tileSize * 2, y + previewHeight - 4);
            g2.drawLine(x + 4, y + tileSize, x + previewWidth - 4, y + tileSize);
            g2.drawLine(x + 4, y + tileSize * 2, x + previewWidth - 4, y + tileSize * 2);
        } else if (selectedBuildType == BuildableType.FACTORY_INPUT_PORT || selectedBuildType == BuildableType.FACTORY_OUTPUT_PORT) {
            g2.fillRoundRect(x + 6, y + 6, tileSize - 12, tileSize - 12, 8, 8);
            g2.setColor(Color.WHITE);
            g2.drawString(selectedBuildType == BuildableType.FACTORY_INPUT_PORT ? "IN" : "OUT", x + 5, y + tileSize - 6);
        } else {
            drawPreviewArrow(g2, x, y);
        }
        g2.dispose();
    }

    private void drawPreviewArrow(Graphics2D g2, int x, int y) {
        int cx = x + tileSize / 2;
        int cy = y + tileSize / 2;
        int length = tileSize / 3;
        int endX = cx + buildDirection.getDx() * length;
        int endY = cy + buildDirection.getDy() * length;
        int startX = cx - buildDirection.getDx() * length;
        int startY = cy - buildDirection.getDy() * length;
        g2.setStroke(new BasicStroke(4f));
        g2.drawLine(startX, startY, endX, endY);
    }

    private void renderTowerActionPanel(Graphics g) {
        moduleButtonBounds.clear();
        sellTowerButtonBounds = null;
        if (!(selectedBuilding instanceof CombatTower)
                || !selectedBuilding.isAlive()
                || state.getStatus() != GameStatus.RUNNING) return;

        CombatTower tower = (CombatTower) selectedBuilding;
        int panelX = 8;
        int panelWidth = Math.min(getWidth() - 16, 624);
        int panelHeight = 132;
        int panelY = getHeight() - panelHeight - 8;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(12, 17, 24, 238));
        g2.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 12, 12);
        g2.setColor(new Color(120, 165, 215));
        g2.drawRoundRect(panelX, panelY, panelWidth, panelHeight, 12, 12);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        String thermalState = tower.isOverheated() ? "ПЕРЕГРЕВ — стрельба остановлена" : "система в норме";
        g2.drawString("Модули: " + getBuildingName(tower)
                + " · " + String.format(java.util.Locale.US, "%.1f", tower.getShotsPerSecond()) + " выстр./с"
                + " · нагрев " + Math.round(tower.getHeat()) + "/" + Math.round(tower.getMaxHeat())
                + " · " + thermalState, panelX + 10, panelY + 20);

        int buttonGap = 6;
        int buttonY = panelY + 30;
        int buttonHeight = 70;
        int available = panelWidth - 20 - buttonGap * 3;
        int buttonWidth = available / 4;
        int buttonX = panelX + 10;

        for (TowerModuleType module : TowerModuleType.values()) {
            Rectangle bounds = new Rectangle(buttonX, buttonY, buttonWidth, buttonHeight);
            moduleButtonBounds.put(module, bounds);
            drawModuleButton(g2, tower, module, bounds);
            buttonX += buttonWidth + buttonGap;
        }

        sellTowerButtonBounds = new Rectangle(buttonX, buttonY, buttonWidth, buttonHeight);
        drawSaleButton(g2, tower, sellTowerButtonBounds);

        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g2.setColor(new Color(195, 205, 218));
        g2.drawString("Стабилизаторы повышают темп огня, но создают тепло; Delete сносит без возврата.",
                panelX + 10, panelY + 121);
        g2.dispose();
    }

    private void drawModuleButton(Graphics2D g2, CombatTower tower,
                                  TowerModuleType module, Rectangle bounds) {
        boolean installed = tower.getInstalledModules().contains(module);
        boolean structurallyAvailable = tower.canInstallModule(module);
        boolean affordable = state.canAfford(module);

        Color background;
        if (installed) background = new Color(48, 112, 76);
        else if (structurallyAvailable && affordable) background = new Color(48, 82, 122);
        else background = new Color(55, 58, 65);

        g2.setColor(background);
        g2.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g2.setColor(installed ? new Color(120, 235, 155) : new Color(145, 170, 205));
        g2.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        g2.drawString(module.getDisplayName(), bounds.x + 7, bounds.y + 16);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        String stateText;
        if (installed) stateText = "УСТАНОВЛЕН";
        else if (!structurallyAvailable) stateText = tower.getModuleFailureReason(module);
        else if (!affordable) stateText = "Не хватает ресурсов";
        else stateText = "Установить";
        drawClippedString(g2, stateText, bounds.x + 7, bounds.y + 35, bounds.width - 14);
        drawClippedString(g2, module.getCostText(), bounds.x + 7, bounds.y + 51, bounds.width - 14);
        String effect = module == TowerModuleType.COOLING_I
                ? "+охлаждение, +предел тепла"
                : "ускорение стрельбы";
        drawClippedString(g2, effect, bounds.x + 7, bounds.y + 66, bounds.width - 14);
    }

    private void drawSaleButton(Graphics2D g2, CombatTower tower, Rectangle bounds) {
        Map<ResourceType, Integer> refund = state.getTowerSaleRefund(tower);
        g2.setColor(new Color(120, 67, 48));
        g2.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g2.setColor(new Color(235, 145, 95));
        g2.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        g2.drawString("Продать башню", bounds.x + 7, bounds.y + 16);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        drawClippedString(g2, "Возврат: " + formatResources(refund),
                bounds.x + 7, bounds.y + 36, bounds.width - 14);
        drawClippedString(g2, "60% башни + 50% модулей",
                bounds.x + 7, bounds.y + 53, bounds.width - 14);
        drawClippedString(g2, "Боеприпасы не возвращаются",
                bounds.x + 7, bounds.y + 68, bounds.width - 14);
    }

    private void drawClippedString(Graphics2D g2, String text, int x, int y, int maxWidth) {
        if (text == null) return;
        String value = text;
        FontMetrics fm = g2.getFontMetrics();
        while (value.length() > 3 && fm.stringWidth(value) > maxWidth) {
            value = value.substring(0, value.length() - 2);
        }
        if (!value.equals(text)) value = value + "…";
        g2.drawString(value, x, y);
    }

    private boolean handleTowerActionClick(Point point) {
        if (!(selectedBuilding instanceof CombatTower) || point == null) return false;
        CombatTower tower = (CombatTower) selectedBuilding;

        if (sellTowerButtonBounds != null && sellTowerButtonBounds.contains(point)) {
            Map<ResourceType, Integer> refund = state.sellTower(tower);
            if (refund.isEmpty()) {
                statusMessage = "Продать башню не удалось";
            } else {
                statusMessage = getBuildingName(tower) + " продана · возвращено: " + formatResources(refund);
                selectedBuilding = null;
            }
            updateBuildMenuResources();
            return true;
        }

        for (Map.Entry<TowerModuleType, Rectangle> entry : moduleButtonBounds.entrySet()) {
            if (!entry.getValue().contains(point)) continue;
            TowerModuleType module = entry.getKey();
            if (state.installTowerModule(tower, module)) {
                statusMessage = module.getDisplayName() + " установлен · темп огня: "
                        + String.format(java.util.Locale.US, "%.1f", tower.getShotsPerSecond()) + " выстр./с";
            } else {
                statusMessage = state.getModuleFailureReason(tower, module);
            }
            updateBuildMenuResources();
            return true;
        }
        return false;
    }

    private String formatResources(Map<ResourceType, Integer> resources) {
        if (resources == null || resources.isEmpty()) return "ничего";
        StringBuilder result = new StringBuilder();
        for (Map.Entry<ResourceType, Integer> entry : resources.entrySet()) {
            if (entry.getValue() <= 0) continue;
            if (result.length() > 0) result.append(" · ");
            result.append(entry.getKey().getDisplayName()).append(" ").append(entry.getValue());
        }
        return result.length() == 0 ? "ничего" : result.toString();
    }

    private void renderEndOverlay(Graphics g) {
        if (state.getStatus() == GameStatus.RUNNING) {
            restartButtonBounds = null;
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(5, 8, 12, 195));
        g2.fillRect(0, 0, getWidth(), getHeight());

        String title = state.getStatus() == GameStatus.VICTORY ? "ПОБЕДА" : "БАЗА УНИЧТОЖЕНА";
        String subtitle = state.getStatus() == GameStatus.VICTORY
                ? "Все пять волн отражены"
                : "Постройте стены и защитите главное здание";

        g2.setColor(state.getStatus() == GameStatus.VICTORY
                ? new Color(105, 235, 135) : new Color(245, 95, 95));
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        FontMetrics titleMetrics = g2.getFontMetrics();
        g2.drawString(title, (getWidth() - titleMetrics.stringWidth(title)) / 2, getHeight() / 2 - 55);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        FontMetrics subMetrics = g2.getFontMetrics();
        g2.drawString(subtitle, (getWidth() - subMetrics.stringWidth(subtitle)) / 2, getHeight() / 2 - 20);

        int buttonWidth = 210;
        int buttonHeight = 46;
        restartButtonBounds = new Rectangle(
                (getWidth() - buttonWidth) / 2,
                getHeight() / 2 + 15,
                buttonWidth,
                buttonHeight
        );
        g2.setColor(new Color(66, 112, 170));
        g2.fillRoundRect(restartButtonBounds.x, restartButtonBounds.y,
                restartButtonBounds.width, restartButtonBounds.height, 12, 12);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        String buttonText = "Начать заново (Enter)";
        FontMetrics buttonMetrics = g2.getFontMetrics();
        g2.drawString(buttonText,
                restartButtonBounds.x + (buttonWidth - buttonMetrics.stringWidth(buttonText)) / 2,
                restartButtonBounds.y + 29);
        g2.dispose();
    }

    private String describeSelectedBuilding(Building building) {
        if (building instanceof ConstructionSite) {
            ConstructionSite site = (ConstructionSite) building;
            String phase = site.hasAllMaterials()
                    ? "сборка " + Math.round(site.getProgressFraction() * 100) + "%"
                    : "доставка материалов · осталось " + site.getTotalRemainingMaterials();
            return "Чертёж: " + site.getTargetType().getDisplayName() + " · " + phase
                    + " · очередь №" + (state.getConstructionQueue().indexOf(site) + 1);
        }
        if (building instanceof CombatTower) {
            CombatTower tower = (CombatTower) building;
            return getBuildingName(building) + " выбрана · патроны " + tower.getAmmo()
                    + "/" + tower.getMaxAmmo() + " · скорость "
                    + String.format(java.util.Locale.US, "%.1f", tower.getShotsPerSecond())
                    + " выстр./с · управление в нижней панели";
        }
        if (building instanceof Drill) {
            Drill drill = (Drill) building;
            return "Бур выбран · буфер " + drill.getBuffer() + "/" + drill.getBufferCapacity()
                    + " · месторождение " + drill.getRemainingDeposit();
        }
        if (building == state.getMainBuilding()) {
            Inventory storage = state.getMainBuilding().getInventory();
            return "Главное здание · металл " + storage.getAmount(ResourceType.METAL)
                    + " · лом " + storage.getAmount(ResourceType.SCRAP);
        }
        if (building instanceof Workshop) {
            Workshop workshop = (Workshop) building;
            return "Workshop выбран · размер 3×3 → 9×9 · входов " + workshop.getInputPorts().size()
                    + " · выходов " + workshop.getOutputPorts().size() + " · E — войти";
        }
        if (building instanceof FactoryPort) {
            FactoryPort port = (FactoryPort) building;
            return (port.isInput() ? "Вход фабрики" : "Выход фабрики") + " · связан с цехом: "
                    + (port.getWorkshop() != null ? "да" : "нет");
        }
        return getBuildingName(building) + " выбран(а) · здоровье " + building.getHealth()
                + " · Delete/Backspace — удалить";
    }

    private String describeSelectedUnit(Unit unit) {
        if (unit instanceof Worker) {
            Worker worker = (Worker) unit;
            Inventory bag = worker.getInventory();
            String job = worker.getActiveConstruction() == null
                    ? "автодобыча"
                    : "строит " + worker.getActiveConstruction().getTargetType().getDisplayName();
            return "Вася · " + worker.getWorkerState().getDisplayName()
                    + " · инвентарь " + bag.getStoredAmount() + "/" + bag.getSize()
                    + " · металл " + bag.getAmount(ResourceType.METAL)
                    + " · уголь " + bag.getAmount(ResourceType.COAL)
                    + " · " + job;
        }
        return "Юнит · здоровье " + unit.getHealth();
    }

    public String getStatusMessage() { return statusMessage; }

    public String getSelectedObjectText() {
        if (selectedBuilding != null) return describeSelectedBuilding(selectedBuilding);
        if (selectedUnit != null) return describeSelectedUnit(selectedUnit);
        return "Объект не выбран";
    }

    private String getBuildingName(Building building) {
        if (building instanceof MachineGunTower) return "Пулемётная башня";
        if (building instanceof SniperTower) return "Снайперская башня";
        if (building instanceof SlowTower) return "Замедляющая башня";
        if (building instanceof Wall) return "Стена";
        if (building instanceof Conveyor) return "Конвейер";
        if (building instanceof Drill) return "Бур";
        if (building instanceof ConstructionSite) return "Чертёж";
        if (building instanceof Workshop) return "Workshop";
        if (building instanceof FactoryPort) return ((FactoryPort) building).isInput() ? "Вход фабрики" : "Выход фабрики";
        if (building instanceof House) return "Главное здание";
        return "Постройка";
    }

    private void updateBuildMenuResources() {
        if (buildMenu == null || state.getMainBuilding() == null) return;
        Inventory storage = state.getMainBuilding().getInventory();
        buildMenu.updateResourceSummary(
                storage.getAmount(ResourceType.METAL),
                storage.getAmount(ResourceType.COAL),
                storage.getAmount(ResourceType.SCRAP),
                state.getMainBuilding().getAmmoStock(),
                state.getReservedResource(ResourceType.METAL),
                state.getReservedResource(ResourceType.COAL)
        );
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        state.update();
        updateBuildMenuResources();
        if (selectedBuilding != null && !selectedBuilding.isAlive()) selectedBuilding = null;
        if (selectedUnit != null && !selectedUnit.isAlive()) selectedUnit = null;

        if (state.getStatus() != previousStatus) {
            previousStatus = state.getStatus();
            if (buildMenu != null) buildMenu.setVisible(false);
            selectedBuildType = null;
            if (previousStatus == GameStatus.DEFEAT) statusMessage = "Поражение: главное здание уничтожено";
            else if (previousStatus == GameStatus.VICTORY) statusMessage = "Победа: все волны отражены";
        }
        repaint();
    }
}
