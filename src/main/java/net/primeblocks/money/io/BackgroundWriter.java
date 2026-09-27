package net.primeblocks.money.io;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.primeblocks.money.PrimeMoneyClient;

/**
 * Runs this mod's disk writes off the client thread.
 *
 * <p>Everything here used to happen inline: the 24-hour history is a six-figure JSON document that
 * was serialised and written every fifteen seconds, and again on every disconnect - which is also
 * what fires when hopping to the farm world or the plot world. So the heaviest write landed exactly
 * while the client was already busy tearing down one world and building the next, and it showed up
 * as a stutter at the worst possible moment.
 *
 * <p>One thread, in order, so writes to the same file cannot overtake each other. It is a daemon:
 * saved statistics are never worth holding the game open, and what still matters at shutdown is
 * handled by {@link #shutdown}.
 */
public final class BackgroundWriter {
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "PrimeMoney IO");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		return thread;
	});

	private BackgroundWriter() {
	}

	/**
	 * Queues a write. The caller must hand over data nobody else will touch again - snapshot on the
	 * client thread, serialise and write here.
	 */
	public static void submit(Runnable write) {
		try {
			EXECUTOR.execute(() -> {
				try {
					write.run();
				} catch (Throwable failure) {
					PrimeMoneyClient.LOGGER.warn("[primemoney] Hintergrund-Schreibvorgang fehlgeschlagen", failure);
				}
			});
		} catch (Throwable rejected) {
			// Shutting down already; losing the last few seconds of statistics is not worth a fuss.
			PrimeMoneyClient.LOGGER.debug("[primemoney] Schreibvorgang nicht mehr angenommen", rejected);
		}
	}

	/** Lets queued writes finish, briefly, so a quit does not lose the last minutes. */
	public static void shutdown() {
		EXECUTOR.shutdown();

		try {
			if (!EXECUTOR.awaitTermination(3, TimeUnit.SECONDS)) {
				PrimeMoneyClient.LOGGER.warn("[primemoney] Schreibvorgaenge liefen beim Beenden noch");
			}
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}
}
