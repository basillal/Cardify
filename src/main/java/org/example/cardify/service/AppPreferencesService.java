package org.example.cardify.service;

import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

public class AppPreferencesService {
    private static final Preferences PREFS = Preferences.userNodeForPackage(AppPreferencesService.class);
    private static final String KEY_TEMPLATE_PATH = "lastTemplatePath";
    private static final String KEY_TEMPLATE_HISTORY = "templateHistory";
    private static final String KEY_EXCEL_PATH = "lastExcelPath";

    public String getSavedTemplatePath() {
        return PREFS.get(KEY_TEMPLATE_PATH, null);
    }

    public void saveTemplatePath(String templatePath) {
        if (templatePath == null) {
            PREFS.remove(KEY_TEMPLATE_PATH);
        } else {
            PREFS.put(KEY_TEMPLATE_PATH, templatePath);
        }
    }

    public List<String> getSavedTemplatePaths() {
        String savedHistory = PREFS.get(KEY_TEMPLATE_HISTORY, null);
        if (savedHistory != null && !savedHistory.isBlank()) {
            return Arrays.stream(savedHistory.split("\\R"))
                    .map(String::trim)
                    .filter(path -> !path.isBlank())
                    .toList();
        }

        String savedTemplate = getSavedTemplatePath();
        if (savedTemplate == null || savedTemplate.isBlank()) {
            return List.of();
        }
        return List.of(savedTemplate);
    }

    public void saveTemplatePaths(List<String> templatePaths) {
        if (templatePaths == null || templatePaths.isEmpty()) {
            clearSavedTemplatePaths();
            return;
        }

        PREFS.put(KEY_TEMPLATE_HISTORY, String.join(System.lineSeparator(), templatePaths));
        PREFS.put(KEY_TEMPLATE_PATH, templatePaths.get(0));
    }

    public String getSavedExcelPath() {
        return PREFS.get(KEY_EXCEL_PATH, null);
    }

    public void saveExcelPath(String excelPath) {
        if (excelPath == null) {
            PREFS.remove(KEY_EXCEL_PATH);
        } else {
            PREFS.put(KEY_EXCEL_PATH, excelPath);
        }
    }

    public void clearSavedTemplatePath() {
        PREFS.remove(KEY_TEMPLATE_PATH);
    }

    public void clearSavedTemplatePaths() {
        PREFS.remove(KEY_TEMPLATE_HISTORY);
        PREFS.remove(KEY_TEMPLATE_PATH);
    }

    public void clearSavedExcelPath() {
        PREFS.remove(KEY_EXCEL_PATH);
    }
}
