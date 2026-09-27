package net.primeblocks.money.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.primeblocks.money.PrimeMoneyConfig;
import net.primeblocks.money.job.JobStats;
import net.primeblocks.money.job.JobStatsRegistry;
import net.primeblocks.money.tracking.EarningsHistory;
import net.primeblocks.money.tracking.LiveRateMeter;
import net.primeblocks.money.tracking.RateEstimator;

/**
 * Builds and draws the overlay. Shared by the live HUD element and the drag-to-position editor so
 * both show exactly the same box.
 */
public final class MoneyXpOverlayRenderer {
	static final int COLOUR_TITLE = 0xFFFFD966;
	private static final int COLOUR_LABEL = 0xFFE8E8E8;
	private static final int COLOUR_MONEY = 0xFF9BD9A0;
	private static final int COLOUR_XP = 0xFF8FC7E8;
	private static final int COLOUR_MUTED = 0xFF9A9A9A;
	private static final int COLOUR_JOB = 0xFFD9B3F0;
	private static final int LINE_SPACING = 2;
	private static final int PADDING = 5;

	/** Long enough to save the repeated work, short enough that nobody sees the lag. */
	private static final long CACHE_MILLIS = 150;

	private final JobStatsRegistry registry;
	private final EarningsHistory history;
	private final PrimeMoneyConfig config;

	private List<HudLine> cachedLines;
	private long cachedAtMillis;

	public MoneyXpOverlayRenderer(JobStatsRegistry registry, EarningsHistory history,
			PrimeMoneyConfig config) {
		this.registry = registry;
		this.history = history;
		this.config = config;
	}

	/**
	 * The lines to draw. Always at least a title line - the overlay's visibility is controlled
	 * purely by the F9 toggle, not by whether anything just happened, so it never has "nothing to
	 * draw" while enabled.
	 *
	 * <p>Cached briefly: the figures are rebuilt from every sample in the window and every stored
	 * event of the last 24 hours, which at frame rate is thousands of times more often than either
	 * the data or a reading human changes.
	 */
	public List<HudLine> buildLines() {
		long now = System.currentTimeMillis();

		if (cachedLines != null && now - cachedAtMillis < CACHE_MILLIS) {
			return cachedLines;
		}

		cachedLines = computeLines(now);
		cachedAtMillis = now;
		return cachedLines;
	}

	/** Drops the cache so a settings change shows up on the very next frame. */
	public void invalidate() {
		cachedLines = null;
	}

	private List<HudLine> computeLines(long now) {
		List<HudLine> lines = new ArrayList<>();
		List<JobStats> active = registry.activeJobs(now, config.perJobActiveMillis());

		lines.add(new HudLine(title(active), COLOUR_TITLE));

		if (!registry.hasData() && !history.hasAnyData()) {
			lines.add(new HudLine("Warte auf den ersten Job-Fortschritt...", COLOUR_MUTED));
			return lines;
		}

		RateEstimator.Projection projection =
				RateEstimator.estimate(registry.combined().rateMeter(), config, now);

		if (config.showCombined) {
			appendCombined(lines, now, projection);
		}

		if (config.showPerJob) {
			appendPerJob(lines, now, active, projection);
		}

		return lines;
	}

	private static String minutes(long millis) {
		return Math.max(1, Math.round(millis / 60_000.0)) + " Min";
	}

	/** Names the single active job when there is one, otherwise stays generic. */
	private String title(List<JobStats> active) {
		if (active.size() == 1) {
			return active.get(0).label();
		}

		if (active.size() > 1) {
			return "PrimeMoney — " + active.size() + " Jobs";
		}

		JobStats recent = registry.mostRecent();
		return recent != null ? recent.label() : "PrimeMoney";
	}

	private void appendCombined(List<HudLine> lines, long now, RateEstimator.Projection projection) {
		JobStats combined = registry.combined();

		lines.add(new HudLine(
				"Seit Login (" + AmountFormat.duration(combined.sessionElapsedMillis(now)) + "): "
						+ AmountFormat.compact(combined.sessionMoney()) + " Münzen · "
						+ AmountFormat.compact(combined.sessionXp()) + " XP",
				COLOUR_LABEL));

		// No window in the label. It is a per-hour figure either way, and naming a window it was
		// derived from reads like the number itself covers that span.
		if (projection != null) {
			lines.add(new HudLine(
					"Hochrechnung: " + AmountFormat.compact(projection.moneyPerHour()) + " Münzen/h · "
							+ AmountFormat.compact(projection.xpPerHour()) + " XP/h",
					COLOUR_MONEY));
		} else {
			lines.add(new HudLine("Hochrechnung: sammle Daten...", COLOUR_MUTED));
		}

		// Off by default - one per-hour number is the point; the longer average is for whoever
		// wants to compare their current pace against it.
		if (config.showRateReference && projection != null && projection.longRate() != null) {
			LiveRateMeter.Rate longRate = projection.longRate();
			lines.add(new HudLine(
					"Schnitt (" + minutes(longRate.windowMillis()) + "): "
							+ AmountFormat.compact(longRate.moneyPerHour()) + " Münzen/h · "
							+ AmountFormat.compact(longRate.xpPerHour()) + " XP/h",
					COLOUR_MUTED));
		}

		// Named only once a CB is actually known - these totals are kept separate per CB (see
		// EarningsHistory), so the tag says which one is being shown right now.
		String cbTag = cbTag();

		EarningsHistory.Sum sum60 = history.hourSum(now);
		lines.add(new HudLine(
				"Letzte 60 Min" + cbTag + ": " + AmountFormat.compact(sum60.money()) + " Münzen · "
						+ AmountFormat.compact(sum60.xp()) + " XP",
				COLOUR_XP));

		EarningsHistory.Sum sum24 = history.daySum(now);
		lines.add(new HudLine(
				"Letzte 24 Std" + cbTag + ": " + AmountFormat.compact(sum24.money()) + " Münzen · "
						+ AmountFormat.compact(sum24.xp()) + " XP",
				COLOUR_XP));
	}

