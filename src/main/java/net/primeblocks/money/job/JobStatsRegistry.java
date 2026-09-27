package net.primeblocks.money.job;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The per-job {@link JobStats}, plus one combined set covering all of them.
 *
 * <p>The combined set is fed every delta as it happens rather than being summed on demand, so its
 * rate meter sees the real timeline of earnings across all jobs instead of having to guess at one.
 */
public final class JobStatsRegistry {
	/** Insertion-ordered so the HUD lines stay put instead of reshuffling as rates move around. */
	private final Map<String, JobStats> byJob = new LinkedHashMap<>();
	private JobStats combined = new JobStats("Gesamt");

	/** How far back every rate meter keeps samples - the longest window anyone can ask for. */
	private long retentionMillis = 60 * 60 * 1000L;

	public void setRetentionMillis(long retentionMillis) {
		this.retentionMillis = retentionMillis;
		combined.rateMeter().setRetentionMillis(retentionMillis);

		for (JobStats stats : byJob.values()) {
			stats.rateMeter().setRetentionMillis(retentionMillis);
		}
	}

	/** The stats for a job, created on first sight. A missing label lands in one shared bucket. */
	public JobStats forJob(String label) {
		String key = key(label);
		return byJob.computeIfAbsent(key, ignored -> {
			JobStats stats = new JobStats(displayName(label));
			stats.rateMeter().setRetentionMillis(retentionMillis);
			return stats;
		});
	}

	/** Existing stats for a job, or null if that job has never reported. */
	public JobStats peek(String label) {
		return byJob.get(key(label));
	}

	public JobStats combined() {
		return combined;
	}

	/** Records earnings against both the job itself and the combined totals. */
	public void credit(JobStats stats, long now, double money, double xp) {
		stats.credit(now, money, xp);
		combined.credit(now, money, xp);
	}

	/**
	 * Notes that a job HUD line just came in, whether or not it revealed any earnings.
	 *
	 * <p>The server only draws that line while a job is being worked, so its mere arrival is proof
	 * the player is still at it. That keeps the combined timeline - the one every rate is measured
	 * against - continuous through the quiet stretches between payouts.
	 */
	public void heartbeat(long now) {
		combined.rateMeter().heartbeat(now);
	}

	/** Jobs that earned something within {@code withinMillis}, in the order they first appeared. */
	public List<JobStats> activeJobs(long now, long withinMillis) {
		List<JobStats> active = new ArrayList<>();

		for (JobStats stats : byJob.values()) {
			if (stats.isActive(now, withinMillis)) {
				active.add(stats);
			}
		}

		return active;
	}

	/** The job that earned most recently - what the HUD names when it shows a single title. */
	public JobStats mostRecent() {
		JobStats best = null;

		for (JobStats stats : byJob.values()) {
			if (stats.hasData() && (best == null || stats.lastEarnMillis() > best.lastEarnMillis())) {
				best = stats;
			}
		}

		return best;
	}

	public boolean hasData() {
		return combined.hasData();
	}

	/**
	 * Clears the session counters by dropping the stats outright, so a job no longer being worked
	 * stops taking up a HUD line. A job that carries on gets a fresh {@link JobStats} on its next
	 * reading, whose counter re-baselines against whatever the counter says at that moment - so a
	 * reset cannot make the progress already on display look like it was just earned.
	 */
	public void reset() {
		byJob.clear();
		combined = new JobStats(combined.label());
		combined.rateMeter().setRetentionMillis(retentionMillis);
	}

	private static String key(String label) {
		return label == null || label.isBlank() ? "" : label.trim().toLowerCase(Locale.ROOT);
	}

	/** Prefers the wiki's spelling so casing and umlauts stay consistent whatever the server sent. */
	private static String displayName(String label) {
		JobDefinition known = JobDefinition.fromLabel(label);

		if (known != null) {
			return known.displayName();
		}

		return label == null || label.isBlank() ? "Job" : label.trim();
	}
}
