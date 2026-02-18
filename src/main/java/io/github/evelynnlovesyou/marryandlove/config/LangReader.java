package io.github.evelynnlovesyou.marryandlove.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import io.github.evelynnlovesyou.marryandlove.MarryAndLove;
import net.fabricmc.loader.api.FabricLoader;

public class LangReader {
	public static String MARRY_NO_PERMISSION;
	public static String CANNOT_MARRY_SELF;
	public static String ALREADY_MARRIED_SELF;
	public static String ALREADY_MARRIED_TARGET;
	public static String MARRY_FAILED;
	public static String MARRY_SUCCESS_SENDER;
	public static String MARRY_SUCCESS_TARGET;
	public static String MARRY_PROPOSAL_SENT;
	public static String MARRY_PROPOSAL_RECEIVED;
	public static String MARRY_NO_PENDING_PROPOSAL;
	public static String MARRY_PROPOSAL_EXPIRED;
	public static String MARRY_PROPOSER_OFFLINE;
	public static String MARRY_TARGET_MUST_BE_INDIVIDUAL;
	public static String MARRY_TARGET_OFFLINE;
	public static String MARRY_DENY_SUCCESS;
	public static String MARRY_DENY_TARGET;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, String> DEFAULT_MESSAGES = createDefaultMessages();
	private static final Map<String, String> MESSAGES = new LinkedHashMap<>();

	private static final Path LANG_FILE_PATH = FabricLoader.getInstance()
			.getConfigDir()
			.resolve("marryandlove")
			.resolve("lang.json");

	public static void init() {
		try {
			ensureLangFileExists();
			loadMessages();
		} catch (IOException exception) {
			MESSAGES.clear();
			MESSAGES.putAll(DEFAULT_MESSAGES);
			syncPublicFields();
			MarryAndLove.LOGGER.error("Failed to load lang.json, it either doesn't exist or is corrupted - using defaults.", exception);
		}
	}

	public static String get(String key) {
		return MESSAGES.getOrDefault(key, DEFAULT_MESSAGES.getOrDefault(key, key));
	}

	public static String format(String key, Object... args) {
		return String.format(get(key), args);
	}

	private static void ensureLangFileExists() throws IOException {
		Path parentDir = LANG_FILE_PATH.getParent();
		if (parentDir != null) {
			Files.createDirectories(parentDir);
		}

		if (!Files.exists(LANG_FILE_PATH)) {
			try (Writer writer = Files.newBufferedWriter(LANG_FILE_PATH)) {
				GSON.toJson(DEFAULT_MESSAGES, writer);
			}
		}
	}

	private static void loadMessages() throws IOException {
		Map<String, String> loadedMessages;
		try (Reader reader = Files.newBufferedReader(LANG_FILE_PATH)) {
			loadedMessages = GSON.fromJson(reader, new TypeToken<LinkedHashMap<String, String>>() {}.getType());
		}

		if (loadedMessages == null) {
			loadedMessages = new LinkedHashMap<>();
		}

		MESSAGES.clear();
		MESSAGES.putAll(DEFAULT_MESSAGES);
		MESSAGES.putAll(loadedMessages);
		syncPublicFields();

		if (!loadedMessages.keySet().containsAll(DEFAULT_MESSAGES.keySet())) {
			try (Writer writer = Files.newBufferedWriter(LANG_FILE_PATH)) {
				GSON.toJson(MESSAGES, writer);
			}
		}
	}

	private static void syncPublicFields() {
		MARRY_NO_PERMISSION = get("marry_no_permission");
		CANNOT_MARRY_SELF = get("cannot_marry_self");
		ALREADY_MARRIED_SELF = get("already_married_self");
		ALREADY_MARRIED_TARGET = get("already_married_target");
		MARRY_FAILED = get("marry_failed");
		MARRY_SUCCESS_SENDER = get("marry_success_sender");
		MARRY_SUCCESS_TARGET = get("marry_success_target");
		MARRY_PROPOSAL_SENT = get("marry_proposal_sent");
		MARRY_PROPOSAL_RECEIVED = get("marry_proposal_received");
		MARRY_NO_PENDING_PROPOSAL = get("marry_no_pending_proposal");
		MARRY_PROPOSAL_EXPIRED = get("marry_proposal_expired");
		MARRY_PROPOSER_OFFLINE = get("marry_proposer_offline");
		MARRY_TARGET_MUST_BE_INDIVIDUAL = get("marry_target_must_be_individual");
		MARRY_TARGET_OFFLINE = get("marry_target_offline");
		MARRY_DENY_SUCCESS = get("marry_deny_success");
		MARRY_DENY_TARGET = get("marry_deny_target");
	}

	private static Map<String, String> createDefaultMessages() {
		Map<String, String> defaults = new LinkedHashMap<>();
		defaults.put("marry_no_permission", "&cyou do not have permission to marry other players.");
		defaults.put("cannot_marry_self", "&cyou cannot marry yourself.");
		defaults.put("already_married_self", "&cyou are already married.");
		defaults.put("already_married_target", "&cthat player is already married.");
		defaults.put("marry_failed", "&ccould not complete marriage.");
		defaults.put("marry_success_sender", "&ayou are now married to %player%!");
		defaults.put("marry_success_target", "&a%player% is now married to you!");
		defaults.put("marry_proposal_sent", "&ayou proposed to %player%.");
		defaults.put("marry_proposal_received", "&a%player% has proposed to you. Use /marry accept to respond.");
		defaults.put("marry_no_pending_proposal", "&cyou do not have a pending proposal.");
		defaults.put("marry_proposal_expired", "&cthat proposal is no longer valid.");
		defaults.put("marry_proposer_offline", "&cthe player who proposed is offline.");
		defaults.put("marry_target_must_be_individual", "&cplease target exactly one player.");
		defaults.put("marry_target_offline", "&cthat player is offline or unavailable.");
		defaults.put ("marry_deny_success", "&ayou have denied the marriage proposal.");
		defaults.put("marry_deny_target", "&a%player% has denied your marriage proposal.");
		return defaults;
	}

}
