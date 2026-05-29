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

    public SpreadsheetRow(Map<String, String> values) {
        this.values = new LinkedHashMap<>(values);
    }

    public String getValue(String header) {
        return values.getOrDefault(header, "");
    }

    public Set<String> headers() {
        return values.keySet();
    }

    public StringProperty valueProperty(String header) {
        return new SimpleStringProperty(getValue(header));
    }

    public ReadOnlyStringWrapper readOnlyValue(String header) {
        return new ReadOnlyStringWrapper(getValue(header));
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
