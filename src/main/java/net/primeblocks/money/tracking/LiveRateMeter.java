package net.primeblocks.money.tracking;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Computes the "if you keep farming like this" per-hour projection.
 *
 * <p>Dividing everything earned by wall-clock time goes wrong the moment you stop for a while: the
 * idle stretch drags the average down even though your farming speed never changed. So idle time is
 * taken out - but only genuinely idle time. A gap between two earnings counts in full up to
 * {@code pauseMillis}; beyond that it is treated as a break and contributes nothing.
 *
 * <p>That threshold has to be generous, because earnings do not arrive smoothly. A payout lands
 * only every 20-40 seconds, and while several jobs share the screen one job's own readings can be
 * minutes apart. An earlier version capped every gap instead of excluding long ones, which quietly
 * divided by a third of the real time and reported rates 2-3x too high.
 *
 * <p>The window length is the caller's choice, and it is a real trade-off - a shorter window
 * follows a change of pace sooner but rides on fewer data points. Measured against how earnings
 * actually arrive (~14 readings a minute per job while its bar is on screen, ~1.7 payouts a minute
 * when it is not), five minutes carries ~70 readings and about 12% sampling error, while the same
 * five minutes of payouts alone carries ~9 and about 34%. That is why {@link Rate} reports how many
 * earning samples it was built from: the caller can insist on a longer window when the short one is
 * running on too little.
 *
 * <p>In-memory only: this feeds a live display, and its resolution is far too fine to be worth
 * writing to disk. {@link EarningsHistory} covers the persistent totals.
 */
public final class LiveRateMeter {
	/** Heartbeats closer together than this add nothing but memory - see {@link #heartbeat}. */
	private static final long HEARTBEAT_SPACING_MILLIS = 500;

	private final Deque<Sample> samples = new ArrayDeque<>();

	/** How far back anything is kept at all; windows longer than this cannot be asked for. */
	private long retentionMillis = 60 * 60 * 1000L;

	public void setRetentionMillis(long retentionMillis) {
		this.retentionMillis = Math.max(60_000L, retentionMillis);
	}

	public void add(long timestampMillis, double money, double xp) {
		samples.addLast(new Sample(timestampMillis, money, xp));
		prune(timestampMillis, retentionMillis);
	}

	/**
	 * Marks a moment as active without any earnings attached.
	 *
	 * <p>Evidence that the player is still working, used to keep the timeline continuous between
	 * payouts. Without it a quiet stretch mid-farming is indistinguishable from a break.
	 */
	public void heartbeat(long timestampMillis) {
		Sample last = samples.peekLast();

		if (last == null || timestampMillis - last.timestamp() >= HEARTBEAT_SPACING_MILLIS) {
			samples.addLast(new Sample(timestampMillis, 0, 0));
			prune(timestampMillis, retentionMillis);
		}
	}

	public void clear() {
		samples.clear();
	}

	private void prune(long now, long windowMillis) {
		long cutoff = now - windowMillis;

		while (!samples.isEmpty() && samples.peekFirst().timestamp() < cutoff) {
			samples.removeFirst();
		}
	}

	/**
	 * @param windowMillis    how far back to look
	 * @param pauseMillis     a gap longer than this is a break and contributes no active time
	 * @param minActiveMillis how much active time is needed before a projection is meaningful
	 * @return the projection, or null while there is not enough active time yet
	 */
	public Rate compute(long now, long windowMillis, long pauseMillis, long minActiveMillis) {
		if (samples.size() < 2) {
			return null;
		}

		long activeMillis = activeMillis(now - windowMillis, now, pauseMillis);

		if (activeMillis < minActiveMillis) {
			return null;
		}

		return over(now, windowMillis, activeMillis);
	}

	/**
	 * What actually came in over a stretch that has already passed.
	 *
	 * <p>This is how a projection gets marked against reality: take what was predicted at some
	 * earlier moment, then ask what the following minutes really delivered.
	 */
	public Rate between(long from, long to, long pauseMillis, long minActiveMillis) {
		long activeMillis = activeMillis(from, to, pauseMillis);

		if (activeMillis < minActiveMillis) {
			return null;
		}

		double money = 0;
		double xp = 0;
		int earningSamples = 0;

		for (Sample sample : samples) {
			if (sample.timestamp() < from || sample.timestamp() > to) {
				continue;
			}

			money += sample.money();
			xp += sample.xp();

			if (sample.money() > 0 || sample.xp() > 0) {
				earningSamples++;
			}
		}

		double factor = 3_600_000.0 / activeMillis;
		return new Rate(money * factor, xp * factor, activeMillis, earningSamples, to - from);
	}

	private long activeMillis(long from, long to, long pauseMillis) {
		long active = 0;
		Sample previous = null;

		for (Sample sample : samples) {
			if (sample.timestamp() < from || sample.timestamp() > to) {
				continue;
			}

			if (previous != null) {
				long gap = sample.timestamp() - previous.timestamp();

				// All of it or none of it: either the player was working through that stretch, or
				// they were away. Counting part of a long break would still inflate the rate.
				if (gap <= pauseMillis) {
					active += gap;
				}
			}

			previous = sample;
		}

		// The time since the last earning counts too, up to the pause threshold. Without it a rate
		// left alone stays frozen at whatever it was, so a number from ten minutes ago reads as if
		// it were current; with it the figure visibly goes stale and then holds.
		if (previous != null) {
			active += Math.min(to - previous.timestamp(), pauseMillis);
		}

		return active;
	}

	/**
	 * The same projection, but against an active time measured somewhere else - the combined
	 * timeline across all jobs.
	 *
	 * <p>A job's own gaps are the wrong yardstick when several jobs run together: while one pays
	 * out the others are idle, so measuring each against only its own busy stretches divides by too
	 * little time and overstates every one of them. Sharing one denominator makes each job's figure
	 * its share of the whole, so the per-job numbers add up to the combined one instead of
	 * contradicting it.
	 */
	public Rate over(long now, long windowMillis, long activeMillis) {
		if (activeMillis <= 0 || samples.isEmpty()) {
			return null;
		}

		long cutoff = now - windowMillis;
		double money = 0;
		double xp = 0;
		int earningSamples = 0;

		for (Sample sample : samples) {
			if (sample.timestamp() < cutoff) {
				continue;
			}

			money += sample.money();
			xp += sample.xp();

			if (sample.money() > 0 || sample.xp() > 0) {
				earningSamples++;
			}
		}

		double factor = 3_600_000.0 / activeMillis;
		return new Rate(money * factor, xp * factor, activeMillis, earningSamples, windowMillis);
	}

	/**
	 * @param earningSamples how many readings actually carried earnings - the measure of how much
	 *                       this estimate rests on, since heartbeats carry none
	 */
	public record Rate(double moneyPerHour, double xpPerHour, long activeMillis, int earningSamples,
			long windowMillis) {
	}

	private record Sample(long timestamp, double money, double xp) {
	}
}
