package com.example.opticore.config;

import com.example.opticore.util.LoggerUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/** Resilient JSON configuration with safe defaults on corruption. */
public final class ConfigManager {
    public static OpticoreConfigModel CONFIG = new OpticoreConfigModel();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "opticore.json");
    private ConfigManager() {}
    public static void load() {
        if (!CONFIG_FILE.exists()) { save(); return; }
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            OpticoreConfigModel loaded = GSON.fromJson(reader, OpticoreConfigModel.class);
            CONFIG = loaded != null ? loaded : new OpticoreConfigModel();
        } catch (Exception e) {
            LoggerUtil.error("Failed to load opticore.json; restoring defaults.", e);
            CONFIG = new OpticoreConfigModel();
            save();
        }
    }
    public static void save() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) { GSON.toJson(CONFIG, writer); }
        } catch (Exception e) { LoggerUtil.error("Failed to save opticore.json.", e); }
    }
}
