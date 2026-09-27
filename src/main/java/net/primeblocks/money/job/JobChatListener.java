package net.primeblocks.money.job;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import net.minecraft.network.chat.Component;

import net.primeblocks.money.PrimeMoneyClient;
import net.primeblocks.money.PrimeMoneyConfig;
import net.primeblocks.money.debug.DebugFileLogger;
import net.primeblocks.money.debug.TextDump;

/**
 * Watches chat for {@code JOBS » Du hast … verdient.} payout lines and hands each one to
 * {@link JobHudTracker}.
 *
 * <p>A payout is the same money the live HUD counter was already filling up with, so the two are
 * not separate income to be added together - the tracker folds both into one running total per
 * job. That makes a payout worth reporting for two reasons: it states the amount exactly (payouts
 * overshoot the threshold a little, which the counter alone cannot see), and it still lands even
 * for a job whose HUD line is not currently on screen.
 */
public final class JobChatListener {
	private final PrimeMoneyConfig config;
	private final JobHudTracker hudTracker;

	private String compiledMoneyRegex;
	private String compiledJobRegex;
	private Pattern moneyPattern;
	private Pattern jobPattern;

	private String lastActionBarText;

	public JobChatListener(PrimeMoneyConfig config, JobHudTracker hudTracker) {
		this.config = config;
		this.hudTracker = hudTracker;
	}

	public void register() {
		// Both handlers run while a packet is being processed, so an exception escaping either one
		// is a packet handling error and drops the player off the server. Missing a payout is a far
		// smaller price than a disconnect, so nothing gets past these guards.
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			try {
				if (config.debugLogAll) {
					dumpRaw(overlay ? "GAME overlay=true" : "GAME overlay=false", message);
				}

				if (overlay) {
					debugLogActionBar(message);
				}

				handle(message);
			} catch (Throwable failure) {
				PrimeMoneyClient.LOGGER.error("[primemoney] Fehler beim Auswerten einer Nachricht", failure);
			}

			return true;
		});

		ClientReceiveMessageEvents.ALLOW_CHAT.register(
				(message, signedMessage, sender, params, receptionTimestamp) -> {
					try {
						if (config.debugLogAll) {
							dumpRaw("CHAT", message);
						}

						handle(message);
					} catch (Throwable failure) {
						PrimeMoneyClient.LOGGER.error("[primemoney] Fehler beim Auswerten einer Chatnachricht", failure);
					}

					return true;
				});
	}

	private void dumpRaw(String channel, Component message) {
		String text = message.getString();

		if (text == null || text.isEmpty()) {
			return;
		}

		DebugFileLogger.log("[" + channel + "] " + TextDump.escapeNonAscii(text));
	}

	private void debugLogActionBar(Component message) {
		if (!config.debugLogActionBar) {
			return;
		}

		String text = message.getString();

		if (text == null || text.isEmpty() || text.equals(lastActionBarText)) {
			return;
		}

		lastActionBarText = text;
		debug("[primemoney] Actionbar: " + text);
	}

	private void handle(Component message) {
		String text = message.getString();

		if (text == null || text.isEmpty()) {
			return;
		}

		boolean looksLikeJobs = containsJobsMarker(text);

		if (config.requireJobsPrefix && !looksLikeJobs) {
			return;
		}

		ensurePatterns();

		Double money = moneyPattern != null ? extractAmount(moneyPattern, text) : null;
		String jobLabel = jobPattern != null ? extractGroup(jobPattern, text, "job") : null;

		if (money == null) {
			if (config.debugLogUnmatched && looksLikeJobs) {
				debug("[primemoney] JOBS-Nachricht nicht erkannt: " + text);
			}

			return;
		}

		// The credited amount and its logging happen in the tracker, which is the only place that
		// knows how much of this payout the live counter had already accounted for.
		hudTracker.onPayout(jobLabel, money, System.currentTimeMillis());
	}

	/** Writes to both the normal mod log and the dedicated debug file - see {@link DebugFileLogger}. */
	private void debug(String message) {
		PrimeMoneyClient.LOGGER.info(message);
		DebugFileLogger.log(message);
	}

	/**
	 * Loose on purpose: the arrow glyph the server uses is a resource-pack font character, not the
	 * plain ASCII "»" it looks like, so only the literal word "JOBS" is required here.
	 */
	private boolean containsJobsMarker(String text) {
		return text.toUpperCase(Locale.ROOT).contains("JOBS");
	}

	private Double extractAmount(Pattern pattern, String text) {
		String raw = extractGroup(pattern, text, "amount");
		return raw != null ? NumberParser.parse(raw) : null;
	}

	private String extractGroup(Pattern pattern, String text, String group) {
		Matcher matcher = pattern.matcher(text);

		if (!matcher.find()) {
			return null;
		}

		try {
			return matcher.group(group);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Recompiles a pattern only when the configured regex string actually changed. */
	private void ensurePatterns() {
		if (!config.moneyRegex.equals(compiledMoneyRegex)) {
			compiledMoneyRegex = config.moneyRegex;
			moneyPattern = compile(compiledMoneyRegex);
		}

		if (!config.jobRegex.equals(compiledJobRegex)) {
			compiledJobRegex = config.jobRegex;
			jobPattern = compile(compiledJobRegex);
		}
	}

	private Pattern compile(String regex) {
		if (regex == null || regex.isBlank()) {
			return null;
		}

		try {
			return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		} catch (PatternSyntaxException e) {
			PrimeMoneyClient.LOGGER.warn("[primemoney] Ungueltiges Regex in der Config: {}", regex, e);
			return null;
		}
	}
}
