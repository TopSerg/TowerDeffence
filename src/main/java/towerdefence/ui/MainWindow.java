package towerdefence.ui;

import towerdefence.game.GameState;
import towerdefence.world.GameMap;

import javax.swing.*;
import java.awt.*;

public class MainWindow extends JFrame {
    public MainWindow() {
        setTitle("Tower Defense Hybrid Example");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        GameMap map = new GameMap(20, 20);
        GameState state = new GameState(map);
        GamePanel panel = new GamePanel(map, state);

        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        add(new GameInfoPanel(state, panel), BorderLayout.EAST);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainWindow());
    }
}
