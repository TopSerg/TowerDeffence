package towerdefence.ui;

import towerdefence.building.FactoryPort;
import towerdefence.building.Workshop;
import towerdefence.game.RoadmapGameState;
import towerdefence.resource.ResourceType;
import towerdefence.roadmap.RoadmapRuntime;
import towerdefence.world.Direction;
import towerdefence.world.GameMap;
import towerdefence.world.Tile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

/** Compact UI shell for the experimental full-roadmap runtime. */
public class RoadmapGamePanel extends WorkshopGamePanel {
    private static final int TILE = 32;
    private final GameMap map;
    private final RoadmapRuntime roadmap;

    private Workshop viewedWorkshop;
    private Workshop lastWorkshop;
    private Point interiorHover;
    private Direction interiorDirection = Direction.RIGHT;
    private RoadmapRuntime.MachineType machineType = RoadmapRuntime.MachineType.AMMO_PRESS;
    private RoadmapRuntime.FacilityKind facilityKind = RoadmapRuntime.FacilityKind.POWER_PLANT;
    private boolean conveyorPlan;
    private boolean machinePlan;
    private boolean powerView;
    private boolean pipeView;
    private boolean facilityPlan;
    private boolean rallyPlan;
    private String roadmapStatus = "Mega roadmap · middle-click задаёт фокус камеры";

    public RoadmapGamePanel(GameMap map, RoadmapGameState state) {
        super(map, state);
        this.map = map;
        this.roadmap = state.getRoadmap();
        wrapBaseActions();
        installBindings();
    }

