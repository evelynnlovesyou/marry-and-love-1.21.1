package io.github.evelynnlovesyou.marryandlove.manager;

import io.github.evelynnlovesyou.marryandlove.MarryAndLove;
import net.minecraft.server.level.ServerPlayer;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionManager {
	public static final String MARRY_COMMAND_PERMISSION = (MarryAndLove.MOD_ID+".command.marry");
    public static final String DIVORCE_COMMAND_PERMISSION = (MarryAndLove.MOD_ID+".command.divorce");
    private static final Logger LOGGER = LoggerFactory.getLogger(MarryAndLove.MOD_ID);
    private static volatile LuckPerms luckPermsApi;
    private static volatile boolean luckPermsMissing = false;
    private static volatile boolean luckPermsWarned = false;

	public static boolean canUseMarryCommand(ServerPlayer player) {
		return hasPermission(player, MARRY_COMMAND_PERMISSION);
	}

    public static void init() {
        initLuckPerms();
    }

    private static synchronized void initLuckPerms() {
        if (luckPermsApi != null || luckPermsMissing) {
            return;
        }

        try {
            luckPermsApi = LuckPermsProvider.get();
            LOGGER.info("Successfully detected and initialised LuckPerms API");
        } catch (NoClassDefFoundError ignored) {
            luckPermsMissing = true;
            if (!luckPermsWarned) {
                LOGGER.warn("LuckPerms not detected - using fallback permission system (OP only)");
                luckPermsWarned = true;
            }
        } catch (Exception ignored) {
            if (!luckPermsWarned) {
                LOGGER.warn("LuckPerms unavailable - will retry when available");
                luckPermsWarned = true;
            }
        }
    }
	public static boolean hasPermission(ServerPlayer player, String permission) {
        initLuckPerms();
        if (luckPermsApi != null) {
            try {
                User user = luckPermsApi.getUserManager().getUser(player.getGameProfile().getId());
                if (user != null && user.getCachedData().getPermissionData()
                        .checkPermission(permission).asBoolean()) {
                    return true;
                }
            } catch (Exception ignored) {
                // Fallback to OP
            }
        }
        return player.server != null && player.server.getPlayerList().isOp(player.getGameProfile());
    }
}
