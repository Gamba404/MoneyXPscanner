package net.primeblocks.money.tracking;

import net.primeblocks.money.PrimeMoneyConfig;

/**
 * Turns the recent and the longer-run measurements into one projection, weighting them by how much
 * each is worth believing right now.
 *
 * <p>Neither window is right on its own. The long one is the more precise estimate of a steady
 * pace - it rests on far more readings - but it keeps averaging in a farm you left twenty minutes
 * ago. The short one follows a change of pace immediately, at the cost of riding on few readings.
 * Picking one and living with its weakness is what makes a projection feel wrong.
 *
 * <p>So the deciding question is not "which window" but <em>has the pace actually changed?</em>
 * Each window's sampling error is about {@code rate / sqrt(readings)}, which says how far apart the
 * two may drift on chance alone. Their gap is measured in exactly those units:
 *
 * <pre>z = |short - long| / sqrt(err_short² + err_long²)</pre>
 *
 * <p>A z near zero means the two disagree no more than noise predicts - the pace is steady, and the
 * long window is simply the better measurement of it. A large z means the difference is too big to
 * be chance: something really did change, the long window is averaging in history that no longer
 * applies, and the short one is right. The weight follows that evidence smoothly rather than
 * flipping at a threshold, so the displayed figure never jumps between two answers.
 *
 * <p>Because the blend is linear, applying the same weight to one job's numbers keeps the per-job
 * lines an exact breakdown of the combined one - see {@link #forJob}.
 */
public final class RateEstimator {
	private RateEstimator() {
	}

	/**
	 * @return the projection, or null when neither window has enough to say anything
	 */
	public static Projection estimate(LiveRateMeter meter, PrimeMoneyConfig config, long now) {
		LiveRateMeter.Rate shortRate = meter.compute(now, config.rateShortWindowMillis(),
				config.ratePauseMillis(), config.rateMinActiveMillis());
		LiveRateMeter.Rate longRate = meter.compute(now, config.rateLongWindowMillis(),
				config.ratePauseMillis(), config.rateMinActiveMillis());

		if (shortRate == null && longRate == null) {
			return null;
		}

		if (shortRate == null) {
			return new Projection(longRate.moneyPerHour(), longRate.xpPerHour(), 0,
					relativeError(longRate), null, longRate);
		}

		if (longRate == null || shortRate.earningSamples() < config.rateMinEvents) {
			// Too thin to stand on its own; the long window is the only honest answer.
			LiveRateMeter.Rate fallback = longRate != null ? longRate : shortRate;
			double weight = longRate != null ? 0 : 1;
			return new Projection(fallback.moneyPerHour(), fallback.xpPerHour(), weight,
					relativeError(fallback), shortRate, longRate);
		}

		double weight = shortWeight(shortRate, longRate, config.rateBlendSensitivity);

		return new Projection(
				blend(shortRate.moneyPerHour(), longRate.moneyPerHour(), weight),
				blend(shortRate.xpPerHour(), longRate.xpPerHour(), weight),
				weight,
				blendedRelativeError(shortRate, longRate, weight),
				shortRate,
				longRate);
	}

	/**
	 * The same projection for a single job, reusing the combined weighting.
	 *
	 * <p>The weight and both active times come from the combined timeline on purpose. A job's own
	 * gaps are the wrong yardstick when several jobs run together - while one pays out the others
	 * are idle - and sharing the denominator is what makes the job lines add up to the total.
	 */
	public static Projection forJob(LiveRateMeter jobMeter, Projection combined, long now,
			PrimeMoneyConfig config) {
		if (combined == null) {
			return null;
		}

		LiveRateMeter.Rate shortRate = combined.shortRate() != null
				? jobMeter.over(now, combined.shortRate().windowMillis(), combined.shortRate().activeMillis())
				: null;
		LiveRateMeter.Rate longRate = combined.longRate() != null
				? jobMeter.over(now, combined.longRate().windowMillis(), combined.longRate().activeMillis())
				: null;

		if (shortRate == null && longRate == null) {
			return null;
		}

		if (shortRate == null) {
			return new Projection(longRate.moneyPerHour(), longRate.xpPerHour(),
					combined.weightShort(), combined.relativeError(), null, longRate);
		}

		if (longRate == null) {
			return new Projection(shortRate.moneyPerHour(), shortRate.xpPerHour(),
					combined.weightShort(), combined.relativeError(), shortRate, null);
		}

		double weight = combined.weightShort();

		return new Projection(
				blend(shortRate.moneyPerHour(), longRate.moneyPerHour(), weight),
				blend(shortRate.xpPerHour(), longRate.xpPerHour(), weight),
				weight, combined.relativeError(), shortRate, longRate);
	}

	private static double blend(double shortValue, double longValue, double weight) {
		return weight * shortValue + (1 - weight) * longValue;
	}

	/**
	 * How much to believe the short window, from how far the two disagree relative to the noise
	 * either of them carries.
	 */
	private static double shortWeight(LiveRateMeter.Rate shortRate, LiveRateMeter.Rate longRate,
			double sensitivity) {
		double shortErr = shortRate.moneyPerHour() * relativeError(shortRate);
		double longErr = longRate.moneyPerHour() * relativeError(longRate);
		double noise = Math.sqrt(shortErr * shortErr + longErr * longErr);

		if (noise <= 0) {
			return 0;
		}

		double z = Math.abs(shortRate.moneyPerHour() - longRate.moneyPerHour()) / noise;
		double k = Math.max(0.1, sensitivity);

		// Rises from 0 to 1 as the gap outgrows the noise: half weight at z = sqrt(k).
		return (z * z) / (k + z * z);
	}

	/** Sampling error of a sum of n increments runs at roughly 1/sqrt(n). */
	private static double relativeError(LiveRateMeter.Rate rate) {
		int n = Math.max(1, rate.earningSamples());
		return 1.0 / Math.sqrt(n);
	}

	private static double blendedRelativeError(LiveRateMeter.Rate shortRate,
			LiveRateMeter.Rate longRate, double weight) {
		double shortRel = relativeError(shortRate);
		double longRel = relativeError(longRate);
		double variance = weight * weight * shortRel * shortRel
				+ (1 - weight) * (1 - weight) * longRel * longRel;
		return Math.sqrt(variance);
	}

	/**
	 * @param weightShort    how much of the answer came from the recent window, 0..1
	 * @param relativeError  rough spread of the estimate, as a fraction of itself
	 */
	public record Projection(double moneyPerHour, double xpPerHour, double weightShort,
			double relativeError, LiveRateMeter.Rate shortRate, LiveRateMeter.Rate longRate) {
	}
}
