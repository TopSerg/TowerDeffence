package towerdefence.resource;

import java.awt.Color;

public enum ResourceType {
    METAL("Металл", new Color(165, 175, 185)),
    SCRAP("Металлолом", new Color(195, 120, 70)),
    ORGANIC("Органика", new Color(90, 165, 85)),
    COAL("Уголь", new Color(65, 65, 70)),
    OIL("Нефть", new Color(45, 40, 55));

    private final String displayName;
    private final Color color;

    ResourceType(String displayName, Color color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Color getColor() {
        return color;
    }
}
