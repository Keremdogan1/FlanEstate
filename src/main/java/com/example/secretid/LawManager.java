package com.example.secretid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class LawManager {

    public enum LawStatus {
        VOTING,
        APPROVED,
        REJECTED
    }

    public static class LawInfo {
        public int numericalId;
        public String displayId; // "000001"
        public String title;
        public String description;
        public String creatorSecretId;
        public LawStatus status;
        public Map<String, Boolean> votes; // secretId -> true(evet)/false(hayir)

        public LawInfo() {
            this.votes = new HashMap<>();
        }

        public LawInfo(int numericalId, String title, String description, String creatorSecretId) {
            this.numericalId = numericalId;
            this.displayId = String.format("%06d", numericalId);
            this.title = title;
            this.description = description;
            this.creatorSecretId = creatorSecretId;
            this.status = LawStatus.VOTING;
            this.votes = new HashMap<>();
        }
        
        public int getYesVotes() {
            int count = 0;
            for (boolean v : votes.values()) {
                if (v) count++;
            }
            return count;
        }

        public int getNoVotes() {
            int count = 0;
            for (boolean v : votes.values()) {
                if (!v) count++;
            }
            return count;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final MinecraftServer server;
    private Map<String, LawInfo> laws = new HashMap<>(); // displayId -> LawInfo
    private int lawCounter = 0;

    private static LawManager instance;

    private LawManager(MinecraftServer server) {
        this.server = server;
        load();
    }

    public static void init(MinecraftServer server) {
        instance = new LawManager(server);
    }

    public static LawManager getInstance() {
        return instance;
    }

    private Path getSaveFile() {
        return server.getSavePath(WorldSavePath.ROOT).resolve("secret_id_laws.json");
    }

    public void load() {
        Path file = getSaveFile();
        if (!Files.exists(file)) {
            laws = new HashMap<>();
            lawCounter = 0;
            return;
        }

        try (Reader reader = new FileReader(file.toFile())) {
            Map<String, LawInfo> loadedLaws = GSON.fromJson(reader, new TypeToken<Map<String, LawInfo>>(){}.getType());
            if (loadedLaws != null) {
                laws = loadedLaws;
                for (LawInfo law : laws.values()) {
                    if (law.numericalId > lawCounter) {
                        lawCounter = law.numericalId;
                    }
                }
            }
        } catch (Exception e) {
            SecretIdMod.LOGGER.error("Yasa dosyasi okunamadi (secret_id_laws.json):", e);
            laws = new HashMap<>();
        }
    }

    public void save() {
        Path file = getSaveFile();
        try (Writer writer = new FileWriter(file.toFile())) {
            GSON.toJson(laws, writer);
        } catch (Exception e) {
            SecretIdMod.LOGGER.error("Yasa dosyasi kaydedilemedi (secret_id_laws.json):", e);
        }
    }

    public LawInfo createLaw(String title, String description, String creatorSecretId) {
        lawCounter++;
        LawInfo newLaw = new LawInfo(lawCounter, title, description, creatorSecretId);
        laws.put(newLaw.displayId, newLaw);
        save();
        return newLaw;
    }

    public LawInfo getLaw(String displayId) {
        return laws.get(displayId);
    }
    
    public Collection<LawInfo> getAllLaws() {
        return laws.values();
    }
}
