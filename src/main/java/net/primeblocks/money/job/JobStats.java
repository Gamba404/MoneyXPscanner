package net.primeblocks.money.job;

import net.primeblocks.money.tracking.LiveRateMeter;

/**
 * Everything tracked for one job: what it earned this session, when, and how far it is from
 * levelling up.
 *
 * <p>One of these per job rather than one shared set, because several jobs run at once: a spawner
 * farm pays Jäger and Taucher in the same breath and their HUD lines alternate. With shared state
 * every line would look like a job switch, every delta would be discarded, and the level estimate
 * would keep getting overwritten by whichever job happened to report last.
 */
public final class JobStats {
	private final String label;
	private final LiveRateMeter rateMeter = new LiveRateMeter();

	/** Money toward the next payout, and XP toward the next level - see {@link CycleCounter}. */
	private final CycleCounter moneyCounter = new CycleCounter();
	private final CycleCounter xpCounter = new CycleCounter();

	private double sessionMoney;
	private double sessionXp;
	private long sessionStartMillis = -1;
	private long lastEarnMillis = -1;

	// Split by where the money was learned about, purely so the debug log can answer "is the live
	// HUD actually feeding this job, or is it running on payout messages alone?".
	private double hudMoney;
	private double payoutMoney;
	private int hudReadings;

	private Double levelXp;
	private Double levelXpMax;

	/** Whether this job pays anything per action at all - Fischer does not, see {@link #onHudReading}. */
	private boolean earnsMoney;

	JobStats(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	/**
	 * A fresh reading of this job's HUD line. Returns what it revealed as newly earned.
	 *
	 * <p>{@code money} may be null: a job that pays nothing per action - Fischer, whose catches are
	 * items until sold - shows no coin counter at all, and its XP is still worth following.
	 */
	Earned onHudReading(Double money, Double moneyMax, double xp, Double xpMax) {
		levelXp = xp;
		levelXpMax = xpMax;
		hudReadings++;

		double moneyEarned = 0;

		if (money != null) {
			earnsMoney = true;
			moneyEarned = moneyCounter.observe(money, moneyMax);
		}

		Earned earned = new Earned(moneyEarned, xpCounter.observe(xp, xpMax));
		hudMoney += earned.money();
		return earned;
	}

	/** A payout announced in chat. Returns what it revealed as newly earned. */
	double onPayout(double amount) {
		earnsMoney = true;
		double earned = moneyCounter.complete(amount);
		payoutMoney += earned;
		return earned;
	}

	/**
	 * Whether coins are worth showing for this job. False for one that pays nothing per action, so
	 * its line says nothing rather than a row of zeroes that reads like a failure.
	 */
	public boolean earnsMoney() {
		return earnsMoney;
	}

	void credit(long now, double money, double xp) {
		if (money <= 0 && xp <= 0) {
			return;
		}

		if (sessionStartMillis < 0) {
			sessionStartMillis = now;
		}

		lastEarnMillis = now;
		sessionMoney += money;
		sessionXp += xp;
		rateMeter.add(now, money, xp);
	}

	/**
	 * XP still missing for the next level-up, or null when there is no answer - which includes a
	 * job sitting at the level cap, where the counter keeps climbing with no next level to reach.
	 */
	public Double xpToNextLevel() {
		if (levelXp == null || levelXpMax == null || levelXpMax <= 0) {
			return null;
		}

		double remaining = levelXpMax - levelXp;
		return remaining > 0 ? remaining : null;
	}

	public LiveRateMeter rateMeter() {
		return rateMeter;
	}

	public boolean hasData() {
		return lastEarnMillis >= 0;
	}

	/** Whether this job earned anything recently enough to still be worth a line on the HUD. */
	public boolean isActive(long now, long withinMillis) {
		return lastEarnMillis >= 0 && now - lastEarnMillis <= withinMillis;
	}

	public double sessionMoney() {
		return sessionMoney;
	}

	public double sessionXp() {
		return sessionXp;
	}

	public long lastEarnMillis() {
		return lastEarnMillis;
	}

	public long sessionElapsedMillis(long now) {
		return sessionStartMillis < 0 ? 0 : Math.max(0, now - sessionStartMillis);
	}

	/** Money this job learned about from live HUD readings rather than from a payout message. */
	public double hudMoney() {
		return hudMoney;
	}

	public double payoutMoney() {
		return payoutMoney;
	}

	public int hudReadings() {
		return hudReadings;
	}

	record Earned(double money, double xp) {
	}
}
