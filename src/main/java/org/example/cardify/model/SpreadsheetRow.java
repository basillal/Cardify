package org.example.cardify.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class SpreadsheetRow {
    private final LinkedHashMap<String, String> values;
    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final StringProperty status = new SimpleStringProperty("Pending");

    public SpreadsheetRow(Map<String, String> values) {
        this.values = new LinkedHashMap<>(values);
        if (this.values.containsKey("Status")) {
            status.set(this.values.get("Status"));
            this.values.remove("Status");
        }
    }

    public String getValue(String header) {
        return values.getOrDefault(header, "");
    }

    public Set<String> headers() {
        return values.keySet();
    }

    public StringProperty valueProperty(String header) {
        StringProperty property = new SimpleStringProperty(getValue(header));
        property.addListener((observable, oldValue, newValue) -> values.put(header, newValue));
        return property;
    }

    public ReadOnlyStringWrapper readOnlyValue(String header) {
        return new ReadOnlyStringWrapper(getValue(header));
    }

    public StringProperty statusProperty() {
        return status;
    }

    public String getStatus() {
        return status.get();
    }

    public void setStatus(String status) {
        this.status.set(status);
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public Map<String, String> asMap() {
        return Map.copyOf(values);
    }
}
