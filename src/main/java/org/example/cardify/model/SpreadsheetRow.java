package org.example.cardify.model;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class SpreadsheetRow {
    private final LinkedHashMap<String, String> values;

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

    public Map<String, String> asMap() {
        return Map.copyOf(values);
    }
}
