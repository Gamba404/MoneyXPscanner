package net.primeblocks.money.job;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.primeblocks.money.PrimeMoneyClient;
import net.primeblocks.money.PrimeMoneyConfig;
import net.primeblocks.money.debug.TrackingDiagnostics;
import net.primeblocks.money.tracking.EarningsHistory;

/**
 * Reads PrimeBlocks' live job HUD - the line above the hotbar, shown while a job is being worked:
 *
 * <pre>Farmer » 340,896.16/365,000 [xp] | 8,378.14/10,000 [coin]</pre>
 *
 * <p>Only the numbers <em>before</em> each slash carry earnings. The values after them are the
 * current level's XP requirement and the chosen payout threshold; both change over time and neither
 * says anything about what was earned. Feeding the leading numbers through a {@link CycleCounter}
 * gives live, fine-grained tracking - this line updates many times a second, versus one chat payout
 * every several seconds - while staying correct across the resets both counters go through.
 *
 * <p>Counters live per job in {@link JobStats}, because more than one job runs at a time: a spawner
 * farm pays Jäger and Taucher together and their HUD lines alternate. One shared baseline would
 * read every line as a job switch and throw away every delta.
 */
public final class JobHudTracker {
	private final PrimeMoneyConfig config;
	private final JobStatsRegistry registry;
	private final EarningsHistory history;
	private final TrackingDiagnostics diagnostics;

	private String compiledHudRegex;
	private Pattern hudPattern;

	// History is written in buckets: the HUD ticks several times a second, and one stored entry per
	// tick would mean hundreds of thousands of them per day on disk for no added accuracy.
	private double bucketMoney;
	private double bucketXp;
	private String bucketJob;
	private long bucketStartMillis = Long.MIN_VALUE;

	public JobHudTracker(PrimeMoneyConfig config, JobStatsRegistry registry, EarningsHistory history,
			TrackingDiagnostics diagnostics) {
		this.config = config;
		this.registry = registry;
		this.history = history;
		this.diagnostics = diagnostics;
	}

	/** Registers this tracker as the consumer of overlay text intercepted by the HUD mixin. */
	public void register() {
		OverlayMessageBus.setListener(this::onOverlayText);
	}

	/**
	 * Books a payout announced in chat.
	 *
	 * <p>Safe to call for every payout, always: the money counter folds it into the same running
	 * total the HUD readings feed, so a payout and the HUD progress it consumed can never be
	 * counted as two separate earnings.
	 */
	public void onPayout(String jobLabel, double amount, long now) {
		if (!config.isJobEnabled(jobLabel)) {
			return;
		}

		JobStats stats = registry.forJob(jobLabel);
		double earned = stats.onPayout(amount);
		diagnostics.onPayoutCredit(stats.label(), earned, amount);

		if (earned > 0) {
			registry.credit(stats, now, earned, 0);
			addToBucket(now, earned, 0, stats.label());
		}
	}

	public void onDisconnect() {
		flushBucket();
	}

	public void tick(long now) {
		if (bucketStartMillis != Long.MIN_VALUE && now - bucketStartMillis >= config.historyBucketMillis()) {
			flushBucket();
		}
	}

	private void onOverlayText(String text) {
		ensurePattern();

		if (hudPattern == null) {
			return;
		}

		Matcher matcher = hudPattern.matcher(text);

		if (!matcher.find()) {
			return;
		}

		Double xp = parseGroup(matcher, "xp");
		Double xpMax = parseGroup(matcher, "xpmax");
		Double money = parseGroup(matcher, "money");
		Double moneyMax = parseGroup(matcher, "moneymax");

		if (xp == null) {
			return;
		}

		String job = group(matcher, "job");
		job = job != null ? job.trim() : null;

		// Without the coin half, "<name> » <number>/<number>" is a shape plenty of unrelated server
		// messages could take, so an unrecognised name is not enough to go on. With it, the line is
		// distinctive enough to trust from any job - including ones this mod has never heard of.
		if (money == null && JobDefinition.fromLabel(job) == null) {
			return;
		}

		if (!config.isJobEnabled(job)) {
			return;
		}

		long now = System.currentTimeMillis();
		registry.heartbeat(now);

		JobStats stats = registry.forJob(job);
		JobStats.Earned earned = stats.onHudReading(money, moneyMax, xp, xpMax);

		if (earned.money() <= 0 && earned.xp() <= 0) {
			return;
		}

		diagnostics.onHudCredit(stats.label(), earned.money(), earned.xp(), money, moneyMax, xp, xpMax);

		registry.credit(stats, now, earned.money(), earned.xp());
		addToBucket(now, earned.money(), earned.xp(), stats.label());
	}

	private void addToBucket(long now, double money, double xp, String job) {
		// A bucket holds one job's earnings, so the stored history keeps the attribution.
		if (bucketStartMillis != Long.MIN_VALUE && !Objects.equals(bucketJob, job)) {
			flushBucket();
		}

		if (bucketStartMillis == Long.MIN_VALUE) {
			bucketStartMillis = now;
		}

		bucketMoney += money;
		bucketXp += xp;
		bucketJob = job;
	}

	private void flushBucket() {
		if (bucketStartMillis != Long.MIN_VALUE && (bucketMoney > 0 || bucketXp > 0)) {
			history.add(new JobEvent(System.currentTimeMillis(), bucketMoney, bucketXp, bucketJob));
		}

		bucketStartMillis = Long.MIN_VALUE;
		bucketMoney = 0;
		bucketXp = 0;
		bucketJob = null;
	}

	private Double parseGroup(Matcher matcher, String name) {
		String raw = group(matcher, name);
		return raw != null ? NumberParser.parse(raw) : null;
	}

	private String group(Matcher matcher, String name) {
		try {
			return matcher.group(name);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private void ensurePattern() {
		if (config.hudRegex.equals(compiledHudRegex)) {
			return;
		}

		compiledHudRegex = config.hudRegex;

		if (compiledHudRegex == null || compiledHudRegex.isBlank()) {
			hudPattern = null;
			return;
		}

		try {
			hudPattern = Pattern.compile(compiledHudRegex);
		} catch (PatternSyntaxException e) {
			PrimeMoneyClient.LOGGER.warn("[primemoney] Ungueltiges hudRegex in der Config: {}", compiledHudRegex, e);
			hudPattern = null;
		}
	}
}
