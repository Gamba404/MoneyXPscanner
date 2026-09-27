package net.primeblocks.money.debug;

import java.util.List;
import java.util.Locale;

import net.primeblocks.money.PrimeMoneyConfig;
import net.primeblocks.money.job.JobStats;
import net.primeblocks.money.job.JobStatsRegistry;
import net.primeblocks.money.tracking.LiveRateMeter;
import net.primeblocks.money.tracking.ProjectionAccuracy;
import net.primeblocks.money.tracking.RateEstimator;

/**
 * Writes what the tracker is actually doing into the debug log.
 *
 * <p>Two things, because the interesting failures are invisible from the totals alone:
 *
 * <ul>
 *   <li>every credited amount, tagged with where it came from - a live HUD reading or a payout
 *       message. If a job's money turns out to arrive purely through payouts, its earnings land in
 *       sparse 10k lumps and every rate derived from them is guesswork on too little data.</li>
 *   <li>a periodic snapshot of the figures the HUD is showing right now, with the active time they
 *       were computed from - so a number that looks wrong on screen can be checked against the
 *       inputs that produced it instead of guessed at.</li>
 * </ul>
 */
public final class TrackingDiagnostics {
	private final PrimeMoneyConfig config;
	private final JobStatsRegistry registry;
	private final ProjectionAccuracy accuracy;

	private long lastSnapshotMillis;

	public TrackingDiagnostics(PrimeMoneyConfig config, JobStatsRegistry registry,
			ProjectionAccuracy accuracy) {
		this.config = config;
		this.registry = registry;
		this.accuracy = accuracy;
	}

	/**
	 * A HUD reading was parsed. {@code money}/{@code xp} are what it revealed as newly earned.
	 *
	 * <p>{@code moneyValue} and {@code moneyMax} are boxed because a job without a coin counter -
	 * Fischer - has neither.
	 */
	public void onHudCredit(String job, double money, double xp, Double moneyValue, Double moneyMax,
			double xpValue, Double xpMax) {
		if (!config.debugLogTracking) {
			return;
		}

		DebugFileLogger.log(String.format(Locale.GERMANY,
				"[LIVE] %s +%.2f Münzen +%.2f XP  (Anzeige %s/%s · %.2f/%s)",
				job, money, xp, num(moneyValue), num(moneyMax), xpValue, num(xpMax)));
	}

	public void onPayoutCredit(String job, double credited, double payout) {
		if (!config.debugLogTracking) {
			return;
		}

		DebugFileLogger.log(String.format(Locale.GERMANY,
				"[AUSZAHLUNG] %s +%.2f Münzen  (gemeldet %.2f, Rest kam schon über die Leiste)",
				job, credited, payout));
	}

	public void tick(long now) {
		if (!config.debugLogTracking || config.debugSnapshotSeconds <= 0) {
			return;
		}

		if (now - lastSnapshotMillis < config.debugSnapshotSeconds * 1000L) {
			return;
		}

		lastSnapshotMillis = now;

		if (!registry.hasData()) {
			return;
		}

		RateEstimator.Projection combined =
				RateEstimator.estimate(registry.combined().rateMeter(), config, now);

		if (combined == null) {
			DebugFileLogger.log("[STAND] noch zu wenig aktive Zeit für eine Hochrechnung");
			return;
		}

		DebugFileLogger.log(String.format(Locale.GERMANY,
				"[STAND] Gesamt %.0f Münzen/h · %.0f XP/h  (±%.0f%%, %.0f%% aus dem kurzen Fenster)",
				combined.moneyPerHour(), combined.xpPerHour(),
				combined.relativeError() * 100, combined.weightShort() * 100));

		logWindow("kurz", combined.shortRate());
		logWindow("lang", combined.longRate());

		Double moneyError = accuracy.moneyError();

		if (moneyError != null) {
			DebugFileLogger.log(String.format(Locale.GERMANY,
					"         Treffsicherheit über %d Prüfungen: Münzen ±%.0f%% · XP ±%.0f%%",
					accuracy.scored(), moneyError * 100,
					accuracy.xpError() != null ? accuracy.xpError() * 100 : 0));
		}

		List<JobStats> active = registry.activeJobs(now, config.perJobActiveMillis());

		for (JobStats stats : active) {
			RateEstimator.Projection rate =
					RateEstimator.forJob(stats.rateMeter(), combined, now, config);

			DebugFileLogger.log(String.format(Locale.GERMANY,
					"         %s: %.0f Münzen/h · %.0f XP/h · Sitzung %.0f Münzen"
							+ " · Quelle: Leiste %.0f (%d Messungen) / Auszahlung %.0f",
					stats.label(),
					rate != null ? rate.moneyPerHour() : 0,
					rate != null ? rate.xpPerHour() : 0,
					stats.sessionMoney(), stats.hudMoney(), stats.hudReadings(), stats.payoutMoney()));
		}
	}

	private void logWindow(String name, LiveRateMeter.Rate rate) {
		if (rate == null) {
			DebugFileLogger.log("         Fenster " + name + ": noch keine Daten");
			return;
		}

		DebugFileLogger.log(String.format(Locale.GERMANY,
				"         Fenster %s (%.0f Min): %.0f Münzen/h · %.0f XP/h"
						+ " · %d Messungen · aktive Zeit %.0f s",
				name, rate.windowMillis() / 60000.0, rate.moneyPerHour(), rate.xpPerHour(),
				rate.earningSamples(), rate.activeMillis() / 1000.0));
	}

	private static String num(Double value) {
		return value == null ? "?" : String.format(Locale.GERMANY, "%.0f", value);
	}
}
