package org.example.cardify.service;

import java.util.prefs.Preferences;

public class AppPreferencesService {
    private static final Preferences PREFS = Preferences.userNodeForPackage(AppPreferencesService.class);
    private static final String KEY_TEMPLATE_PATH = "lastTemplatePath";
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

    public void clearSavedExcelPath() {
        PREFS.remove(KEY_EXCEL_PATH);
    }
}
