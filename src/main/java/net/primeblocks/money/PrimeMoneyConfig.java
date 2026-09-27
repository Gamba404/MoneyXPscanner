package net.primeblocks.money;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import net.fabricmc.loader.api.FabricLoader;

import net.primeblocks.money.io.BackgroundWriter;

/**
 * User settings, stored at {@code config/primemoney.json}.
 *
 * <p>All three patterns are confirmed against real PrimeBlocks output and can be edited here
 * directly - no rebuild needed - should the server ever change its wording.
 */
public final class PrimeMoneyConfig {
	private static final String FILE_NAME = "primemoney.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Sentinel for {@link #hudX}: the overlay is centered horizontally instead of edge-anchored. */
	public static final int CENTERED = Integer.MIN_VALUE;

	public static final double MIN_SCALE = 0.5;
	public static final double MAX_SCALE = 3.0;
	public static final double SCALE_STEP = 0.1;

	/** Master switch for the overlay. */
	public boolean hudEnabled = true;

	/** HUD anchor in scaled GUI pixels. {@link #CENTERED} centers it; negative anchors to the right edge. */
	public int hudX = CENTERED;
	public int hudY = 20;

	public double hudScale = 1.0;

	/** Alpha of the panel behind the text, 0..255. Zero draws no panel at all. */
	public int backgroundOpacity = 144;

	/** The block covering all jobs together: session total, projection, 60min and 24h. */
	public boolean showCombined = true;

	/** One line per job currently earning, with that job's own rate and level estimate. */
	public boolean showPerJob = true;

	/**
	 * Estimate how long until a job levels up, from the XP it still needs and the XP-per-hour that
	 * same job is running at. Part of the per-job lines.
	 */
	public boolean showLevelEta = true;

	/** How long a job keeps its line on the HUD after its last earning. */
	public int perJobActiveSeconds = 120;

	/**
	 * Jobs whose earnings are ignored, by display name ("Jäger", "Taucher", ...). Stored as the
	 * exclusion rather than the inclusion so the empty default means "count everything" - and so a
	 * job this mod does not know about still gets counted instead of silently vanishing.
	 */
	public List<String> disabledJobs = new ArrayList<>();

	/**
	 * Parses PrimeBlocks' live job HUD - the line above the hotbar shown while working a job.
	 * Confirmed live: {@code Farmer » 340,896.16/365,000 [xp] | 8,378.14/10,000 [coin]}. The
	 * numbers after each slash are captured only so a wrap (level-up, payout) can be measured;
	 * what is tracked is how the numbers <em>before</em> them move. See
	 * {@link net.primeblocks.money.job.JobHudTracker}.
	 *
	 * <p>The coin half is optional. Fisherman pays nothing per catch - fish are items, worth coins
	 * only once sold to the trader - so that job has no payout counter to show, and demanding one
	 * would leave its XP untracked as well.
	 */
	public String hudRegex = "^(?<job>[^»|/]{1,24}?)\\s*»\\s*(?<xp>[0-9][0-9.,]*)\\s*/\\s*(?<xpmax>[0-9][0-9.,]*)"
			+ "(?:[^|]*\\|\\s*(?<money>[0-9][0-9.,]*)\\s*/\\s*(?<moneymax>[0-9][0-9.,]*))?";

	/**
	 * The window the shown projection prefers - short, so it follows a change of pace quickly and
	 * really answers "at the rate I am going right now".
	 */
	public int rateShortWindowMinutes = 5;

	/**
	 * The fallback and reference window. Steadier, but slow to notice that you switched to a
	 * faster farm.
	 */
	public int rateLongWindowMinutes = 60;

	/**
	 * How many readings carrying earnings the short window needs before it is trusted on its own.
	 *
	 * <p>A projection is a sum of increments, so its sampling error runs at roughly 1/sqrt(n).
	 * While a job's bar is on screen its readings arrive ~14 times a minute, so five minutes holds
	 * ~70 of them - about 12% error, fine. With the bar not showing, the same five minutes holds
	 * only the ~9 payouts, at about 34% - far too shaky to show as "your rate". Below this count
	 * the long window is used instead.
	 */
	public int rateMinEvents = 30;

