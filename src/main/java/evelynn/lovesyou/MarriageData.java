package com.evelynnlovesyou.marryandlove;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.*;

public class MarriageData extends PersistentState {
    private static final String NAME = "marryandlove_data";
    private static MarriageData INSTANCE;

    private final Map<UUID, UUID> marriages = new HashMap<>();
    private final Map<UUID, UUID> proposals = new HashMap<>();

    public static void init() {
        // no-op
    }

    public static MarriageData getInstance() {
        return INSTANCE;
    }

    public static MarriageData getOrCreate(MinecraftServer server) {
        PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
        INSTANCE = manager.getOrCreate(nbt -> readFromNbt(nbt), MarriageData::new, NAME);
        return INSTANCE;
    }

    public static MarriageData readFromNbt(NbtCompound nbt) {
        MarriageData data = new MarriageData();
        NbtCompound marriagesTag = nbt.getCompound("Marriages");
        for (String key : marriagesTag.getKeys()) {
            data.marriages.put(UUID.fromString(key), UUID.fromString(marriagesTag.getString(key)));
        }
        return data;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound marriagesTag = new NbtCompound();
        for (var entry : marriages.entrySet()) {
            marriagesTag.putString(entry.getKey().toString(), entry.getValue().toString());
        }
        nbt.put("Marriages", marriagesTag);
        return nbt;
    }

    private void markDirtyAndSave() {
        this.markDirty();
    }

    public void sendProposal(ServerPlayerEntity sender, ServerPlayerEntity target) {
        if (sender.getUuid().equals(target.getUuid())) {
            sender.sendMessage(Text.literal("You can't marry yourself!"));
            return;
        }

        if (isMarried(sender.getUuid())) {
            sender.sendMessage(Text.literal("You are already married."));
            return;
        }

        proposals.put(target.getUuid(), sender.getUuid());
        sender.sendMessage(Text.literal("Marriage proposal sent to " + target.getName().getString()));
        target.sendMessage(Text.literal(sender.getName().getString() + " wants to marry you! Use /marry accept to say yes."));
    }

    public void acceptProposal(ServerPlayerEntity player) {
        UUID proposer = proposals.remove(player.getUuid());
        if (proposer == null) {
            player.sendMessage(Text.literal("You have no pending proposals."));
            return;
        }

        marriages.put(player.getUuid(), proposer);
        marriages.put(proposer, player.getUuid());

        markDirtyAndSave();

        player.sendMessage(Text.literal("You are now married to " + getPlayerName(proposer)));
        ServerPlayerEntity proposerPlayer = player.getServer().getPlayerManager().getPlayer(proposer);
        if (proposerPlayer != null) {
            proposerPlayer.sendMessage(Text.literal("You are now married to " + player.getName().getString()));
        }
    }

    public void teleportToPartner(ServerPlayerEntity player) {
        UUID partnerId = marriages.get(player.getUuid());
        if (partnerId == null) {
            player.sendMessage(Text.literal("You are not married."));
            return;
        }

        ServerPlayerEntity partner = player.getServer().getPlayerManager().getPlayer(partnerId);
        if (partner == null) {
            player.sendMessage(Text.literal("Your partner is not online."));
            return;
        }

        player.teleport(partner.getServerWorld(), partner.getX(), partner.getY(), partner.getZ(), partner.getYaw(), partner.getPitch());
        player.sendMessage(Text.literal("Teleported to your partner!"));
    }

    public void divorce(ServerPlayerEntity player) {
        UUID partner = marriages.remove(player.getUuid());
        if (partner != null) {
            marriages.remove(partner);
            markDirtyAndSave();
            player.sendMessage(Text.literal("You are now divorced."));
            ServerPlayerEntity partnerPlayer = player.getServer().getPlayerManager().getPlayer(partner);
            if (partnerPlayer != null) {
                partnerPlayer.sendMessage(Text.literal(player.getName().getString() + " has divorced you."));
            }
        } else {
            player.sendMessage(Text.literal("You are not married."));
        }
    }

    public boolean isMarried(UUID uuid) {
        return marriages.containsKey(uuid);
    }

    public UUID getPartner(UUID uuid) {
        return marriages.get(uuid);
    }

    public String getPlayerName(UUID uuid) {
        ServerPlayerEntity player = ServerPlayerEntity.getServer().getPlayerManager().getPlayer(uuid);
        return player != null ? player.getName().getString() : "Offline";
    }
}