	private String cbTag() {
		String cb = history.activeCityBuild();
		return cb.isEmpty() ? "" : " (" + cb.toUpperCase(Locale.ROOT) + ")";
	}

	/**
	 * One line per job currently earning. Every job keeps its own counters, so two jobs paying out
	 * side by side stay separate - but their rates share the combined active time, so the lines
	 * read as a breakdown of the total rather than as figures that contradict it.
	 */
	private void appendPerJob(List<HudLine> lines, long now, List<JobStats> active,
			RateEstimator.Projection combined) {
		for (JobStats stats : active) {
			StringBuilder line = new StringBuilder(stats.label());
			line.append(": ");

			// Fischer pays nothing per catch - its fish are items until sold - so its coin figures
			// would be a row of zeroes that reads like the tracking broke. Left out entirely.
			if (stats.earnsMoney()) {
				line.append(AmountFormat.compact(stats.sessionMoney())).append(" Münzen");
			}

			// Same windows, same active times and same weighting as the combined figure, so the job
			// lines stay a breakdown of it rather than separately-measured numbers.
			RateEstimator.Projection rate = RateEstimator.forJob(stats.rateMeter(), combined, now, config);

			if (rate != null) {
				if (stats.earnsMoney()) {
					line.append(" · ").append(AmountFormat.compact(rate.moneyPerHour())).append("/h · ");
				}

				line.append(AmountFormat.compact(rate.xpPerHour())).append(" XP/h");
			}

			if (config.showLevelEta) {
				appendLevelEta(line, stats, rate);
			}

			lines.add(new HudLine(line.toString(), COLOUR_JOB));
		}
	}

	/**
	 * How long this job's next level-up is away: the XP its HUD line says is still missing, over
	 * the XP-per-hour that same job is contributing.
	 *
	 * <p>Nothing is shown for a job at the level cap - there the counter keeps climbing with no
	 * next level to reach, so {@link JobStats#xpToNextLevel()} has no answer to give.
	 */
	private void appendLevelEta(StringBuilder line, JobStats stats, RateEstimator.Projection rate) {
		Double remaining = stats.xpToNextLevel();

		if (remaining == null) {
			return;
		}

		if (rate == null || rate.xpPerHour() <= 0) {
			line.append(" · noch ").append(AmountFormat.compact(remaining)).append(" XP");
			return;
		}

		long etaMillis = Math.round(remaining / rate.xpPerHour() * 3_600_000.0);
		line.append(" · Lvl in ").append(AmountFormat.eta(etaMillis));
	}

	public static int boxWidth(Font font, List<HudLine> lines) {
		int content = 0;

		for (HudLine line : lines) {
			content = Math.max(content, font.width(line.text()));
		}

		return content + PADDING * 2;
	}

	public static int boxHeight(Font font, List<HudLine> lines) {
		int lineHeight = font.lineHeight + LINE_SPACING;
		return lines.size() * lineHeight - LINE_SPACING + PADDING * 2;
	}

	public static int scaledWidth(Font font, List<HudLine> lines, float scale) {
		return Math.round(boxWidth(font, lines) * scale);
	}

	public static int scaledHeight(Font font, List<HudLine> lines, float scale) {
		return Math.round(boxHeight(font, lines) * scale);
	}

	/** Resolves the stored anchor into a pixel position; negative values anchor to the far edge. */
	public static int resolveX(PrimeMoneyConfig config, int guiWidth, int boxWidth) {
		if (config.hudX == PrimeMoneyConfig.CENTERED) {
			return (guiWidth - boxWidth) / 2;
		}

		return config.hudX >= 0 ? config.hudX : guiWidth + config.hudX - boxWidth;
	}

	public static int resolveY(PrimeMoneyConfig config, int guiHeight, int boxHeight) {
		return config.hudY >= 0 ? config.hudY : guiHeight + config.hudY - boxHeight;
	}

	/**
	 * Draws the overlay with its top-left corner at {@code boxX}/{@code boxY}.
	 *
	 * @param backgroundColour ARGB panel colour; fully transparent alpha skips the panel
	 */
	public void draw(GuiGraphicsExtractor context, Font font, List<HudLine> lines,
			int boxX, int boxY, float scale, int backgroundColour) {
		boolean scaled = Math.abs(scale - 1.0f) > 1e-4f;

		if (scaled) {
			context.pose().pushMatrix();
			context.pose().translate(boxX, boxY);
			context.pose().scale(scale);
		}

		int originX = scaled ? 0 : boxX;
		int originY = scaled ? 0 : boxY;

		if ((backgroundColour >>> 24) != 0) {
			context.fill(originX, originY,
					originX + boxWidth(font, lines), originY + boxHeight(font, lines),
					backgroundColour);
		}

		int lineHeight = font.lineHeight + LINE_SPACING;
		int boxWidth = boxWidth(font, lines);
		int textY = originY + PADDING;

		for (HudLine line : lines) {
			int textX = originX + (boxWidth - font.width(line.text())) / 2;
			context.text(font, line.text(), textX, textY, line.color());
			textY += lineHeight;
		}

		if (scaled) {
			context.pose().popMatrix();
		}
	}
}
