package org.example.cardify.model;

/**
 * Built-in page-size presets available in the Cardify page-size configuration.
 *
 * <p>Dimensions are in millimetres (width × height) in <em>portrait</em> orientation.
 * For card formats that are typically landscape on screen (e.g. CR-80) the shorter
 * side is still declared as width so that Cardify's portrait-mode PDF pipeline works
 * without any further rotation.
 *
 * <p>The {@link #CUSTOM} sentinel has {@code -1} for both dimensions; when it is
 * selected the UI exposes manual width / height text fields.
 */
public enum PageSizePreset {

    // ── ID & Access Card formats ──────────────────────────────────────────────
    CR80_CARD  ("CR-80 Card (Standard ID)",    53.98f,  85.60f),
    ID1_ISO    ("ID-1 / ISO 7810 (Bank card)", 53.98f,  85.60f),  // identical physical size, different name
    ID2        ("ID-2 (Visa)",                 74.00f, 105.00f),
    ID3        ("ID-3 (Passport booklet)",     88.00f, 125.00f),

    // ── Common paper / document formats ───────────────────────────────────────
    A3         ("A3",                         297.00f, 420.00f),
    A4         ("A4",                         210.00f, 297.00f),
    A5         ("A5",                         148.00f, 210.00f),
    A6         ("A6 (Postcard)",              105.00f, 148.00f),
    LETTER     ("US Letter",                  215.90f, 279.40f),
    HALF_LETTER("US Half Letter",             139.70f, 215.90f),
    LEGAL      ("US Legal",                   215.90f, 355.60f),

    // ── Badge / label formats ─────────────────────────────────────────────────
    BADGE_4X3  ("Badge 4×3 in",              76.20f, 101.60f),
    LABEL_62MM ("Label 62 mm roll",           62.00f, 100.00f),

    // ── Custom (user-entered) ─────────────────────────────────────────────────
    CUSTOM     ("Custom…",                    -1f,    -1f);

    // ─────────────────────────────────────────────────────────────────────────

    private final String displayName;
    private final float  widthMm;
    private final float  heightMm;

    PageSizePreset(String displayName, float widthMm, float heightMm) {
        this.displayName = displayName;
        this.widthMm     = widthMm;
        this.heightMm    = heightMm;
    }

    public String getDisplayName() { return displayName; }
    public float  getWidthMm()     { return widthMm; }
    public float  getHeightMm()    { return heightMm; }

    /** Returns {@code true} when the user must supply the dimensions manually. */
    public boolean isCustom() { return this == CUSTOM; }

    @Override
    public String toString() { return displayName; }

    /**
     * Finds the preset whose enum name matches {@code name} (case-insensitive),
     * falling back to {@link #CR80_CARD} if the name is unrecognised.
     */
    public static PageSizePreset fromName(String name) {
        if (name == null) return CR80_CARD;
        for (PageSizePreset p : values()) {
            if (p.name().equalsIgnoreCase(name)) return p;
        }
        return CR80_CARD;
    }
}
