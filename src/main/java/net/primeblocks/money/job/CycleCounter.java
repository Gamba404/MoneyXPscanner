package net.primeblocks.money.job;

/**
 * Tracks a counter that fills up and starts over - job money running toward the next payout, job
 * XP running toward the next level.
 *
 * <p>Rather than trying to measure each individual jump, this keeps a running total of
 * "everything this counter has ever been worth" ({@code completed + progress}) and hands out only
 * what is new since last time. That makes it immune to the two things that actually go wrong in
 * practice: readings that get skipped entirely (the total simply catches up on the next one) and a
 * payout arriving through a different channel than the counter itself (it lands in the same total,
 * so it cannot be counted twice).
 *
 * <p>Whatever the counter already held when it first came into view is deliberately not credited -
 * that progress was made before this was watching.
 */
final class CycleCounter {
	/** A reading that comes back a hair below the last one is float noise, not a new cycle. */
	private static final double EPSILON = 0.001;

	private double completed;
	private double progress;
	private double credited;
	private boolean started;

	/**
	 * The cycle size from the previous reading - the size of the cycle currently being filled, and
	 * therefore of the one that ends at the next wrap.
	 */
	private Double cycleSize;

	/**
	 * Takes a new reading of the counter.
	 *
	 * @param cycleSize the target shown alongside the counter, used to size a wrap
	 * @return how much was newly earned
	 */
	double observe(double value, Double cycleSize) {
		if (!started) {
			started = true;
			this.cycleSize = cycleSize;
			return rebaseline(value);
		}

		if (value < progress - EPSILON) {
			// Size the finished cycle from the PREVIOUS reading, never this one. A level-up brings
			// the next level's requirement along with it - 515,000 where the level just completed
			// needed 365,000 - so taking the number off the same line that reports the reset
			// invents the whole difference between the two as XP earned.
			if (this.cycleSize == null || this.cycleSize <= 0) {
				// Nothing usable to size it with. A job at the level cap reports its XP target as 0
				// while the counter keeps climbing into the millions, so guessing "one whole cycle"
				// could invent millions out of a single odd reading. Starting over costs at most
				// one real cycle, and only in a case that should not arise.
				this.cycleSize = cycleSize;
				return rebaseline(value);
			}

			completed += this.cycleSize;
		}

		this.cycleSize = cycleSize;
		progress = value;
		return take();
	}

	/** Treats the current reading as the new starting point, crediting nothing for the jump. */
	private double rebaseline(double value) {
		progress = value;
		credited = completed + value;
		return 0;
	}

	/**
	 * A cycle finished and its exact size is known - a payout announcing what it paid. Payouts
	 * overshoot the threshold slightly, so this is meaningfully more accurate than the estimate
	 * {@link #observe} would have to fall back on.
	 */
	double complete(double exactAmount) {
		started = true;
		completed += exactAmount;
		progress = 0;
		return take();
	}

	private double take() {
		double total = completed + progress;
		double delta = total - credited;

		if (delta <= 0) {
			return 0;
		}

		credited = total;
		return delta;
	}
}