	/**
	 * How readily the projection abandons the long window when the recent one disagrees. The two
	 * carry equal weight once their gap reaches {@code sqrt(this)} times the noise either of them
	 * has; lower follows a change of pace sooner but reacts to more chance wobble.
	 */
	public double rateBlendSensitivity = 4.0;

	/**
	 * How far ahead the projection is marked against reality - it is filed away, and this much
	 * farming later the same stretch is measured for real and the two compared.
	 */
	public int accuracyHorizonMinutes = 10;

	/**
	 * Show the long window's average on a second line, to put the current pace in context. Off by
	 * default: one per-hour figure is what the projection is for.
	 */
	public boolean showRateReference = false;

	/**
	 * A gap between earnings longer than this counts as a break and contributes no active time,
	 * which is what keeps the projection meaning "per hour of farming" instead of "per hour of
	 * being logged in". Anything shorter counts in full.
	 *
	 * <p>Deliberately generous, because earnings do not arrive smoothly: a payout lands only every
	 * 20-40 seconds, and while several jobs share the screen one job's own readings can be minutes
	 * apart. Set it too low and ordinary quiet stretches get mistaken for breaks - which divides by
	 * too little time and reports a rate several times too high.
	 */
	public int ratePauseSeconds = 120;

	/**
	 * Minimum active time before a projection is shown at all, so a single payout cannot
	 * extrapolate into an absurd number.
	 */
	public int rateMinActiveSeconds = 60;

	/**
	 * How much earning is lumped into one stored history entry. The HUD ticks many times a second;
	 * one entry per tick would mean hundreds of thousands of them per day on disk for no more
	 * accuracy in the 60min/24h totals.
	 */
	public int historyBucketSeconds = 30;

	/**
	 * Matched against every chat line that contains "JOBS". The named group {@code amount}
	 * captures the coin amount gained. Confirmed live: {@code JOBS » Du hast 100,008.70[coin]
	 * als Farmer verdient.} - note the amount uses English-style grouping (comma) with a dot as
	 * the decimal point, handled by {@link net.primeblocks.money.job.NumberParser}. Used to size
	 * the money wrap exactly (payouts overshoot the threshold a little) and as the fallback when
	 * the live HUD is unavailable.
	 */
	public String moneyRegex = "JOBS.{0,4}Du hast\\s+(?<amount>[0-9][0-9.,]*)";

	/**
	 * Matched against the same line to pick out which job it was for (named group {@code job}).
	 * Confirmed live: {@code ... als Farmer verdient.}. If it does not match, the mod keeps
	 * showing the last job it did see. Leave empty to disable and always keep the last known job.
	 */
	public String jobRegex = "\\bals\\s+(?<job>\\S+)\\s+verdient\\b";

	/** Only chat lines that contain "JOBS" are tested against the regexes above. */
	public boolean requireJobsPrefix = true;

	/**
	 * Logs unmatched "JOBS" lines so the regexes above can be corrected from real examples. Off by
	 * default now that both regexes are confirmed against real PrimeBlocks output; turn back on if
	 * the server ever changes its wording and money tracking silently stops.
	 */
	public boolean debugLogUnmatched = false;

	/**
	 * Logs every credited amount with its source (live HUD reading vs. payout message), plus a
	 * periodic snapshot of the computed rates and the active time behind them, to
	 * {@code config/primemoney-debug.log}. This is how every accounting bug so far was actually
	 * found and proven fixed - turn it back on (and send the log) if a number ever looks wrong
	 * again. Off by default: it is a write every second on top of the ones the tracking already
	 * does, worth paying only while something is actually being debugged.
	 */
	public boolean debugLogTracking = false;

