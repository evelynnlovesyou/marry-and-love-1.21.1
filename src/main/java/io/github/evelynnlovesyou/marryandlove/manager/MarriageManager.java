package io.github.evelynnlovesyou.marryandlove.manager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.evelynnlovesyou.marryandlove.MarryAndLove;
import io.github.evelynnlovesyou.marryandlove.config.ConfigReader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public class MarriageManager {
    private static final Map<UUID, UUID> MARRIAGES = new HashMap<>();
    private static final Map<UUID, UUID> PENDING_PROPOSALS = new HashMap<>();
    private static final Map<UUID, Long> PROPOSAL_TIMESTAMPS = new HashMap<>();
    private static final Set<UUID> LOADED_PLAYERS = new HashSet<>();
    private static final String NBT_ROOT_KEY = "marryandlove";
    private static final String NBT_SPOUSE_KEY = "spouse";
    private static final String NBT_PROPOSAL_KEY = "proposal";
    private static final String NBT_PROPOSAL_TIMESTAMP_KEY = "proposalTimestamp";

    public static void init(){
        MARRIAGES.clear();
        PENDING_PROPOSALS.clear();
        PROPOSAL_TIMESTAMPS.clear();
        LOADED_PLAYERS.clear();
    }

    public static boolean canMarry(ServerPlayer player) {
        ensureLoaded(player);
        return !MARRIAGES.containsKey(player.getUUID());
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

        PENDING_PROPOSALS.put(targetId, proposerId);
        PROPOSAL_TIMESTAMPS.put(targetId, System.currentTimeMillis());
        savePlayerData(target);
        return true;
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
            return new ProposalResult(proposerId, false);
        }

        long proposalTime = PROPOSAL_TIMESTAMPS.getOrDefault(targetId, 0L);
        if (proposalTime == 0L || System.currentTimeMillis() - proposalTime > timeoutMs) {
            clearProposal(target);
            return new ProposalResult(null, true);
        }

        return new ProposalResult(proposerId, false);
    }

    public static void clearProposal(ServerPlayer target) {
        ensureLoaded(target);
        PENDING_PROPOSALS.remove(target.getUUID());
        PROPOSAL_TIMESTAMPS.remove(target.getUUID());
        savePlayerData(target);
    }

    public static void handleDisconnect(ServerPlayer player) {
        UUID playerId = player.getUUID();
        LOADED_PLAYERS.remove(playerId);
        PENDING_PROPOSALS.remove(playerId);
        PROPOSAL_TIMESTAMPS.remove(playerId);
    }

    public static void ensureLoaded(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (LOADED_PLAYERS.contains(playerId)) {
            return;
        }

        loadPlayerData(player);
        LOADED_PLAYERS.add(playerId);
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
            long proposalTimestamp = data.getLong(NBT_PROPOSAL_TIMESTAMP_KEY);

            if (!spouseValue.isEmpty()) {
                UUID spouseId = UUID.fromString(spouseValue);
                MARRIAGES.put(player.getUUID(), spouseId);
                MARRIAGES.put(spouseId, player.getUUID());
            }

            if (!proposalValue.isEmpty()) {
                UUID proposerId = UUID.fromString(proposalValue);
                if (isProposalExpired(proposalTimestamp)) {
                    clearProposalInternal(player);
                } else {
                    PENDING_PROPOSALS.put(player.getUUID(), proposerId);
                    PROPOSAL_TIMESTAMPS.put(player.getUUID(), proposalTimestamp);
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            MarryAndLove.LOGGER.error("Failed to read marriage data for {}", player.getUUID(), exception);
        }
    }

    private static boolean isProposalExpired(long proposalTimestamp) {
        if (ConfigReader.PROPOSAL_TIMEOUT_SECONDS < 0) {
            return false;
        }

        long timeoutMs = Math.max(0L, (long) ConfigReader.PROPOSAL_TIMEOUT_SECONDS * 1000L);
        if (timeoutMs == 0L) {
            return false;
        }

        return proposalTimestamp == 0L || System.currentTimeMillis() - proposalTimestamp > timeoutMs;
    }

    private static void clearProposalInternal(ServerPlayer target) {
        PENDING_PROPOSALS.remove(target.getUUID());
        PROPOSAL_TIMESTAMPS.remove(target.getUUID());
        savePlayerData(target);
    }

    private static void savePlayerData(ServerPlayer player) {
        Path filePath = getPlayerDataFile(player.server, player.getUUID());
        if (filePath == null) {
            return;
        }

        try {
            Files.createDirectories(filePath.getParent());
            CompoundTag root = new CompoundTag();
            CompoundTag data = new CompoundTag();

            UUID spouseId = MARRIAGES.get(player.getUUID());
            UUID proposerId = PENDING_PROPOSALS.get(player.getUUID());
            long proposalTimestamp = PROPOSAL_TIMESTAMPS.getOrDefault(player.getUUID(), 0L);

            data.putString(NBT_SPOUSE_KEY, spouseId == null ? "" : spouseId.toString());
            data.putString(NBT_PROPOSAL_KEY, proposerId == null ? "" : proposerId.toString());
            data.putLong(NBT_PROPOSAL_TIMESTAMP_KEY, proposerId == null ? 0L : proposalTimestamp);
            root.put(NBT_ROOT_KEY, data);

            NbtIo.writeCompressed(root, filePath);
        } catch (IOException exception) {
            MarryAndLove.LOGGER.error("Failed to save marriage data for {}", player.getUUID(), exception);
        }
    }

    private static Path getPlayerDataFile(MinecraftServer server, UUID playerId) {
        if (server == null) {
            return null;
        }

        Path playerDataDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve("marryandlove");
        return playerDataDir.resolve(playerId.toString() + ".dat");
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
