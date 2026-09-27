package net.primeblocks.money.tracking;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import net.primeblocks.money.PrimeMoneyConfig;

/**
 * Marks the projection against reality: what it predicted a while ago, versus what the minutes
 * that followed actually delivered.
 *
 * <p>Every so often the current projection is filed away. Once enough time has passed for the
 * answer to exist, the same stretch is measured for real and the two are compared. What comes out
 * is a plain statement of how far off the projection tends to be - not a guess at its accuracy
 * from theory, but its measured track record on this player's actual farming.
 *
 * <p>Deliberately measurement only, with no correction fed back into the estimate. A projection
 * that adjusts itself by its own past error chases its own tail: the correction changes the
 * prediction, which changes the next error, which changes the correction again. Drift between the
 * windows is already handled where it belongs, in {@link RateEstimator}.
 */
public final class ProjectionAccuracy {
	/** Filing a prediction more often than this would just measure the same stretch repeatedly. */
	private static final long RECORD_INTERVAL_MILLIS = 30_000;

	/** Enough scores to be meaningful, few enough to stay recent. */
	private static final int MAX_SCORES = 40;

	private final Deque<Pending> pending = new ArrayDeque<>();
	private final Deque<Double> moneyErrors = new ArrayDeque<>();
	private final Deque<Double> xpErrors = new ArrayDeque<>();

	private long lastRecordMillis;

	public void tick(LiveRateMeter meter, PrimeMoneyConfig config, long now) {
		score(meter, config, now);

		if (now - lastRecordMillis < RECORD_INTERVAL_MILLIS) {
			return;
		}

		// Only worked out when a prediction is actually due to be filed - computing it on every
		// client tick would walk the whole sample window twenty times a second for nothing.
		RateEstimator.Projection projection = RateEstimator.estimate(meter, config, now);

		if (projection == null) {
			return;
		}

		lastRecordMillis = now;
		pending.addLast(new Pending(now, projection.moneyPerHour(), projection.xpPerHour()));
	}

	private void score(LiveRateMeter meter, PrimeMoneyConfig config, long now) {
		long horizon = config.accuracyHorizonMillis();

		while (!pending.isEmpty() && now - pending.peekFirst().timestamp() >= horizon) {
			Pending due = pending.removeFirst();
			LiveRateMeter.Rate actual = meter.between(due.timestamp(), due.timestamp() + horizon,
					config.ratePauseMillis(), config.rateMinActiveMillis());

			if (actual == null) {
				// Not enough farming happened in that stretch to judge the prediction against.
				continue;
			}

			// The projection says "if you keep farming like this". When the player stopped instead,
			// the premise fell away and the prediction was never wrong - it was never tested. Only
			// stretches that really were spent farming are allowed to count for or against it;
			// without this the score is dominated by breaks and reads as hundreds of percent off.
			if (actual.activeMillis() < horizon / 2) {
				continue;
			}

			record(moneyErrors, due.moneyPerHour(), actual.moneyPerHour());
			record(xpErrors, due.xpPerHour(), actual.xpPerHour());
		}
	}

	private void record(Deque<Double> errors, double predicted, double actual) {
		if (actual <= 0 && predicted <= 0) {
			return;
		}

		// Measured against the midpoint of the two rather than against the outcome alone: dividing
		// by the outcome makes the same absolute miss look enormous whenever the outcome happens to
		// be small, which says more about the divisor than about the projection.
		double midpoint = (predicted + actual) / 2;
		errors.addLast(Math.abs(predicted - actual) / midpoint);

		while (errors.size() > MAX_SCORES) {
			errors.removeFirst();
		}
	}

	/**
	 * Typical error of the money projection as a fraction, or null with nothing scored yet.
	 *
	 * <p>The median, not the average: one odd stretch - a booster expiring, a farm running dry -
	 * would otherwise drag the whole figure and make a projection that is usually right look
	 * unreliable.
	 */
	public Double moneyError() {
		return median(moneyErrors);
	}

	public Double xpError() {
		return median(xpErrors);
	}

	public int scored() {
		return moneyErrors.size();
	}

	public void clear() {
		pending.clear();
		moneyErrors.clear();
		xpErrors.clear();
	}

	private static Double median(Deque<Double> errors) {
		if (errors.isEmpty()) {
			return null;
		}

		double[] sorted = new double[errors.size()];
		int i = 0;

		for (double error : errors) {
			sorted[i++] = error;
		}

		Arrays.sort(sorted);
		int middle = sorted.length / 2;

		return sorted.length % 2 == 1
				? sorted[middle]
				: (sorted[middle - 1] + sorted[middle]) / 2;
	}

	private record Pending(long timestamp, double moneyPerHour, double xpPerHour) {
	}
}
