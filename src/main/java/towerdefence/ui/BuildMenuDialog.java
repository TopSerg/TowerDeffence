package towerdefence.ui;

import towerdefence.building.BuildableType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/** Отдельная масштабируемая панель строительства с вкладками и карточками построек. */
public class BuildMenuDialog extends JDialog {
    private final Consumer<BuildableType> selectionListener;
    private final Runnable cancelListener;
    private final JLabel statusLabel = new JLabel("Выберите постройку");
    private final JLabel resourceLabel = new JLabel("Склад базы");
    private final Map<BuildableType, JPanel> cards = new EnumMap<>(BuildableType.class);
    private BuildableType selectedType;

    public BuildMenuDialog(Window owner,
                           Consumer<BuildableType> selectionListener,
                           Runnable cancelListener) {
        super(owner, "Панель строительства");
        this.selectionListener = selectionListener;
        this.cancelListener = cancelListener;

        setModal(false);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setMinimumSize(new Dimension(590, 585));
        setResizable(true);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Выберите постройку и размещайте её на карте");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        resourceLabel.setForeground(new Color(80, 95, 115));
        resourceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(3));
        header.add(resourceLabel);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Оборона", createBuildingTab(
                BuildableType.MACHINE_GUN_TOWER,
                BuildableType.SNIPER_TOWER,
                BuildableType.SLOW_TOWER,
                BuildableType.WALL
        ));
        tabs.addTab("Логистика", createBuildingTab(BuildableType.CONVEYOR));
        tabs.addTab("Производство", createBuildingTab(BuildableType.DRILL));
        tabs.addTab("Фабрики", createBuildingTab(
                BuildableType.WORKSHOP,
                BuildableType.FACTORY_INPUT_PORT,
                BuildableType.FACTORY_OUTPUT_PORT));
        tabs.addTab("Инфраструктура", createPlaceholder(
                "Здесь позже появятся энергетика, склады и внутренние цеховые здания."));

        statusLabel.setBorder(new EmptyBorder(6, 7, 6, 7));
        statusLabel.setOpaque(true);
        setFeedback("Выберите карточку постройки", false);

        JButton cancelButton = new JButton("Отменить режим строительства");
        cancelButton.setFocusPainted(false);
        cancelButton.addActionListener(e -> {
            selectedType = null;
            refreshCardBorders();
            if (cancelListener != null) cancelListener.run();
            setFeedback("Режим строительства отменён", false);
        });

        JLabel controls = new JLabel(
                "Размещение — ЛКМ на карте · конвейер — R · удалить выбранное — Delete");
        controls.setFont(controls.getFont().deriveFont(Font.PLAIN, 11f));
        controls.setForeground(Color.DARK_GRAY);

        JPanel footerTop = new JPanel(new BorderLayout(8, 4));
        footerTop.add(statusLabel, BorderLayout.CENTER);
        footerTop.add(cancelButton, BorderLayout.EAST);

        JPanel footer = new JPanel(new BorderLayout(4, 4));
        footer.add(footerTop, BorderLayout.NORTH);
        footer.add(controls, BorderLayout.SOUTH);

        root.add(header, BorderLayout.NORTH);
        root.add(tabs, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
        pack();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                requestFocusInWindow();
            }
        });
    }

    private JComponent createBuildingTab(BuildableType... types) {
        JPanel list = new ScrollableVerticalPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(new EmptyBorder(8, 8, 8, 8));

        for (BuildableType type : types) {
            JPanel card = createBuildingCard(type);
            cards.put(type, card);
            list.add(card);
            list.add(Box.createVerticalStrut(8));
        }

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(14);
        return scrollPane;
    }

    private JPanel createBuildingCard(BuildableType type) {
        JPanel card = new JPanel(new BorderLayout(10, 6));
        card.setBorder(createCardBorder(false));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, type.isTower() ? 146 : 132));
        card.setBackground(new Color(248, 249, 251));

        JLabel icon = new JLabel(createIcon(type));
        icon.setBorder(new EmptyBorder(3, 3, 3, 3));

        JLabel name = new JLabel(type.getDisplayName());
        name.setFont(name.getFont().deriveFont(Font.BOLD, 14f));

        JTextArea description = new JTextArea(type.getDescription());
        description.setEditable(false);
        description.setOpaque(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setFont(description.getFont().deriveFont(11.5f));
        description.setRows(2);
        description.setColumns(28);
        description.setMinimumSize(new Dimension(0, 34));

        JLabel cost = new JLabel("Цена: " + type.getCostText());
        cost.setFont(cost.getFont().deriveFont(Font.BOLD, 11.5f));
        cost.setForeground(new Color(130, 78, 32));

        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setMinimumSize(new Dimension(0, 0));
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.add(name);
        details.add(Box.createVerticalStrut(2));
        details.add(description);
        details.add(Box.createVerticalStrut(3));
        details.add(cost);

        JLabel stats = new JLabel(type.getStatsText());
        stats.setFont(stats.getFont().deriveFont(Font.PLAIN, 11f));
        stats.setForeground(new Color(55, 76, 105));
        details.add(stats);
        if (type.isTower()) {
            JLabel special = new JLabel("Особенность: " + type.getSpecialAction());
            special.setFont(special.getFont().deriveFont(Font.ITALIC, 11f));
            special.setForeground(new Color(40, 105, 105));
            details.add(special);
        }

        JButton buildButton = new JButton("Строить");
        buildButton.setPreferredSize(new Dimension(105, 38));
        buildButton.setFocusPainted(false);
        buildButton.addActionListener(e -> select(type));

        card.add(icon, BorderLayout.WEST);
        card.add(details, BorderLayout.CENTER);
        card.add(buildButton, BorderLayout.EAST);
        return card;
    }

    private JPanel createPlaceholder(String text) {
        JPanel panel = new JPanel(new GridBagLayout());
        JLabel label = new JLabel(text);
        label.setForeground(Color.GRAY);
        panel.add(label);
        return panel;
    }

    private Icon createIcon(BuildableType type) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(type.getPreviewColor());
                g2.fillRoundRect(x + 3, y + 3, 46, 46, 9, 9);
                g2.setColor(new Color(35, 35, 35));
                int cx = x + 26;
                int cy = y + 26;

                if (type == BuildableType.MACHINE_GUN_TOWER) {
                    g2.fillOval(x + 14, y + 12, 24, 24);
                    g2.setStroke(new BasicStroke(4f));
                    g2.drawLine(cx, cy, cx, y + 5);
                } else if (type == BuildableType.SNIPER_TOWER) {
                    g2.fillRect(x + 15, y + 15, 22, 22);
                    g2.setStroke(new BasicStroke(3f));
                    g2.drawLine(cx, cy, x + 47, y + 6);
                    g2.setColor(new Color(220, 210, 255));
                    g2.fillOval(cx - 3, cy - 3, 6, 6);
                } else if (type == BuildableType.SLOW_TOWER) {
                    g2.fillOval(x + 12, y + 12, 28, 28);
                    g2.setColor(new Color(175, 245, 250));
                    g2.drawOval(x + 17, y + 17, 18, 18);
                    g2.drawLine(cx, cy, x + 45, cy);
                } else if (type == BuildableType.WALL) {
                    g2.drawRect(x + 10, y + 14, 32, 25);
                    g2.drawLine(x + 10, y + 26, x + 42, y + 26);
                    g2.drawLine(x + 26, y + 14, x + 26, y + 26);
                    g2.drawLine(x + 19, y + 26, x + 19, y + 39);
                    g2.drawLine(x + 34, y + 26, x + 34, y + 39);
                } else if (type == BuildableType.DRILL) {
                    g2.drawOval(x + 13, y + 13, 26, 26);
                    g2.fillOval(x + 22, y + 22, 8, 8);
                    g2.drawLine(cx, y + 7, cx, y + 45);
                    g2.drawLine(x + 7, cy, x + 45, cy);
                } else if (type == BuildableType.WORKSHOP) {
                    g2.drawRoundRect(x + 8, y + 8, 36, 36, 8, 8);
                    g2.drawLine(x + 20, y + 8, x + 20, y + 44);
                    g2.drawLine(x + 32, y + 8, x + 32, y + 44);
                    g2.drawLine(x + 8, y + 20, x + 44, y + 20);
                    g2.drawLine(x + 8, y + 32, x + 44, y + 32);
                } else if (type == BuildableType.FACTORY_INPUT_PORT || type == BuildableType.FACTORY_OUTPUT_PORT) {
                    g2.drawRoundRect(x + 10, y + 10, 30, 30, 7, 7);
                    g2.drawString(type == BuildableType.FACTORY_INPUT_PORT ? "IN" : "OUT", x + 12, y + 30);
                } else {
                    g2.setStroke(new BasicStroke(4f));
                    g2.drawLine(x + 11, cy, x + 41, cy);
                    g2.drawLine(x + 41, cy, x + 31, y + 17);
                    g2.drawLine(x + 41, cy, x + 31, y + 35);
                }
                g2.dispose();
            }

            @Override public int getIconWidth() { return 52; }
            @Override public int getIconHeight() { return 52; }
        };
    }

    private void select(BuildableType type) {
        selectedType = type;
        refreshCardBorders();
        setFeedback("Выбрано: " + type.getDisplayName() + " · " + type.getCostText(), false);
        selectionListener.accept(type);
    }

    private void refreshCardBorders() {
        for (Map.Entry<BuildableType, JPanel> entry : cards.entrySet()) {
            entry.getValue().setBorder(createCardBorder(entry.getKey() == selectedType));
        }
    }

    private javax.swing.border.Border createCardBorder(boolean selected) {
        Color color = selected ? new Color(67, 132, 205) : new Color(210, 215, 222);
        int thickness = selected ? 2 : 1;
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, thickness),
                new EmptyBorder(8, 8, 8, 8)
        );
    }

    public void setFeedback(String text, boolean error) {
        statusLabel.setText(text == null || text.isBlank() ? " " : text);
        statusLabel.setForeground(error ? new Color(145, 38, 38) : new Color(45, 76, 112));
        statusLabel.setBackground(error ? new Color(255, 230, 230) : new Color(229, 240, 252));
    }

    public void updateResourceSummary(int metal, int coal, int scrap, int ammo,
                                      int reservedMetal, int reservedCoal) {
        resourceLabel.setText("Склад: металл " + metal + " (резерв " + reservedMetal + ")"
                + " · уголь " + coal + " (резерв " + reservedCoal + ")"
                + " · лом " + scrap + " · патроны " + ammo);
    }

    public void clearSelection() {
        selectedType = null;
        refreshCardBorders();
    }

    private static class ScrollableVerticalPanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return Math.max(32, visibleRect.height - 32); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    public void showNear(Component component) {
        if (!isVisible()) {
            Window owner = SwingUtilities.getWindowAncestor(component);
            if (owner != null) {
                Point point = owner.getLocationOnScreen();
                Rectangle screen = owner.getGraphicsConfiguration().getBounds();
                int x = point.x + owner.getWidth() + 10;
                int y = point.y;
                if (x + getWidth() > screen.x + screen.width) x = point.x - getWidth() - 10;
                if (x < screen.x) x = screen.x + screen.width - getWidth() - 10;
                y = Math.max(screen.y, Math.min(y, screen.y + screen.height - getHeight()));
                setLocation(x, y);
            } else {
                setLocationRelativeTo(component);
            }
            setVisible(true);
        } else {
            toFront();
        }
    }
}
