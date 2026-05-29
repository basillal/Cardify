package org.example.cardify.service;

import org.example.cardify.util.ImageDataUrlConverter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HtmlTemplateService {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_\\-]+)\\s*\\}\\}");
    private static final Pattern QR_PLACEHOLDER_PATTERN = Pattern.compile("(?i)^(qr(?:[_-].+)?|.+(?:[_-]qr|qr))$");

    public Set<String> extractPlaceholders(Path templatePath) {
        return findPlaceholders(readTemplate(templatePath));
    }

    public String readTemplate(Path templatePath) {
        try {
            return Files.readString(templatePath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read template: " + templatePath, exception);
        }
    }

    public String renderTemplate(String template, Map<String, String> values) {
        return renderTemplate(template, values, Map.of());
    }

    public String renderTemplate(String template, Map<String, String> values, Map<String, String> qrMappings) {
        Map<String, String> resolvedValues = new LinkedHashMap<>();
        Map<String, String> mapping = qrMappings == null ? Map.of() : qrMappings;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            String normalized = value.trim();
            if (isQrPlaceholder(entry.getKey())) {
                String sourceKey = mapping.getOrDefault(entry.getKey(), entry.getKey());
                String sourceValue = values.getOrDefault(sourceKey, value);
                resolvedValues.put(entry.getKey(), ImageDataUrlConverter.toQrCodeDataUrl(sourceValue == null ? "" : sourceValue.trim()));
            } else if (ImageDataUrlConverter.looksLikeImagePath(normalized)) {
                resolvedValues.put(entry.getKey(), ImageDataUrlConverter.toDataUrl(Path.of(normalized)));
            } else {
                resolvedValues.put(entry.getKey(), escapeHtml(value));
            }
        }

        String rendered = template;
        for (Map.Entry<String, String> entry : resolvedValues.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
            rendered = rendered.replace("{{ " + entry.getKey() + " }}", entry.getValue());
        }
        return rendered;
    }

    public Set<String> findPlaceholders(String template) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        return matcher.results()
                .map(matchResult -> matchResult.group(1).trim())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public boolean looksLikeQrPlaceholder(String placeholderName) {
        return placeholderName != null && placeholderName.toLowerCase(Locale.ROOT).contains("qr");
    }

    private boolean isQrPlaceholder(String placeholderName) {
        return looksLikeQrPlaceholder(placeholderName);
    }
}
