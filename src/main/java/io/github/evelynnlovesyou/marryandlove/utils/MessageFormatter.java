package io.github.evelynnlovesyou.marryandlove.utils;

import com.google.gson.JsonParseException;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import io.github.evelynnlovesyou.marryandlove.MarryAndLove;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MessageFormatter {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarryAndLove.MOD_ID);
    private static final MiniMessage MINI_MESSAGE = initializeMiniMessage();
    private static final Pattern MINI_MESSAGE_TAG_PATTERN = Pattern.compile("<[/]?[a-zA-Z][^>]*>");

    static {
        if (MINI_MESSAGE != null) {
            LOGGER.info("MiniMessage formatting is enabled.");
        } else {
            LOGGER.warn("MiniMessage formatting is unavailable, falling back to JSON/& formatting.");
        }
    }

    private MessageFormatter() {
    }

    public static Component format(String template) {
        return format(template, null, null);
    }

    public static Component format(String template, HolderLookup.Provider provider) {
        return format(template, null, provider);
    }

    public static Component format(String template, Map<String, String> placeholders) {
        return format(template, placeholders, null);
    }

    public static Component format(String template, Map<String, String> placeholders, HolderLookup.Provider provider) {
        String message = Objects.requireNonNullElse(template, "");
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String token = "%" + entry.getKey() + "%";
                String value = Objects.requireNonNullElse(entry.getValue(), "");
                message = message.replace(token, value);
            }
        }

        String trimmedMessage = message.trim();
        if (looksLikeJsonComponent(trimmedMessage)) {
            Component jsonComponent = tryParseJsonComponent(trimmedMessage, provider);
            if (jsonComponent != null) {
                return jsonComponent;
            }
        }

        if (looksLikeMiniMessage(trimmedMessage)) {
            Component miniMessageComponent = tryParseMiniMessage(trimmedMessage, provider);
            if (miniMessageComponent != null) {
                return miniMessageComponent;
            }
        }

        return Component.literal(translateLegacyCodes(message));
    }

    private static boolean looksLikeJsonComponent(String input) {
        if (input.isEmpty()) {
            return false;
        }

        return (input.startsWith("{") && input.endsWith("}")) || (input.startsWith("[") && input.endsWith("]"));
    }

    private static boolean looksLikeMiniMessage(String input) {
        if (input.isEmpty()) {
            return false;
        }

        return MINI_MESSAGE_TAG_PATTERN.matcher(input).find();
    }

    private static MiniMessage initializeMiniMessage() {
        try {
            return MiniMessage.miniMessage();
        } catch (LinkageError exception) {
            return null;
        }
    }

    private static Component tryParseJsonComponent(String input, HolderLookup.Provider provider) {
        if (provider == null) {
            return null;
        }

        try {
            return Component.Serializer.fromJson(input, provider);
        } catch (JsonParseException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static Component tryParseMiniMessage(String input, HolderLookup.Provider provider) {
        if (provider == null || MINI_MESSAGE == null) {
            return null;
        }

        try {
            net.kyori.adventure.text.Component adventureComponent = MINI_MESSAGE.deserialize(input);
            String json = GsonComponentSerializer.gson().serialize(adventureComponent);
            return Component.Serializer.fromJson(json, provider);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String translateLegacyCodes(String message) {
        return message.replace('&', '§');
    }
}