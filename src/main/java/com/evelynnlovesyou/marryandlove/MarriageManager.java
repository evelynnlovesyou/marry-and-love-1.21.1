package com.evelynnlovesyou.marryandlove;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MarriageManager {

    private static final Gson GSON = new Gson();

    private static final Map<UUID, UUID> marriages = new ConcurrentHashMap<>();
    private static final Map<UUID, MarriageRequest> pendingRequests = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> tpCooldowns = new ConcurrentHashMap<>();

    private static File dataFile;

    public static void init(MinecraftServer server) {
        if (server == null) return;
        Path runDir = server.getRunDirectory();
        Path configDir = runDir.resolve("config").resolve("marryandlove");
        File folder = configDir.toFile();
        if (!folder.exists()) {
            boolean created = folder.mkdirs();
            if (!created) {
                System.err.println("Failed to create config directory: " + folder.getAbsolutePath());
                // Optionally: throw an exception or fallback
            }
        }
        dataFile = new File(folder, "marriages.json");
        load();
    }

    private static void load() {
        if (dataFile == null || !dataFile.exists()) return;
        try (FileReader reader = new FileReader(dataFile)) {
            Type mapType = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> rawMap = GSON.fromJson(reader, mapType);
            marriages.clear();
            if (rawMap != null) {
                for (Map.Entry<String, String> entry : rawMap.entrySet()) {
                    marriages.put(UUID.fromString(entry.getKey()), UUID.fromString(entry.getValue()));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load marriages.json: " + e.getMessage());
        }
    }

    private static void save() {
        if (dataFile == null) return;
        try (FileWriter writer = new FileWriter(dataFile)) {
            Map<String, String> rawMap = new ConcurrentHashMap<>();
            for (Map.Entry<UUID, UUID> entry : marriages.entrySet()) {
                rawMap.put(entry.getKey().toString(), entry.getValue().toString());
            }
            GSON.toJson(rawMap, writer);
        } catch (Exception e) {
            System.err.println("Failed to save marriages.json: " + e.getMessage());
        }
    }

    private static class MarriageRequest {
        final UUID requester;
        final long timestamp;

        MarriageRequest(UUID requester) {
            this.requester = requester;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 30_000;
        }
    }

    public static boolean sendRequest(UUID requester, UUID target) {
        MarriageRequest existing = pendingRequests.get(target);
        if (existing != null && !existing.isExpired()) return false;
        pendingRequests.put(target, new MarriageRequest(requester));
        return true;
    }

    public static boolean hasPendingRequest(UUID player) {
        MarriageRequest req = pendingRequests.get(player);
        if (req == null) return false;
        if (req.isExpired()) {
            pendingRequests.remove(player);
            return false;
        }
        return true;
    }

    public static boolean acceptRequest(UUID player) {
        MarriageRequest req = pendingRequests.get(player);
        if (req == null || req.isExpired()) {
            pendingRequests.remove(player);
            return false;
        }
        UUID requester = req.requester;
        if (isMarried(requester) || isMarried(player)) {
            pendingRequests.remove(player);
            return false;
        }
        marriages.put(requester, player);
        marriages.put(player, requester);
        pendingRequests.remove(player);
        save();
        return true;
    }

    public static void denyRequest(UUID player) {
        pendingRequests.remove(player);
    }

    public static boolean isMarried(UUID player) {
        return marriages.containsKey(player);
    }

    public static void divorce(UUID player) {
        UUID spouse = marriages.remove(player);
        if (spouse != null) {
            marriages.remove(spouse);
        }
        save();
    }

    public static boolean teleportToSpouse(PlayerEntity player) {
        UUID playerId = player.getUuid();
        if (!isMarried(playerId)) return false;

        long now = System.currentTimeMillis();
        Long cooldownEnd = tpCooldowns.get(playerId);
        if (cooldownEnd != null && cooldownEnd > now) return false;

        UUID spouseId = marriages.get(playerId);
        if (spouseId == null) return false;

        MinecraftServer server = player.getServer();
        if (server == null) {
            player.sendMessage(Text.literal("Server is not available right now."), false);
            return false;
        }
        ServerPlayerEntity spousePlayer = server.getPlayerManager().getPlayer(spouseId);
        if (spousePlayer == null) return false;

        Vec3d spousePos = spousePlayer.getPos();

        // Teleport player to spouse with an empty set of PositionFlag (required in 1.21.1)
        player.teleport(
            spousePlayer.getServerWorld(),
            spousePos.x, spousePos.y, spousePos.z,
            java.util.Collections.emptySet(),
            spousePlayer.getYaw(), spousePlayer.getPitch()
        );

        tpCooldowns.put(playerId, now + 30_000);
        return true;
    }

    public static UUID getSpouse(UUID player) {
        return marriages.get(player);
    }

    public static UUID getPendingRequester(UUID target) {
        MarriageRequest req = pendingRequests.get(target);
        if (req == null || req.isExpired()) return null;
        return req.requester;
    }
}