    private void wrapBaseActions() {
        ActionMap actions = getActionMap();
        final Action baseBuild = actions.get("toggleBuildMenu");
        final Action baseRotate = actions.get("rotateBuilding");
        final Action baseCancel = actions.get("cancelBuilding");
        final Action baseWorkshop = actions.get("toggleWorkshopView");

        actions.put("toggleBuildMenu", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (viewedWorkshop == null) { if (baseBuild != null) baseBuild.actionPerformed(e); return; }
                conveyorPlan = !conveyorPlan;
                machinePlan = false;
                roadmapStatus = conveyorPlan ? "Interior conveyor PLANNED · ЛКМ поставить · R направление" : "Interior conveyor mode off";
                repaint();
            }
        });
        actions.put("rotateBuilding", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (viewedWorkshop != null && (conveyorPlan || machinePlan)) {
                    interiorDirection = interiorDirection.rotateClockwise();
                    roadmapStatus = "Interior direction: " + interiorDirection.getDisplayName();
                    repaint();
                } else if (baseRotate != null) baseRotate.actionPerformed(e);
            }
        });
        actions.put("cancelBuilding", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (conveyorPlan || machinePlan || facilityPlan || rallyPlan) { cancelModes(); return; }
                if (viewedWorkshop != null) roadmap.requestExitWorkshop(viewedWorkshop);
                viewedWorkshop = null;
                interiorHover = null;
                if (baseCancel != null) baseCancel.actionPerformed(e);
                repaint();
            }
        });
        actions.put("toggleWorkshopView", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (viewedWorkshop != null) {
                    roadmap.requestExitWorkshop(viewedWorkshop);
                    viewedWorkshop = null;
                    interiorHover = null;
                    if (baseWorkshop != null) baseWorkshop.actionPerformed(e);
                } else {
                    if (baseWorkshop != null) baseWorkshop.actionPerformed(e);
                    if (lastWorkshop != null && lastWorkshop.isAlive()) {
                        viewedWorkshop = lastWorkshop;
                        roadmap.requestEnterWorkshop(lastWorkshop);
                    }
                }
                repaint();
            }
        });
    }

    private void installBindings() {
        bind(KeyEvent.VK_M, "roadmapMachine", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (viewedWorkshop == null) return;
                if (!machinePlan) { machinePlan = true; conveyorPlan = false; }
                else {
                    RoadmapRuntime.MachineType[] values = RoadmapRuntime.MachineType.values();
                    machineType = values[(machineType.ordinal() + 1) % values.length];
                }
                roadmapStatus = "Machine PLANNED: " + machineType.getDisplayName() + " · M next · ЛКМ place";
                repaint();
            }
        });
        bind(KeyEvent.VK_F, "roadmapFilter", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { cycleFilter(); }
        });
        bind(KeyEvent.VK_P, "roadmapPower", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (viewedWorkshop != null) return;
                powerView = !powerView; pipeView = false; facilityPlan = false; rallyPlan = false;
                roadmapStatus = powerView ? "POWER VIEW · ЛКМ wire" : "POWER VIEW off";
                repaint();
            }
        });
        bind(KeyEvent.VK_L, "roadmapPipe", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (viewedWorkshop != null) return;
                pipeView = !pipeView; powerView = false; facilityPlan = false; rallyPlan = false;
                roadmapStatus = pipeView ? "FLUID VIEW · ЛКМ pipe" : "FLUID VIEW off";
                repaint();
            }
        });
        bind(KeyEvent.VK_H, "roadmapFacility", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (viewedWorkshop != null) return;
                RoadmapRuntime.FacilityKind[] values = RoadmapRuntime.FacilityKind.values();
                if (!facilityPlan) facilityPlan = true;
                else facilityKind = values[(facilityKind.ordinal() + 1) % values.length];
                powerView = false; pipeView = false; rallyPlan = false;
                roadmapStatus = "Facility PLANNED: " + facilityKind.getDisplayName() + " · H next";
                repaint();
            }
        });
        bind(KeyEvent.VK_G, "roadmapRally", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (viewedWorkshop != null) return;
                rallyPlan = true; facilityPlan = false; powerView = false; pipeView = false;
                roadmapStatus = "Command: ЛКМ задаёт rally point";
                repaint();
            }
        });
        bind(KeyEvent.VK_K, "roadmapCombat", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                roadmapStatus = roadmap.deployCombatRobot() ? "Combat robot deployed" : "Нужен ROBOT_KIT";
                repaint();
            }
        });
        bind(KeyEvent.VK_J, "roadmapBuilder", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                roadmapStatus = roadmap.buildConstructionRover() ? "Construction Rover built" : "Нужен ROBOT_KIT / место";
                repaint();
            }
        });
        bind(KeyEvent.VK_I, "roadmapInternalBot", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (viewedWorkshop == null) return;
                roadmapStatus = roadmap.addInternalBot(viewedWorkshop) ? "Internal bot added" : "Нужен ROBOT_KIT";
                repaint();
            }
        });
        bind(KeyEvent.VK_U, "roadmapVehicle", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                roadmapStatus = roadmap.upgradeVasyaRover() ? "Vasya Rover upgraded" : "Нужно 2 Components / max tier";
                repaint();
            }
        });
    }

    private void bind(int key, String id, Action action) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), id);
        getActionMap().put(id, action);
    }

    private void cancelModes() {
        conveyorPlan = machinePlan = facilityPlan = rallyPlan = false;
        roadmapStatus = "Mega build mode cancelled";
        repaint();
    }

    private void cycleFilter() {
        if (viewedWorkshop == null || interiorHover == null) return;
        FactoryPort port = viewedWorkshop.findPortForGatewayCell(interiorHover.x, interiorHover.y);
        if (port == null || !port.isInput()) { roadmapStatus = "Наведите на INPUT Gateway lane"; repaint(); return; }
        ResourceType filter = roadmap.cycleGatewayFilter(viewedWorkshop, interiorHover.x, interiorHover.y);
        roadmapStatus = "Gateway filter: " + (filter == null ? "ANY" : filter.getDisplayName());
        repaint();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        requestFocusInWindow();
        if (handleAlertClick(e)) return;

        if (viewedWorkshop != null) {
            if (SwingUtilities.isRightMouseButton(e) && !conveyorPlan && !machinePlan) {
                roadmap.requestExitWorkshop(viewedWorkshop);
                viewedWorkshop = null;
                super.mouseClicked(e);
                return;
            }
            Point cell = interiorCellAt(e.getPoint());
            if (SwingUtilities.isLeftMouseButton(e) && cell != null && conveyorPlan) {
                roadmapStatus = roadmap.queueInteriorConveyor(viewedWorkshop, cell.x, cell.y, interiorDirection)
                        ? "Interior conveyor: PLANNED" : "Interior cell occupied/reserved";
                repaint(); return;
            }
            if (SwingUtilities.isLeftMouseButton(e) && cell != null && machinePlan) {
                roadmapStatus = roadmap.queueInteriorMachine(viewedWorkshop, cell.x, cell.y, machineType, interiorDirection)
                        ? machineType.getDisplayName() + ": PLANNED" : "Interior cell occupied/reserved";
                repaint(); return;
            }
            super.mouseClicked(e);
            return;
        }

        Tile target = worldTileAt(e.getPoint());
        if (SwingUtilities.isMiddleMouseButton(e) && target != null) { focusCamera(target); return; }
        if (SwingUtilities.isLeftMouseButton(e) && target != null && powerView) {
            roadmap.toggleWire(target.getX(), target.getY()); repaint(); return;
        }
        if (SwingUtilities.isLeftMouseButton(e) && target != null && pipeView) {
            roadmap.togglePipe(target.getX(), target.getY()); repaint(); return;
        }
        if (SwingUtilities.isLeftMouseButton(e) && target != null && facilityPlan) {
            roadmapStatus = roadmap.placeFacilityPlan(facilityKind, target)
                    ? facilityKind.getDisplayName() + ": PLANNED" : "Facility cannot be planned here / insufficient resources";
            repaint(); return;
        }
        if (SwingUtilities.isLeftMouseButton(e) && target != null && rallyPlan) {
            roadmap.setRallyPoint(target); rallyPlan = false; roadmapStatus = "Rally point set"; repaint(); return;
        }

        Workshop clicked = target != null && target.getBuilding() instanceof Workshop ? (Workshop) target.getBuilding() : null;
        super.mouseClicked(e);
        if (SwingUtilities.isRightMouseButton(e)) lastWorkshop = null;
        else if (SwingUtilities.isLeftMouseButton(e)) {
            lastWorkshop = clicked;
            if (e.getClickCount() >= 2 && clicked != null) {
                viewedWorkshop = clicked;
                roadmap.requestEnterWorkshop(clicked);
            }
        }
    }

    @Override public void mouseMoved(MouseEvent e) {
        super.mouseMoved(e);
        interiorHover = viewedWorkshop == null ? null : interiorCellAt(e.getPoint());
    }
    public void mouseDragged(MouseEvent e) { mouseMoved(e); }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        if (viewedWorkshop == null) renderWorldOverlay(g2); else renderInteriorOverlay(g2);
        renderAlerts(g2);
        renderStatus(g2);
        g2.dispose();
    }

    private void renderWorldOverlay(Graphics2D g2) {
        g2.setColor(new Color(5, 8, 12, 205));
        for (int y = 0; y < map.getHeight(); y++) for (int x = 0; x < map.getWidth(); x++) {
            if (!roadmap.isExplored(x, y)) g2.fillRect(x * TILE, y * TILE, TILE, TILE);
        }
        if (powerView || pipeView) {
            g2.setStroke(new BasicStroke(powerView ? 4f : 5f));
            g2.setColor(powerView ? new Color(255, 224, 80, 220) : new Color(80, 220, 225, 220));
            for (int y = 0; y < map.getHeight(); y++) for (int x = 0; x < map.getWidth(); x++) {
                boolean active = powerView ? roadmap.hasWire(x, y) : roadmap.hasPipe(x, y);
                if (active) g2.fillOval(x * TILE + 12, y * TILE + 12, 9, 9);
            }
        }
        Tile rally = roadmap.getRallyPoint();
        if (rally != null) {
            int cx = rally.getX() * TILE + TILE / 2, cy = rally.getY() * TILE + TILE / 2;
            g2.setColor(new Color(120, 235, 145));
            g2.drawLine(cx - 8, cy, cx + 8, cy); g2.drawLine(cx, cy - 8, cx, cy + 8);
        }
    }

    private void renderInteriorOverlay(Graphics2D g2) {
        RoadmapRuntime.FactoryState factory = roadmap.getFactoryState(viewedWorkshop);
        if (factory == null) return;
        Rectangle b = interiorBounds(); int cell = b.width / 9;
        for (RoadmapRuntime.InteriorMachine machine : factory.getMachines()) {
            int x = b.x + machine.getX() * cell, y = b.y + machine.getY() * cell;
            g2.setColor(machine.isDestroyed() ? new Color(150, 55, 55, 220) : new Color(120, 90, 175, 220));
            g2.fillRoundRect(x + 5, y + 5, cell - 10, cell - 10, 6, 6);
            g2.setColor(Color.WHITE); g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
            g2.drawString(machine.getType().name().substring(0, Math.min(3, machine.getType().name().length())), x + 6, y + 16);
        }
        for (RoadmapRuntime.InteriorBuildTask task : factory.getTasks()) {
            if (task.getPhase() == RoadmapRuntime.BlueprintPhase.DONE) continue;
            int x = b.x + task.getX() * cell, y = b.y + task.getY() * cell;
            g2.setColor(new Color(90, 205, 255)); g2.drawRect(x + 3, y + 3, cell - 6, cell - 6);
        }
        if (interiorHover != null && (conveyorPlan || machinePlan)) {
            g2.setColor(new Color(255, 235, 90));
            g2.drawRect(b.x + interiorHover.x * cell + 1, b.y + interiorHover.y * cell + 1, cell - 2, cell - 2);
        }
        int x = b.x + b.width + 10, y = b.y + 250;
        g2.setColor(new Color(18, 24, 32, 230)); g2.fillRoundRect(x, y, 200, 125, 10, 10);
        g2.setColor(Color.WHITE); g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g2.drawString("Power " + (factory.isPowerAvailable() ? "ON" : "OFF") + " " + factory.getGeneration() + "/" + factory.getPowerDemand(), x + 8, y + 20);
        g2.drawString("Water " + factory.getFluid(ResourceType.WATER) + " · Oil " + factory.getFluid(ResourceType.OIL), x + 8, y + 40);
        g2.drawString("Coolant " + factory.getFluid(ResourceType.COOLANT) + " · Bots " + factory.getInternalBots(), x + 8, y + 60);
        g2.drawString("Vasya " + (roadmap.getVasyaInsideWorkshop() == viewedWorkshop ? "INSIDE" : "EN ROUTE"), x + 8, y + 80);
        g2.drawString("B conveyor · M machine · F filter", x + 8, y + 103);
    }

    private void renderAlerts(Graphics2D g2) {
        List<RoadmapRuntime.Alert> alerts = roadmap.getAlerts(); int y = 8;
        for (int i = 0; i < Math.min(4, alerts.size()); i++, y += 26) {
            g2.setColor(new Color(120, 35, 35, 225)); g2.fillRoundRect(8, y, Math.min(370, getWidth() - 16), 22, 8, 8);
            g2.setColor(Color.WHITE); g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            g2.drawString(alerts.get(i).getText(), 14, y + 15);
        }
    }

    private void renderStatus(Graphics2D g2) {
        int y = getHeight() - 28, w = Math.min(getWidth() - 16, 700);
        g2.setColor(new Color(10, 14, 20, 230)); g2.fillRoundRect(8, y, w, 22, 8, 8);
        g2.setColor(Color.WHITE); g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g2.drawString(roadmapStatus + " · PWR " + roadmap.getPowerGeneration() + "/" + roadmap.getPowerDemand()
                + " · explored " + roadmap.getExploredPercent() + "% · robots " + roadmap.getCombatRobots().size(), 14, y + 15);
    }

    private boolean handleAlertClick(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e) || e.getX() > 380 || e.getY() < 8) return false;
        int index = (e.getY() - 8) / 26; List<RoadmapRuntime.Alert> alerts = roadmap.getAlerts();
        if (index < 0 || index >= Math.min(4, alerts.size())) return false;
        Tile tile = alerts.get(index).getTile(); if (tile != null) focusCamera(tile); return true;
    }

    private void focusCamera(Tile tile) {
        roadmap.setCameraFocus(tile);
        roadmapStatus = "Camera focus " + tile.getX() + "," + tile.getY() + " · Вася едет физически";
        scrollRectToVisible(new Rectangle(tile.getX() * TILE - 200, tile.getY() * TILE - 160, 400, 320));
        repaint();
    }

    private Tile worldTileAt(Point p) { return p == null ? null : map.getTile(p.x / TILE, p.y / TILE); }
    private Rectangle interiorBounds() {
        int availableW = Math.max(9 * 22, getWidth() - 248), availableH = Math.max(9 * 22, getHeight() - 118);
        int cell = Math.max(22, Math.min(48, Math.min(availableW / 9, availableH / 9)));
        int size = cell * 9; return new Rectangle(Math.max(12, (getWidth() - size - 212) / 2), 56, size, size);
    }
    private Point interiorCellAt(Point p) {
        if (viewedWorkshop == null || p == null) return null; Rectangle b = interiorBounds(); if (!b.contains(p)) return null;
        int c = b.width / 9; return new Point(Math.max(0, Math.min(8, (p.x - b.x) / c)), Math.max(0, Math.min(8, (p.y - b.y) / c)));
    }

    @Override public String getStatusMessage() {
        return roadmapStatus == null || roadmapStatus.isEmpty() ? super.getStatusMessage() : roadmapStatus;
    }
}
