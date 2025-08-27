import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

public class GamePanel extends JPanel implements ActionListener, MouseListener  {
    private GameMap map;
    private GameState state;
    private int tileSize = 32;
    private Unit selectedUnit;

    public GamePanel(GameMap map, GameState state) {
        this.map = map;
        this.state = state;
        setPreferredSize(new Dimension(map.getWidth() * tileSize, map.getHeight() * tileSize));
        addMouseListener(this);
        new Timer(1000 / 60, this).start(); // 60 FPS
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        int tileX = e.getX() / tileSize;
        int tileY = e.getY() / tileSize;
        Tile targetTile = map.getTile(tileX, tileY);

        if (targetTile == null) return;

        if (SwingUtilities.isLeftMouseButton(e)) {
            if (targetTile.hasUnit()) {
                selectedUnit = targetTile.getUnit();
            } else if (selectedUnit != null && targetTile.isPassable()) {
                selectedUnit.setTarget(targetTile);
                selectedUnit = null; // Снимаем выделение
            }
        }
    }

    private void calcTraectory(){

    }

    private void moveUnitTo(Unit unit, Tile targetTile) {
        if (targetTile.isPassable() && !targetTile.hasUnit()) {
            unit.move(targetTile);
            selectedUnit = null;  // Снимаем выделение
        }
    }

    // Остальные методы MouseListener (пустые, но обязательные)
    @Override public void mousePressed(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        renderMap(g);
        renderBuildings(g);
        renderUnits(g);
    }

    private void renderMap(Graphics g) {
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                g.setColor(getTileColor(tile));
                g.fillRect(x * tileSize, y * tileSize, tileSize, tileSize);
            }
        }
    }

    private Color getTileColor(Tile tile) {
        if (tile == null) return Color.BLACK;

        switch (tile.getType()) {
            case DIRT:
                return new Color(139, 69, 19); // Коричневый
            case GRASS:
                return Color.GREEN;
            case ROCK:
                return Color.GRAY;
            case WATER:
                return Color.BLUE;
            default:
                return Color.BLACK;
        }
    }

    private void renderUnits(Graphics g) {
        for (Unit unit : state.getAllUnits()) {
            unit.render(g, tileSize);
        }
    }

    private void renderBuildings(Graphics g) {
        for (Building building : state.getAllBuildings()) {
            building.render(g, tileSize);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Обновляем всех юнитов
        for (Unit unit : state.getAllUnits()) {
            unit.update();
        }
        repaint();
    }
}