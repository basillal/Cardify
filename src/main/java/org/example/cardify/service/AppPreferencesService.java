package org.example.cardify.service;

import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

public class AppPreferencesService {
    private static final Preferences PREFS = Preferences.userNodeForPackage(AppPreferencesService.class);
    private static final String KEY_TEMPLATE_PATH    = "lastTemplatePath";
    private static final String KEY_TEMPLATE_HISTORY  = "templateHistory";
    private static final String KEY_EXCEL_PATH         = "lastExcelPath";
    private static final String KEY_CARD_WIDTH_MM      = "cardWidthMm";
    private static final String KEY_CARD_HEIGHT_MM     = "cardHeightMm";
    private static final String KEY_CARD_PRESET        = "cardPreset";

    // Standard CR-80 card defaults
    public static final float DEFAULT_CARD_WIDTH_MM  = 53.98f;
    public static final float DEFAULT_CARD_HEIGHT_MM = 85.60f;

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
                    .distinct()
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

        List<String> normalizedPaths = templatePaths.stream()
                .map(String::trim)
                .filter(path -> !path.isBlank())
                .distinct()
                .toList();

        if (normalizedPaths.isEmpty()) {
            clearSavedTemplatePaths();
            return;
        }

        PREFS.put(KEY_TEMPLATE_HISTORY, String.join(System.lineSeparator(), normalizedPaths));
        PREFS.put(KEY_TEMPLATE_PATH, normalizedPaths.get(0));
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

    // ── Card page-size preferences ─────────────────────────────────────────

    public float getCardWidthMm() {
        return PREFS.getFloat(KEY_CARD_WIDTH_MM, DEFAULT_CARD_WIDTH_MM);
    }

    public float getCardHeightMm() {
        return PREFS.getFloat(KEY_CARD_HEIGHT_MM, DEFAULT_CARD_HEIGHT_MM);
    }

    public void saveCardSizeMm(float widthMm, float heightMm) {
        PREFS.putFloat(KEY_CARD_WIDTH_MM, widthMm);
        PREFS.putFloat(KEY_CARD_HEIGHT_MM, heightMm);
    }

    public void clearCardSizeMm() {
        PREFS.remove(KEY_CARD_WIDTH_MM);
        PREFS.remove(KEY_CARD_HEIGHT_MM);
    }

    public String getCardPreset() {
        return PREFS.get(KEY_CARD_PRESET, null);
    }

    public void saveCardPreset(String presetName) {
        if (presetName == null) {
            PREFS.remove(KEY_CARD_PRESET);
        } else {
            PREFS.put(KEY_CARD_PRESET, presetName);
        }
    }

    private String getPrefKey(String baseKey, String templatePath) {
        if (templatePath == null || templatePath.isBlank()) {
            return baseKey;
        }
        return baseKey + "_" + Math.abs(templatePath.hashCode());
    }

    public float getCardWidthMm(String templatePath) {
        return PREFS.getFloat(getPrefKey(KEY_CARD_WIDTH_MM, templatePath), getCardWidthMm());
    }

    public float getCardHeightMm(String templatePath) {
        return PREFS.getFloat(getPrefKey(KEY_CARD_HEIGHT_MM, templatePath), getCardHeightMm());
    }

    public void saveCardSizeMm(String templatePath, float widthMm, float heightMm) {
        PREFS.putFloat(getPrefKey(KEY_CARD_WIDTH_MM, templatePath), widthMm);
        PREFS.putFloat(getPrefKey(KEY_CARD_HEIGHT_MM, templatePath), heightMm);
        saveCardSizeMm(widthMm, heightMm);
    }

    public void clearCardSizeMm(String templatePath) {
        PREFS.remove(getPrefKey(KEY_CARD_WIDTH_MM, templatePath));
        PREFS.remove(getPrefKey(KEY_CARD_HEIGHT_MM, templatePath));
    }

    public String getCardPreset(String templatePath) {
        return PREFS.get(getPrefKey(KEY_CARD_PRESET, templatePath), getCardPreset());
    }

    public void saveCardPreset(String templatePath, String presetName) {
        if (presetName == null) {
            PREFS.remove(getPrefKey(KEY_CARD_PRESET, templatePath));
        } else {
            PREFS.put(getPrefKey(KEY_CARD_PRESET, templatePath), presetName);
            saveCardPreset(presetName);
        }
    }
}
