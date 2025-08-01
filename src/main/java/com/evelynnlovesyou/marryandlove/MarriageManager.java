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

    // Gson instance for JSON read/write, super handy! :)
    private static final Gson GSON = new Gson();

    // Map of married players: player UUID -> spouse UUID
    private static final Map<UUID, UUID> marriages = new ConcurrentHashMap<>();

    // Map of pending marriage requests: target player UUID -> request details
    private static final Map<UUID, MarriageRequest> pendingRequests = new ConcurrentHashMap<>();

    // Map to track teleport cooldowns: player UUID -> time (ms) when cooldown ends
    private static final Map<UUID, Long> tpCooldowns = new ConcurrentHashMap<>();

    // File where marriage data is stored on disk
    private static File dataFile;

    // Call this once when server starts to set up data folder and load saved marriages :)
    public static void init(MinecraftServer server) {
        if (server == null) return; // Nothing to do if no server
        Path runDir = server.getRunDirectory(); // Server's root folder
        Path configDir = runDir.resolve("config").resolve("marryandlove"); // Config folder path
        File folder = configDir.toFile();
        if (!folder.exists()) {
            // Create config folder if it doesn't exist, try not to fail pls :/
            boolean created = folder.mkdirs();
            if (!created) {
                System.err.println("Failed to create config directory: " + folder.getAbsolutePath());
            }
        }
        dataFile = new File(folder, "marriages.json"); // Our data file
        load(); // Load existing marriages from disk :)
    }

    // Load marriages from marriages.json into memory map
    private static void load() {
        if (dataFile == null || !dataFile.exists()) return; // No file? No loading
        try (FileReader reader = new FileReader(dataFile)) {
            Type mapType = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> rawMap = GSON.fromJson(reader, mapType); // Load raw string map
            marriages.clear(); // Clear old data first
            if (rawMap != null) {
                // Convert string keys/values to UUIDs and store in marriages map
                for (Map.Entry<String, String> entry : rawMap.entrySet()) {
                    marriages.put(UUID.fromString(entry.getKey()), UUID.fromString(entry.getValue()));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load marriages.json: " + e.getMessage());
        }
    }

    // Save current marriages map to marriages.json file on disk :)
    private static void save() {
        if (dataFile == null) return; // No file? Can't save
        try (FileWriter writer = new FileWriter(dataFile)) {
            Map<String, String> rawMap = new ConcurrentHashMap<>();
            // Convert UUIDs to strings for JSON serialization
            for (Map.Entry<UUID, UUID> entry : marriages.entrySet()) {
                rawMap.put(entry.getKey().toString(), entry.getValue().toString());
            }
            GSON.toJson(rawMap, writer); // Write JSON
        } catch (Exception e) {
            System.err.println("Failed to save marriages.json: " + e.getMessage());
        }
    }

    // Inner class representing a marriage request with a timestamp
    private static class MarriageRequest {
        final UUID requester; // Who sent the request
        final long timestamp; // When request was sent (ms since epoch)

        MarriageRequest(UUID requester) {
            this.requester = requester;
            this.timestamp = System.currentTimeMillis(); // Mark time now :)
        }

        // Check if request expired (after 30 seconds)
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 30_000;
        }
    }

    // Send a marriage request from requester to target player
    public static boolean sendRequest(UUID requester, UUID target) {
        MarriageRequest existing = pendingRequests.get(target);
        // If target already has a valid request, reject new one
        if (existing != null && !existing.isExpired()) return false;
        // Store new request
        pendingRequests.put(target, new MarriageRequest(requester));
        return true; // Request sent successfully :)
    }

    // Check if player has a pending (non-expired) marriage request
    public static boolean hasPendingRequest(UUID player) {
        MarriageRequest req = pendingRequests.get(player);
        if (req == null) return false; // No request found
        if (req.isExpired()) {
            // Expired request? Remove and say no
            pendingRequests.remove(player);
            return false;
        }
        return true; // Valid request exists :)
    }

    // Accept the pending marriage request for the player
    public static boolean acceptRequest(UUID player) {
        MarriageRequest req = pendingRequests.get(player);
        // No request or expired? Can't accept
        if (req == null || req.isExpired()) {
            pendingRequests.remove(player);
            return false;
        }
        UUID requester = req.requester;
        // If either player already married, reject
        if (isMarried(requester) || isMarried(player)) {
            pendingRequests.remove(player);
            return false;
        }
        // Add both to marriages map (bidirectional)
        marriages.put(requester, player);
        marriages.put(player, requester);
        pendingRequests.remove(player); // Remove request now that accepted
        save(); // Save changes to disk
        return true; // Success! They're married now :)
    }

    // Deny a pending marriage request (just remove it)
    public static void denyRequest(UUID player) {
        pendingRequests.remove(player);
    }

    // Check if player is currently married
    public static boolean isMarried(UUID player) {
        return marriages.containsKey(player);
    }

    // Divorce a player — remove marriage entries both ways and save changes
    public static void divorce(UUID player) {
        UUID spouse = marriages.remove(player); // Remove player's spouse
        if (spouse != null) {
            marriages.remove(spouse); // Remove spouse's entry too
        }
        save(); // Save divorce data to disk
    }

    // Teleport player to their spouse if married and cooldown expired
    public static boolean teleportToSpouse(PlayerEntity player) {
        UUID playerId = player.getUuid();
        if (!isMarried(playerId)) return false; // Not married? No teleport :)

        long now = System.currentTimeMillis();
        Long cooldownEnd = tpCooldowns.get(playerId);
        if (cooldownEnd != null && cooldownEnd > now) return false; // Still cooling down

        UUID spouseId = marriages.get(playerId);
        if (spouseId == null) return false; // No spouse found?

        MinecraftServer server = player.getServer();
        if (server == null) {
            // Server not available, can't teleport
            player.sendMessage(Text.literal("Server is not available right now."), false);
            return false;
        }
        ServerPlayerEntity spousePlayer = server.getPlayerManager().getPlayer(spouseId);
        if (spousePlayer == null) return false; // Spouse offline

        Vec3d spousePos = spousePlayer.getPos();

        // Teleport player to spouse's exact position and orientation
        player.teleport(
                spousePlayer.getServerWorld(),
                spousePos.x, spousePos.y, spousePos.z,
                java.util.Collections.emptySet(),
                spousePlayer.getYaw(), spousePlayer.getPitch()
        );

        // Add 30 second cooldown for teleport to prevent spam
        tpCooldowns.put(playerId, now + 30_000);
        return true; // Teleport success! :)
    }

    // Get the UUID of player's spouse, or null if not married
    public static UUID getSpouse(UUID player) {
        return marriages.get(player);
    }

    // Get who requested marriage from the target player, or null if none/expired
    public static UUID getPendingRequester(UUID target) {
        MarriageRequest req = pendingRequests.get(target);
        if (req == null || req.isExpired()) return null;
        return req.requester;
    }
}
