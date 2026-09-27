package net.primeblocks.money.job;

import java.util.function.Consumer;

import net.primeblocks.money.PrimeMoneyClient;

/**
 * Carries overlay (action bar) text from the mixin that intercepts it to whoever wants to read it.
 *
 * <p>A mixin runs inside a Minecraft class and has no reference to the mod's own objects, so the
 * handover has to go through something static. Keeping that one static thing here - rather than
 * letting the mixin reach into the tracker directly - means the mixin stays a dumb tap and all the
 * parsing logic lives in ordinary, testable code.
 *
 * <p>This is also the boundary where nothing may escape. The tap sits inside
 * {@code ClientPacketListener.setActionBarText}, so an exception thrown here does not merely lose a
 * reading - it becomes a packet handling error and drops the player off the server. Reading a
 * number off a status line is never worth a disconnect, so every failure stops at this line.
 */
public final class OverlayMessageBus {
	/** Enough to notice a broken parser in the log without flooding it at ten readings a second. */
	private static final long ERROR_LOG_INTERVAL_MILLIS = 10_000;

	private static volatile Consumer<String> listener;
	private static long lastErrorLogMillis;
	private static int suppressedErrors;

	private OverlayMessageBus() {
	}

	public static void setListener(Consumer<String> listener) {
		OverlayMessageBus.listener = listener;
	}

	public static void publish(String text) {
		Consumer<String> current = listener;

		if (current == null || text == null || text.isEmpty()) {
			return;
		}

		try {
			current.accept(text);
		} catch (Throwable failure) {
			reportFailure(text, failure);
		}
	}

	private static void reportFailure(String text, Throwable failure) {
		long now = System.currentTimeMillis();

		if (now - lastErrorLogMillis < ERROR_LOG_INTERVAL_MILLIS) {
			suppressedErrors++;
			return;
		}

		int alsoFailed = suppressedErrors;
		suppressedErrors = 0;
		lastErrorLogMillis = now;

		PrimeMoneyClient.LOGGER.error(
				"[primemoney] Konnte die Job-Leiste nicht auswerten (Tracking laeuft weiter{}): {}",
				alsoFailed > 0 ? ", " + alsoFailed + " weitere unterdrueckt" : "", text, failure);
	}
}
