package dev.gametracker;

import javafx.beans.property.*;

public class Game {
    private final int id;
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty platform = new SimpleStringProperty();
    private final DoubleProperty hours = new SimpleDoubleProperty();
    private final StringProperty source = new SimpleStringProperty();
    private final StringProperty processMatch = new SimpleStringProperty();
    public Game(int id, String name, String platform, double hours, String source, String processMatch) {
        this.id = id;
        this.name.set(name);
        this.platform.set(platform);
        this.hours.set(hours);
        this.source.set(source);
        this.processMatch.set(processMatch);
    }
    public int id() { return id; }
    public String getName() { return name.get(); }
    public String getPlatform() { return platform.get(); }
    public double getHours() { return hours.get(); }
    public String getProcessMatch() { return processMatch.get(); }
    public StringProperty nameProperty() { return name; }
    public StringProperty platformProperty() { return platform; }
    public StringProperty sourceProperty() { return source; }
    public StringProperty hoursTextProperty() {
        return new SimpleStringProperty(String.format("%,.1f hrs", hours.get()));
    }
    @Override public String toString() { return getName() + " (" + getPlatform() + ")"; }
}
