package me.warfare.core.claims;

import java.util.Locale;

/**
 * The kinds of claim a player can create.
 *
 * <p>Each type maps to a configurable protection radius (resolved elsewhere,
 * not stored on the enum, so radii can be tuned in {@code config.yml} without
 * code changes). New tiers can be added here and the rest of the system will
 * handle them generically.</p>
 */
public enum ClaimType {

    /** Standard claim created by a Claim Block. */
    NORMAL,

    /** Larger claim created by an Advanced Claim Block. */
    ADVANCED;

    /**
     * Parses a claim type from a string, case-insensitively.
     *
     * @param raw input text; may be {@code null}
     * @param def fallback returned when {@code raw} is null or unrecognised
     * @return the matching type, or {@code def}
     */
    public static ClaimType fromString(final String raw, final ClaimType def) {
        if (raw == null) {
            return def;
        }
        try {
            return ClaimType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ex) {
            return def;
        }
    }
}
