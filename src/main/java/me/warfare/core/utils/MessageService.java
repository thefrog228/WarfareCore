package me.warfare.core.utils;

import me.warfare.core.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Single entry point for all player-facing text.
 *
 * <p>Supports <b>both</b> MiniMessage tags (e.g. {@code <red>}, gradients) and
 * legacy ampersand codes (e.g. {@code &c}). Any legacy codes are converted to
 * MiniMessage-equivalent components first, then the whole string is parsed by
 * MiniMessage, so a single message may freely mix the two styles.</p>
 *
 * <p>Routing every message through one service keeps the prefix, colour
 * handling, and Adventure usage consistent across the plugin.</p>
 */
public final class MessageService {

    /** Parses MiniMessage tags. Thread-safe and reusable. */
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /**
     * Serializer for legacy {@code &} codes. We use it only to translate legacy
     * input into a Component, which we then re-serialise to MiniMessage so the
     * final parse pass understands the whole string uniformly.
     */
    private static final LegacyComponentSerializer LEGACY_AMP =
            LegacyComponentSerializer.legacyAmpersand();

    private final ConfigManager config;

    /**
     * @param config config manager used to read the prefix and prefix toggle
     */
    public MessageService(final ConfigManager config) {
        this.config = config;
    }

    /**
     * Parses a raw string (MiniMessage and/or legacy codes) into a Component.
     *
     * @param raw raw message text; may mix MiniMessage tags and {@code &} codes
     * @return parsed {@link Component}
     */
    public @NotNull Component parse(final String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        // If the string contains legacy '&' codes, normalise them into
        // MiniMessage tags first so a single MiniMessage parse covers both.
        final String normalised = containsLegacy(raw)
                ? MINI.serialize(LEGACY_AMP.deserialize(raw))
                : raw;
        return MINI.deserialize(normalised);
    }

    /**
     * Parses a message and prepends the configured prefix when enabled.
     *
     * @param raw raw message text
     * @return prefixed, parsed {@link Component}
     */
    public @NotNull Component prefixed(final String raw) {
        final boolean usePrefix = config.getBoolean("messages.use-prefix", true);
        if (!usePrefix) {
            return parse(raw);
        }
        final String prefix = config.getString("messages.prefix", "");
        return parse(prefix).append(parse(raw));
    }

    /**
     * Sends a config-keyed, prefixed message to a recipient.
     *
     * @param target  recipient (player or console)
     * @param path    config path under which the raw message lives
     * @param def     fallback text if the key is missing
     */
    public void send(final CommandSender target, final String path, final String def) {
        target.sendMessage(prefixed(config.getString(path, def)));
    }

    /**
     * Sends an already-resolved raw message (prefixed) to a recipient.
     *
     * @param target recipient
     * @param raw    raw message text
     */
    public void sendRaw(final CommandSender target, final String raw) {
        target.sendMessage(prefixed(raw));
    }

    /**
     * Cheap heuristic for whether a string contains legacy ampersand codes.
     *
     * @param s input string
     * @return {@code true} if an {@code &} is followed by a legacy code char
     */
    private boolean containsLegacy(final String s) {
        for (int i = 0; i < s.length() - 1; i++) {
            if (s.charAt(i) == '&'
                    && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(s.charAt(i + 1)) >= 0) {
                return true;
            }
        }
        return false;
    }
}
