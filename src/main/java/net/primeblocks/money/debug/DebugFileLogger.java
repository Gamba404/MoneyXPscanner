package net.primeblocks.money.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import net.fabricmc.loader.api.FabricLoader;

import net.primeblocks.money.PrimeMoneyClient;
import net.primeblocks.money.io.BackgroundWriter;

/**
 * Appends to {@code config/primemoney-debug.log} - a file just for this mod, separate from
 * Minecraft's own log, so a debugging session can be captured and sent on as one small file
 * instead of having to fish "[primemoney]" lines out of a 100+-mod {@code latest.log}.
 *
 * <p>Truncated at the start of every client run, so it only ever holds the current session.
 *
 * <p>Lines are buffered and written out once a second rather than one file operation per line:
 * the job HUD ticks several times a second and every tick can produce a line, which would
 * otherwise mean a re-open, write and close on every one of them.
 */
public final class DebugFileLogger {
	private static final String FILE_NAME = "primemoney-debug.log";
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
	private static final long FLUSH_INTERVAL_MILLIS = 1000;

	/** Stops a runaway logger from eating memory if flushing ever fails. */
	private static final int MAX_BUFFERED_CHARS = 4_000_000;

	private static final StringBuilder BUFFER = new StringBuilder();

	private static Path path;
	private static boolean failed;
	private static long lastFlushMillis;

	/**
	 * Mirrors {@code PrimeMoneyConfig#debugLogAll}, kept here so the mixin in
	 * {@link net.primeblocks.money.mixin.HudOverlayMixin} - a static context with no config
	 * reference of its own - can check it without a hard dependency on the config class.
	 */
	public static volatile boolean verbose;

	private DebugFileLogger() {
	}

	/** Truncates the file for a fresh session. Call once from the entrypoint. */
	public static void start() {
		path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);

		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, "", StandardCharsets.UTF_8);
		} catch (IOException e) {
			failed = true;
			PrimeMoneyClient.LOGGER.warn("Konnte {} nicht anlegen", path, e);
		}
	}

	public static void log(String line) {
		if (failed || path == null) {
			return;
		}

		synchronized (BUFFER) {
			if (BUFFER.length() > MAX_BUFFERED_CHARS) {
				return;
			}

			BUFFER.append('[').append(LocalTime.now().format(TIME_FORMAT)).append("] ")
					.append(line).append(System.lineSeparator());
		}
	}

	public static void tick(long now) {
		if (now - lastFlushMillis >= FLUSH_INTERVAL_MILLIS) {
			flush();
		}
	}

	public static void flush() {
		if (failed || path == null) {
			return;
		}

		String pending;

		synchronized (BUFFER) {
			if (BUFFER.isEmpty()) {
				lastFlushMillis = System.currentTimeMillis();
				return;
			}

			pending = BUFFER.toString();
			BUFFER.setLength(0);
		}

		lastFlushMillis = System.currentTimeMillis();
		Path target = path;

		// Off the client thread: opening the file, appending and closing it once a second is small
		// on its own, but it is pure waiting, and it was waiting the render thread did.
		BackgroundWriter.submit(() -> {
			try {
				Files.writeString(target, pending, StandardCharsets.UTF_8,
						StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			} catch (IOException e) {
				failed = true;
				PrimeMoneyClient.LOGGER.warn("Konnte nicht in {} schreiben", target, e);
			}
		});
	}
}
