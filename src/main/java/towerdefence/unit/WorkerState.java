package towerdefence.unit;

public enum WorkerState {
    IDLE("Ожидает"),
    MOVING_TO_RESOURCE("Едет к месторождению"),
    MINING("Добывает ресурс"),
    RETURNING("Возвращается на склад"),
    UNLOADING("Разгружает ресурсы"),
    MOVING_TO_STORAGE("Едет за материалами"),
    LOADING_CONSTRUCTION("Загружает материалы"),
    MOVING_TO_BUILD_SITE("Едет к стройке"),
    DELIVERING("Доставляет материалы"),
    BUILDING("Строит");

    private final String displayName;
    WorkerState(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
