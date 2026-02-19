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
	public static String MARRY_RECEIVED_PROPOSAL_EXPIRED;
	public static String MARRY_SENT_PROPOSAL_EXPIRED;
	public static String MARRY_PROPOSER_OFFLINE;
	public static String MARRY_TARGET_MUST_BE_INDIVIDUAL;
	public static String MARRY_TARGET_OFFLINE;
	public static String MARRY_DENY_SUCCESS;
	public static String MARRY_DENY_TARGET;
	public static String DIVORCE_SUCCESS;
	public static String DIVORCE_SPOUSE_NOTIFIED;
	public static String DIVORCE_FAILED;
	public static String MARRIAGE_STATUS_BADGE;
	public static String MARRIAGE_STATUS_HOVER;

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
		MARRY_RECEIVED_PROPOSAL_EXPIRED = get("marry_received_proposal_expired");
		MARRY_SENT_PROPOSAL_EXPIRED = get("marry_sent_proposal_expired");
		MARRY_PROPOSER_OFFLINE = get("marry_proposer_offline");
		MARRY_TARGET_MUST_BE_INDIVIDUAL = get("marry_target_must_be_individual");
		MARRY_TARGET_OFFLINE = get("marry_target_offline");
		MARRY_DENY_SUCCESS = get("marry_deny_success");
		MARRY_DENY_TARGET = get("marry_deny_target");
		DIVORCE_SUCCESS = get("divorce_success");
		DIVORCE_SPOUSE_NOTIFIED = get("divorce_spouse_notified");
		DIVORCE_FAILED = get("divorce_failed");
		MARRIAGE_STATUS_BADGE = get("marriage_status_badge");
		MARRIAGE_STATUS_HOVER = get("marriage_status_hover");
	}

	private static Map<String, String> createDefaultMessages() {
		Map<String, String> defaults = new LinkedHashMap<>();
		defaults.put("marry_no_permission", "<red>you do not have permission to marry other players.</red>");
		defaults.put("cannot_marry_self", "<red>you cannot marry yourself.</red>");
		defaults.put("already_married_self", "<red>you are already married.</red>");
		defaults.put("already_married_target", "<red>that player is already married.</red>");
		defaults.put("marry_failed", "<red>could not complete marriage.</red>");
		defaults.put("marry_success_sender", "<green>you are now married to <gold>%player%</gold>!</green>");
		defaults.put("marry_success_target", "<green><gold>%player%</gold> is now married to you!</green>");
		defaults.put("marry_proposal_sent", "<green>you proposed to <gold>%player%</gold>.</green>");
		defaults.put("marry_proposal_received", "<gold>%player%</gold><green> has proposed to you!<newline></green><click:run_command:'/marry accept'><hover:show_text:'<yellow>Accept proposal</yellow>'><bold><dark_green>[ACCEPT]</dark_green></bold></hover></click> <click:run_command:'/marry deny'><hover:show_text:'<yellow>Deny proposal</yellow>'><bold><red>[DENY]</red></bold></hover></click>");
		defaults.put("marry_no_pending_proposal", "<red>you do not have a pending proposal.</red>");
		defaults.put("marry_proposal_expired", "<red>that proposal is no longer valid.</red>");
		defaults.put("marry_received_proposal_expired", "<red><gold>%player%</gold>'s proposal to you has expired.</red>");
		defaults.put("marry_sent_proposal_expired", "<red>>your proposal to <gold>%player%</gold> has expired.</red>");
		defaults.put("marry_proposer_offline", "<red>the player who proposed is offline.</red>");
		defaults.put("marry_target_must_be_individual", "<red>please target exactly one player.</red>");
		defaults.put("marry_target_offline", "<red>that player is offline or unavailable.</red>");
		defaults.put("marry_deny_success", "<green>you have denied the marriage proposal.</green>");
		defaults.put("marry_deny_target", "<green>%player% has denied your marriage proposal.</green>");
		defaults.put("divorce_success", "<green>you are now divorced.</green>");
		defaults.put("divorce_spouse_notified", "<red><gold>%player%</gold> has divorced you.</red>");
		defaults.put("divorce_failed", "<red>could not complete divorce.</red>");
		defaults.put("marriage_status_badge", "<bold><black>[<red>❤</red>]</black></bold>");
		defaults.put("marriage_status_hover", "<aqua><!italic>Married to: %player%</!italic></aqua>");
		return defaults;
	}

}
