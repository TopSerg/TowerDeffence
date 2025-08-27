import javax.swing.*;

public class MainWindow extends JFrame {
    public MainWindow() {
        setTitle("Tower Defense Hybrid Example");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        GameMap map = new GameMap(20, 20);
        GameState state = new GameState(map);
        GamePanel panel = new GamePanel(map, state);

        add(panel);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainWindow());
    }
}