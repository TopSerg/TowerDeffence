package towerdefence.ui;

import towerdefence.game.GameState;
import towerdefence.game.RoadmapGameState;
import towerdefence.world.GameMap;

import javax.swing.*;
import java.awt.*;

public class MainWindow extends JFrame {
    public MainWindow() {
        setTitle("Tower Defense · Roadmap Mega Prototype");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Карта увеличена, чтобы транспорт, разведка и независимый фокус камеры имели физический смысл.
        GameMap map = new GameMap(40, 30);
        RoadmapGameState state = new RoadmapGameState(map);
        RoadmapPresenceGamePanel panel = new RoadmapPresenceGamePanel(map, state);

        JScrollPane worldView = new JScrollPane(panel);
        worldView.setPreferredSize(new Dimension(900, 720));
        worldView.getVerticalScrollBar().setUnitIncrement(24);
        worldView.getHorizontalScrollBar().setUnitIncrement(24);

        setLayout(new BorderLayout());
        add(worldView, BorderLayout.CENTER);
        add(new GameInfoPanel((GameState) state, panel), BorderLayout.EAST);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MainWindow::new);
    }
}