	/** How often the snapshot above is written. Zero turns just the snapshots off. */
	public int debugSnapshotSeconds = 30;

	/** Logs the actionbar text every time it changes. */
	public boolean debugLogActionBar = false;

	/**
	 * Dumps every chat/overlay message PrimeMoney sees - unconditionally, no filter - to
	 * {@code config/primemoney-debug.log}, unicode-escaped so an unusual glyph shows up as a code
	 * point instead of vanishing. The blunt tool for when the server changes its wording and the
	 * patterns above stop matching. Noisy, hence off by default.
	 */
	public boolean debugLogAll = false;

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static PrimeMoneyConfig load() {
		Path file = path();

		if (!Files.exists(file)) {
			PrimeMoneyConfig config = new PrimeMoneyConfig();
			config.save();
			return config;
		}

		try {
			PrimeMoneyConfig config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
					PrimeMoneyConfig.class);
			return config != null ? config : new PrimeMoneyConfig();
		} catch (IOException | JsonSyntaxException e) {
			PrimeMoneyClient.LOGGER.warn("Konnte {} nicht lesen, benutze Standardwerte", file, e);
			return new PrimeMoneyConfig();
		}
	}

	/**
	 * Saves the settings.
	 *
	 * <p>Serialised here, where the values are known to be still - this object is edited from the
	 * settings screen - and written on the background thread, since the caller is usually a button
	 * press or a keystroke that should not wait on a disk.
	 */
	public void save() {
		String json = GSON.toJson(this);
		Path file = path();

		BackgroundWriter.submit(() -> {
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, json, StandardCharsets.UTF_8);
			} catch (IOException e) {
				PrimeMoneyClient.LOGGER.warn("Konnte {} nicht schreiben", file, e);
			}
		});
	}

	public float scale() {
		return (float) Math.clamp(hudScale, MIN_SCALE, MAX_SCALE);
	}

	public int backgroundColour() {
		return Math.clamp(backgroundOpacity, 0, 255) << 24;
	}

	public long rateShortWindowMillis() {
		return Math.max(1, rateShortWindowMinutes) * 60_000L;
	}

	public long rateLongWindowMillis() {
		return Math.max(rateShortWindowMinutes + 1, rateLongWindowMinutes) * 60_000L;
	}

	public long accuracyHorizonMillis() {
		return Math.max(1, accuracyHorizonMinutes) * 60_000L;
	}

	public long ratePauseMillis() {
		return Math.max(1, ratePauseSeconds) * 1000L;
	}

	public long rateMinActiveMillis() {
		return Math.max(1, rateMinActiveSeconds) * 1000L;
	}

	public long historyBucketMillis() {
		return Math.max(1, historyBucketSeconds) * 1000L;
	}

	public long perJobActiveMillis() {
		return Math.max(1, perJobActiveSeconds) * 1000L;
	}

	/**
	 * Whether a job's earnings count. An unrecognised or missing label counts: losing money to a
	 * label this mod failed to match would be a far worse failure than counting one job too many.
	 */
	public boolean isJobEnabled(String label) {
		if (label == null || label.isBlank() || disabledJobs == null) {
			return true;
		}

		String normalized = label.trim().toLowerCase(Locale.ROOT);

		for (String disabled : disabledJobs) {
			if (disabled != null && disabled.trim().toLowerCase(Locale.ROOT).equals(normalized)) {
				return false;
			}
		}

		return true;
	}

	public void setJobEnabled(String label, boolean enabled) {
		if (label == null || label.isBlank()) {
			return;
		}

		if (disabledJobs == null) {
			disabledJobs = new ArrayList<>();
		}

		String normalized = label.trim().toLowerCase(Locale.ROOT);
		disabledJobs.removeIf(entry -> entry == null
				|| entry.trim().toLowerCase(Locale.ROOT).equals(normalized));

		if (!enabled) {
			disabledJobs.add(label);
		}
	}
}
