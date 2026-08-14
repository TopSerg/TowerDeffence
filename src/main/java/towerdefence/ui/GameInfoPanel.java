package towerdefence.ui;

import towerdefence.building.House;
import towerdefence.combat.WaveManager;
import towerdefence.game.GameState;
import towerdefence.game.GameStatus;
import towerdefence.resource.Inventory;
import towerdefence.resource.ResourceType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Информация об игре вне игрового поля: панель больше не перекрывает карту. */
public class GameInfoPanel extends JPanel {
    private final GameState state;
    private final GamePanel gamePanel;
    private final JLabel status = label(true);
    private final JLabel base = label(false);
    private final JLabel resources = label(false);
    private final JLabel wave = label(false);
    private final JLabel waveCountdown = label(true);
    private final JButton waveSettingsButton = new JButton("Настроить время");
    private final JLabel selected = label(false);

    public GameInfoPanel(GameState state, GamePanel gamePanel) {
        this.state = state;
        this.gamePanel = gamePanel;

        setPreferredSize(new Dimension(280, 640));
        setBackground(new Color(15, 20, 28));
        setBorder(new EmptyBorder(14, 14, 14, 14));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        add(section("СОСТОЯНИЕ", status));
        add(Box.createVerticalStrut(14));
        add(section("БАЗА", base));
        add(Box.createVerticalStrut(14));
        add(section("РЕСУРСЫ", resources));
        add(Box.createVerticalStrut(14));
        add(createWaveTimerCard());
        add(Box.createVerticalStrut(10));
        add(section("ВОЛНЫ", wave));
        add(Box.createVerticalStrut(14));
        add(section("ВЫБРАННЫЙ ОБЪЕКТ", selected));
        add(Box.createVerticalGlue());

        JLabel controls = label(false);
        controls.setText("<html>B — строительство<br>R — поворот<br>Esc — отмена"
                + "<br>Delete — снести<br>Рабочий + клик по залежи — назначить добычу</html>");
        add(section("УПРАВЛЕНИЕ", controls));

        refresh();
        new Timer(200, event -> refresh()).start();
    }

    private void refresh() {
        House house = state.getMainBuilding();
        Inventory inventory = house.getInventory();
        WaveManager manager = state.getWaveManager();

        status.setText(html(gamePanel.getStatusMessage()));
        base.setText(html(house.getHealth() + "/" + house.getMaxHealth() + " HP<br>"
                + house.getAmmoStock() + " патронов<br>Врагов: " + state.getAllEnemies().size()
                + " · уничтожено: " + state.getDestroyedEnemies()));
        resources.setText(html("Металл: " + inventory.getAmount(ResourceType.METAL)
                + " · свободно " + state.getAvailableResource(ResourceType.METAL)
                + "<br>Уголь: " + inventory.getAmount(ResourceType.COAL)
                + " · свободно " + state.getAvailableResource(ResourceType.COAL)
                + "<br>Металлолом: " + inventory.getAmount(ResourceType.SCRAP)
                + "<br>Склад: " + inventory.getStoredAmount() + "/" + inventory.getSize()
                + "<br>Чертежей в очереди: " + state.getConstructionQueue().size()
                + "<br>Добыто лома за бой: " + state.getTotalScrapCollected()));
        wave.setText(html(manager.getCurrentWave() + "/" + manager.getTotalWaves()
                + "<br>" + manager.getStatusText()));
        refreshWaveCountdown(manager);
        selected.setText(html(gamePanel.getSelectedObjectText()));
    }

    private JPanel createWaveTimerCard() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));
        panel.setBackground(new Color(27, 42, 59));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(92, 165, 225), 2),
                new EmptyBorder(10, 10, 10, 10)));

        JLabel title = label(true);
        title.setText("ТАЙМЕР ВОЛНЫ");
        title.setForeground(new Color(120, 190, 255));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(7));

        waveCountdown.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        waveCountdown.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(waveCountdown);
        panel.add(Box.createVerticalStrut(8));

        waveSettingsButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        waveSettingsButton.setFocusPainted(false);
        waveSettingsButton.addActionListener(event -> showWaveDelayDialog());
        panel.add(waveSettingsButton);
        return panel;
    }

    private void refreshWaveCountdown(WaveManager manager) {
        if (manager.isCountingDownToWave()) {
            waveCountdown.setForeground(new Color(255, 205, 92));
            waveCountdown.setText("ДО ВОЛНЫ: " + manager.getSecondsUntilNextWave() + " С");
        } else if (state.getStatus() == GameStatus.RUNNING) {
            waveCountdown.setForeground(new Color(112, 225, 145));
            waveCountdown.setText("ВОЛНА " + manager.getCurrentWave() + " ИДЁТ");
        } else {
            waveCountdown.setForeground(Color.LIGHT_GRAY);
            waveCountdown.setText("ВОЛНЫ ЗАВЕРШЕНЫ");
        }
        waveSettingsButton.setEnabled(state.getStatus() == GameStatus.RUNNING);
    }

    private void showWaveDelayDialog() {
        WaveManager manager = state.getWaveManager();
        int initialValue = manager.isCountingDownToWave()
                ? manager.getSecondsUntilNextWave()
                : manager.getWaveDelaySeconds();
        JSpinner seconds = new JSpinner(new SpinnerNumberModel(
                initialValue,
                WaveManager.MIN_WAVE_DELAY_SECONDS,
                WaveManager.MAX_WAVE_DELAY_SECONDS,
                1));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(seconds, "0");
        seconds.setEditor(editor);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.add(new JLabel("Время до волны, секунд:"), BorderLayout.NORTH);
        content.add(seconds, BorderLayout.CENTER);
        content.add(new JLabel("Настройка применяется к текущему таймеру и следующим волнам."),
                BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(this),
                content,
                "Настройка времени до волны",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            manager.setWaveDelaySeconds((Integer) seconds.getValue());
            refresh();
        }
    }

    private JPanel section(String title, JLabel content) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel heading = label(true);
        heading.setForeground(new Color(120, 190, 255));
        heading.setText(title);
        panel.add(heading);
        panel.add(Box.createVerticalStrut(5));
        panel.add(content);
        return panel;
    }

    private static JLabel label(boolean bold) {
        JLabel label = new JLabel();
        label.setForeground(Color.WHITE);
        label.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, bold ? 13 : 12));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static String html(String value) {
        return "<html><div style='width:245px'>" + value + "</div></html>";
    }
}
