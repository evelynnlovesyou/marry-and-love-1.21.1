package io.github.evelynnlovesyou.marryandlove.manager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github.evelynnlovesyou.marryandlove.MarryAndLove;
import io.github.evelynnlovesyou.marryandlove.config.ConfigReader;
import io.github.evelynnlovesyou.marryandlove.config.LangReader;
import io.github.evelynnlovesyou.marryandlove.utils.MessageFormatter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public class MarriageManager {
    private static final Map<UUID, UUID> MARRIAGES = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> PENDING_PROPOSALS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> PENDING_DIVORCE_NOTIFICATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PROPOSAL_TIMESTAMPS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<UUID>> PROPOSALS_BY_PROPOSER = new ConcurrentHashMap<>();
    private static final Set<UUID> LOADED_PLAYERS = new CopyOnWriteArraySet<>();
    private static final Object SAVE_LOCK = new Object();
    private static long lastProposalCheckTick = 0;
    private static long cachedTimeoutMs = -1;
    private static final String NBT_ROOT_KEY = "marryandlove";
    private static final String NBT_SPOUSE_KEY = "spouse";
    private static final String NBT_PROPOSAL_KEY = "proposal";
    private static final String NBT_PROPOSAL_TIMESTAMP_KEY = "proposalTimestamp";
    private static final String NBT_PENDING_DIVORCE_NOTIFIER_KEY = "pendingDivorceNotifier";

    public static void init(){
        MARRIAGES.clear();
        PENDING_PROPOSALS.clear();
        PENDING_DIVORCE_NOTIFICATIONS.clear();
        PROPOSAL_TIMESTAMPS.clear();
        PROPOSALS_BY_PROPOSER.clear();
        LOADED_PLAYERS.clear();
        lastProposalCheckTick = 0;
        cachedTimeoutMs = -1;
    }

    public static void invalidateTimeoutCache() {
        cachedTimeoutMs = -1;
    }

    public static void handleJoin(ServerPlayer player) {
        ensureLoaded(player);
        deliverPendingDivorceNotification(player);
    }

    public static boolean canMarry(ServerPlayer player) {
        ensureLoaded(player);
        return !MARRIAGES.containsKey(player.getUUID());
    }

    public static UUID getSpouse(ServerPlayer player) {
        ensureLoaded(player);
        return MARRIAGES.get(player.getUUID());
    }

    public static boolean marry(ServerPlayer playerOne, ServerPlayer playerTwo) {
        ensureLoaded(playerOne);
        ensureLoaded(playerTwo);
        UUID playerOneId = playerOne.getUUID();
        UUID playerTwoId = playerTwo.getUUID();

        if (playerOneId.equals(playerTwoId)) {
            return false;
        }

        if (MARRIAGES.containsKey(playerOneId) || MARRIAGES.containsKey(playerTwoId)) {
            return false;
        }

        MARRIAGES.put(playerOneId, playerTwoId);
        MARRIAGES.put(playerTwoId, playerOneId);
        PENDING_PROPOSALS.remove(playerOneId);
        PENDING_PROPOSALS.remove(playerTwoId);
        PROPOSAL_TIMESTAMPS.remove(playerOneId);
        PROPOSAL_TIMESTAMPS.remove(playerTwoId);
        PROPOSALS_BY_PROPOSER.remove(playerOneId);
        PROPOSALS_BY_PROPOSER.remove(playerTwoId);
        savePlayerData(playerOne);
        savePlayerData(playerTwo);
        return true;
    }

    public static boolean propose(ServerPlayer proposer, ServerPlayer target) {
        ensureLoaded(proposer);
        ensureLoaded(target);
        UUID proposerId = proposer.getUUID();
        UUID targetId = target.getUUID();

        if (proposerId.equals(targetId)) {
            return false;
        }

        if (MARRIAGES.containsKey(proposerId) || MARRIAGES.containsKey(targetId)) {
            return false;
        }

        // Anti-spam: Check if proposer already has a pending proposal (incoming or outgoing)
        if (PENDING_PROPOSALS.containsKey(proposerId) || PENDING_PROPOSALS.containsValue(proposerId)) {
            return false;
        }

        // Anti-spam: Check if target already has a pending proposal (incoming or outgoing)
        if (PENDING_PROPOSALS.containsKey(targetId) || PENDING_PROPOSALS.containsValue(targetId)) {
            return false;
        }

        PENDING_PROPOSALS.put(targetId, proposerId);
        PROPOSAL_TIMESTAMPS.put(targetId, System.currentTimeMillis());
        PROPOSALS_BY_PROPOSER.computeIfAbsent(proposerId, k -> ConcurrentHashMap.newKeySet()).add(targetId);
        savePlayerData(target);
        return true;
    }

    public static boolean hasProposal(ServerPlayer player) {
        ensureLoaded(player);
        UUID playerId = player.getUUID();
        // Check if player has incoming proposal or is already proposing to someone
        return PENDING_PROPOSALS.containsKey(playerId) || PENDING_PROPOSALS.containsValue(playerId);
    }

    public static ProposalResult getLatestProposal(ServerPlayer target) {
        ensureLoaded(target);
        UUID targetId = target.getUUID();
        UUID proposerId = PENDING_PROPOSALS.get(targetId);
        if (proposerId == null) {
            return new ProposalResult(null, false);
        }

        if (ConfigReader.PROPOSAL_TIMEOUT_SECONDS < 0) {
            return new ProposalResult(proposerId, false);
        }

        long timeoutMs = Math.max(0L, (long) ConfigReader.PROPOSAL_TIMEOUT_SECONDS * 1000L);
        if (timeoutMs == 0L) {
            clearProposal(target);
            return new ProposalResult(null, true);
        }

        long proposalTime = PROPOSAL_TIMESTAMPS.getOrDefault(targetId, 0L);
        if (proposalTime == 0L || System.currentTimeMillis() - proposalTime > timeoutMs) {
            notifyProposalExpired(target, proposerId);
            clearProposal(target);
            return new ProposalResult(null, true);
        }

        return new ProposalResult(proposerId, false);
    }

    public static void clearProposal(ServerPlayer target) {
        ensureLoaded(target);
        UUID targetId = target.getUUID();
        UUID proposerId = PENDING_PROPOSALS.remove(targetId);
        PROPOSAL_TIMESTAMPS.remove(targetId);
        if (proposerId != null) {
            PROPOSALS_BY_PROPOSER.computeIfPresent(proposerId, (k, targets) -> {
                targets.remove(targetId);
                return targets.isEmpty() ? null : targets;
            });
        }
        savePlayerData(target);
    }

    public static void queueDivorceNotification(MinecraftServer server, UUID targetId, UUID divorcerId) {
        if (server == null || targetId == null || divorcerId == null) {
            return;
        }

        PENDING_DIVORCE_NOTIFICATIONS.put(targetId, divorcerId);
        savePlayerData(server, targetId);
    }

    public static void handleDisconnect(ServerPlayer player) {
        UUID playerId = player.getUUID();

        boolean removedIncoming = PENDING_PROPOSALS.remove(playerId) != null;
        PROPOSAL_TIMESTAMPS.remove(playerId);
        if (removedIncoming) {
            savePlayerData(player);
        }

        // Use reverse index for O(1) lookup instead of O(n) scan
        Set<UUID> affectedTargets = PROPOSALS_BY_PROPOSER.remove(playerId);
        if (affectedTargets != null) {
            for (UUID targetId : affectedTargets) {
                PENDING_PROPOSALS.remove(targetId);
                PROPOSAL_TIMESTAMPS.remove(targetId);
                MinecraftServer server = player.server;
                if (server != null) {
                    savePlayerData(server, targetId);
                }
            }
        }

        LOADED_PLAYERS.remove(playerId);
    }

    public static void ensureLoaded(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (LOADED_PLAYERS.contains(playerId)) {
            return;
        }

        loadPlayerData(player);
        LOADED_PLAYERS.add(playerId);
    }

    public static boolean divorce(ServerPlayer player) {
        ensureLoaded(player);
        UUID playerId = player.getUUID();
        UUID spouseId = MARRIAGES.get(playerId);
        if (spouseId == null) {
            return false;
        }

        MARRIAGES.remove(playerId);
        MARRIAGES.remove(spouseId);

        MinecraftServer server = player.server;
        savePlayerData(server, spouseId);
        savePlayerData(server, playerId);
        return true;
    }

    public static void loadPlayerData(ServerPlayer player) {
        Path filePath = getPlayerDataFile(player.server, player.getUUID());
        if (filePath == null || !Files.exists(filePath)) {
            return;
        }

        try {
            CompoundTag root = NbtIo.readCompressed(filePath, NbtAccounter.unlimitedHeap());
            if (root == null || !root.contains(NBT_ROOT_KEY)) {
                return;
            }

            CompoundTag data = root.getCompound(NBT_ROOT_KEY);
            String spouseValue = data.getString(NBT_SPOUSE_KEY);
            String proposalValue = data.getString(NBT_PROPOSAL_KEY);
            String pendingDivorceNotifierValue = data.getString(NBT_PENDING_DIVORCE_NOTIFIER_KEY);
            long proposalTimestamp = data.getLong(NBT_PROPOSAL_TIMESTAMP_KEY);

            if (!spouseValue.isEmpty()) {
                try {
                    UUID spouseId = UUID.fromString(spouseValue);
                    registerLoadedMarriage(player.getUUID(), spouseId);
                } catch (IllegalArgumentException e) {
                    MarryAndLove.LOGGER.warn("Corrupted spouse UUID for player {}: {}", player.getUUID(), spouseValue);
                }
            }

            if (!proposalValue.isEmpty()) {
                try {
                    UUID proposerId = UUID.fromString(proposalValue);
                    if (isProposalExpired(proposalTimestamp)) {
                        clearProposalInternal(player);
                    } else {
                        PENDING_PROPOSALS.put(player.getUUID(), proposerId);
                        PROPOSAL_TIMESTAMPS.put(player.getUUID(), proposalTimestamp);
                        PROPOSALS_BY_PROPOSER.computeIfAbsent(proposerId, k -> ConcurrentHashMap.newKeySet()).add(player.getUUID());
                    }
                } catch (IllegalArgumentException e) {
                    MarryAndLove.LOGGER.warn("Corrupted proposal UUID for player {}: {}", player.getUUID(), proposalValue);
                }
            }

            if (!pendingDivorceNotifierValue.isEmpty()) {
                try {
                    UUID divorcerId = UUID.fromString(pendingDivorceNotifierValue);
                    PENDING_DIVORCE_NOTIFICATIONS.put(player.getUUID(), divorcerId);
                } catch (IllegalArgumentException e) {
                    MarryAndLove.LOGGER.warn("Corrupted divorce notifier UUID for player {}: {}", player.getUUID(), pendingDivorceNotifierValue);
                }
            }
        } catch (IOException exception) {
            MarryAndLove.LOGGER.error("Failed to read marriage data for {}", player.getUUID(), exception);
        }
    }

    private static void deliverPendingDivorceNotification(ServerPlayer player) {
        UUID playerId = player.getUUID();
        UUID divorcerId = PENDING_DIVORCE_NOTIFICATIONS.remove(playerId);
        if (divorcerId == null) {
            return;
        }

        String divorcerName = getPlayerName(player.server, divorcerId);
        player.sendSystemMessage(
            MessageFormatter.format(
                LangReader.DIVORCE_SPOUSE_NOTIFIED,
                Map.of("player", divorcerName),
                player.registryAccess()
            )
        );
        savePlayerData(player);
    }

    private static void notifyProposalExpired(ServerPlayer target, UUID proposerId) {
        if (target.server == null || proposerId == null) {
            return;
        }

        ServerPlayer proposer = target.server.getPlayerList().getPlayer(proposerId);
        if (proposer != null) {
            proposer.sendSystemMessage(
                MessageFormatter.format(
                    LangReader.MARRY_SENT_PROPOSAL_EXPIRED,
                    Map.of("player", target.getName().getString()),
                    proposer.registryAccess()
                )
            );
        }

        target.sendSystemMessage(
            MessageFormatter.format(
                LangReader.MARRY_RECEIVED_PROPOSAL_EXPIRED,
                Map.of("player", getPlayerName(target.server, proposerId)),
                target.registryAccess()
            )
        );
    }

    private static String getPlayerName(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) {
            return "Unknown";
        }

        ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerId);
        if (onlinePlayer != null) {
            return onlinePlayer.getName().getString();
        }

        Optional<GameProfile> cachedProfile = server.getProfileCache().get(playerId);
        if (cachedProfile.isPresent()) {
            return cachedProfile.get().getName();
        }

        return "Unknown";
    }

    private static boolean isProposalExpired(long proposalTimestamp) {
        if (ConfigReader.PROPOSAL_TIMEOUT_SECONDS < 0) {
            return false;
        }

        long timeoutMs = Math.max(0L, (long) ConfigReader.PROPOSAL_TIMEOUT_SECONDS * 1000L);
        if (timeoutMs == 0L) {
            return true;
        }

        return proposalTimestamp == 0L || System.currentTimeMillis() - proposalTimestamp > timeoutMs;
    }

    private static void registerLoadedMarriage(UUID playerId, UUID spouseId) {
        if (playerId.equals(spouseId)) {
            MarryAndLove.LOGGER.warn("Ignoring invalid self-marriage entry for {}", playerId);
            return;
        }

        UUID existingForPlayer = MARRIAGES.get(playerId);
        if (existingForPlayer != null && !existingForPlayer.equals(spouseId)) {
            MarryAndLove.LOGGER.warn("Ignoring conflicting marriage entry for {} -> {} (already mapped to {})", playerId, spouseId, existingForPlayer);
            return;
        }

        UUID existingForSpouse = MARRIAGES.get(spouseId);
        if (existingForSpouse != null && !existingForSpouse.equals(playerId)) {
            MarryAndLove.LOGGER.warn("Ignoring conflicting marriage entry for {} -> {} (spouse already mapped to {})", playerId, spouseId, existingForSpouse);
            return;
        }

        MARRIAGES.put(playerId, spouseId);
        if (playerId.equals(existingForSpouse)) {
            MARRIAGES.put(spouseId, playerId);
        }
    }

    private static void clearProposalInternal(ServerPlayer target) {
        UUID targetId = target.getUUID();
        UUID proposerId = PENDING_PROPOSALS.remove(targetId);
        PROPOSAL_TIMESTAMPS.remove(targetId);
        if (proposerId != null) {
            PROPOSALS_BY_PROPOSER.computeIfPresent(proposerId, (k, targets) -> {
                targets.remove(targetId);
                return targets.isEmpty() ? null : targets;
            });
        }
        savePlayerData(target);
    }

    private static void savePlayerData(ServerPlayer player) {
        savePlayerData(player.server, player.getUUID());
    }

    private static void savePlayerData(MinecraftServer server, UUID playerId) {
        Path filePath = getPlayerDataFile(server, playerId);
        if (filePath == null) {
            return;
        }

        synchronized (SAVE_LOCK) {
            try {
                Files.createDirectories(filePath.getParent());
                CompoundTag root = new CompoundTag();
                CompoundTag data = new CompoundTag();

                UUID spouseId = MARRIAGES.get(playerId);
                UUID proposerId = PENDING_PROPOSALS.get(playerId);
                UUID pendingDivorceNotifierId = PENDING_DIVORCE_NOTIFICATIONS.get(playerId);
                long proposalTimestamp = PROPOSAL_TIMESTAMPS.getOrDefault(playerId, 0L);

                data.putString(NBT_SPOUSE_KEY, spouseId == null ? "" : spouseId.toString());
                data.putString(NBT_PROPOSAL_KEY, proposerId == null ? "" : proposerId.toString());
                data.putLong(NBT_PROPOSAL_TIMESTAMP_KEY, proposerId == null ? 0L : proposalTimestamp);
                data.putString(NBT_PENDING_DIVORCE_NOTIFIER_KEY, pendingDivorceNotifierId == null ? "" : pendingDivorceNotifierId.toString());
                root.put(NBT_ROOT_KEY, data);

                NbtIo.writeCompressed(root, filePath);
            } catch (IOException exception) {
                MarryAndLove.LOGGER.error("Failed to save marriage data for {}", playerId, exception);
            }
        }
    }

    private static Path getPlayerDataFile(MinecraftServer server, UUID playerId) {
        if (server == null) {
            return null;
        }

        Path playerDataDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve("marryandlove");
        return playerDataDir.resolve(playerId.toString() + ".dat");
    }

    public static void checkExpiredProposals(MinecraftServer server) {
        // Throttle: only check every 100 ticks (5 seconds) instead of every tick
        long currentTick = server.getTickCount();
        if (currentTick - lastProposalCheckTick < 100) {
            return;
        }
        lastProposalCheckTick = currentTick;

        if (server == null || ConfigReader.PROPOSAL_TIMEOUT_SECONDS < 0 || PROPOSAL_TIMESTAMPS.isEmpty()) {
            return;
        }

        // Cache timeout calculation
        if (cachedTimeoutMs < 0) {
            cachedTimeoutMs = Math.max(0L, (long) ConfigReader.PROPOSAL_TIMEOUT_SECONDS * 1000L);
        }
        long timeoutMs = cachedTimeoutMs;
        if (timeoutMs == 0L) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Collect expired proposals in a single pass
        PROPOSAL_TIMESTAMPS.entrySet().removeIf(entry -> {
            UUID targetId = entry.getKey();
            long proposalTime = entry.getValue();

            if (currentTime - proposalTime > timeoutMs) {
                UUID proposerId = PENDING_PROPOSALS.remove(targetId);
                if (proposerId != null) {
                    ServerPlayer target = server.getPlayerList().getPlayer(targetId);
                    if (target != null) {
                        notifyProposalExpired(target, proposerId);
                    }
                    // Clean up reverse index
                    PROPOSALS_BY_PROPOSER.computeIfPresent(proposerId, (k, targets) -> {
                        targets.remove(targetId);
                        return targets.isEmpty() ? null : targets;
                    });
                    // Save cleared data
                    savePlayerData(server, targetId);
                }
                return true;  // Remove from PROPOSAL_TIMESTAMPS
            }
            return false;
        });
    }

    public static class ProposalResult {
        private final UUID proposerId;
        private final boolean expired;

        public ProposalResult(UUID proposerId, boolean expired) {
            this.proposerId = proposerId;
            this.expired = expired;
        }

        public UUID getProposerId() {
            return proposerId;
        }

        public boolean isExpired() {
            return expired;
        }
    }
}
