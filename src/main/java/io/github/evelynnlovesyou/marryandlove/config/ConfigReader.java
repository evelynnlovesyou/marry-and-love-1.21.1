package io.github.evelynnlovesyou.marryandlove.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.github.evelynnlovesyou.marryandlove.MarryAndLove;
import net.fabricmc.loader.api.FabricLoader;

public class ConfigReader {
    public static int PROPOSAL_TIMEOUT_SECONDS;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ConfigData DEFAULT_CONFIG = new ConfigData();
    private static ConfigData config = new ConfigData();

    private static final Path CONFIG_FILE_PATH = FabricLoader.getInstance()
        .getConfigDir()
        .resolve("marryandlove")
        .resolve("config.json");

    public static void init() {
        try {
            ensureConfigFileExists();
            loadConfig();
        } catch (IOException exception) {
            config = new ConfigData();
            syncPublicFields();
            MarryAndLove.LOGGER.error("Failed to load config.json, it either doesn't exist or is corrupted - using defaults.", exception);
        }
    }

    private static void ensureConfigFileExists() throws IOException {
        Path parentDir = CONFIG_FILE_PATH.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }

        if (!Files.exists(CONFIG_FILE_PATH)) {
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE_PATH)) {
                GSON.toJson(DEFAULT_CONFIG, writer);
            }
        }
    }

    private static void loadConfig() throws IOException {
        ConfigData loadedConfig;
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE_PATH)) {
            loadedConfig = GSON.fromJson(reader, ConfigData.class);
        }

        if (loadedConfig == null) {
            loadedConfig = new ConfigData();
        }

        if (loadedConfig.proposalTimeoutSeconds == -1) {
            // -1 means never expires
            loadedConfig.proposalTimeoutSeconds = -1;
        } else if (loadedConfig.proposalTimeoutSeconds <= 0) {
            loadedConfig.proposalTimeoutSeconds = DEFAULT_CONFIG.proposalTimeoutSeconds;
        }

        config = loadedConfig;
        syncPublicFields();

        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE_PATH)) {
            GSON.toJson(config, writer);
        }
    }

    private static void syncPublicFields() {
        PROPOSAL_TIMEOUT_SECONDS = config.proposalTimeoutSeconds;
    }

    private static class ConfigData {
        public int proposalTimeoutSeconds = 60;
    }
}
