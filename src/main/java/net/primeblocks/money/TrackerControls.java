package net.primeblocks.money;

import net.primeblocks.money.job.JobStatsRegistry;
import net.primeblocks.money.tracking.EarningsHistory;

/**
 * The counter resets offered by the settings screen, so that screen does not need to know about
 * the tracking objects themselves.
 */
public final class TrackerControls {
	private final JobStatsRegistry registry;
	private final EarningsHistory history;

	public TrackerControls(JobStatsRegistry registry, EarningsHistory history) {
		this.registry = registry;
		this.history = history;
	}

	/** Clears every job's session total and the projections they feed. */
	public void resetSession() {
		registry.reset();
	}

	public void resetHourWindow() {
		history.resetHourWindow();
	}

	public void resetDayWindow() {
		history.resetDayWindow();
	}
}
