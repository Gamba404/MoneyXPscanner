package net.primeblocks.money.tracking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import net.fabricmc.loader.api.FabricLoader;

import net.primeblocks.money.PrimeMoneyClient;
import net.primeblocks.money.io.BackgroundWriter;
import net.primeblocks.money.job.JobEvent;

/**
 * Every {@link JobEvent} of the last 24 hours, kept in memory and mirrored to disk so the 24h
 * total survives a client restart - one such record per citybuild.
 *
 * <p>CB1, CB2 and CB3 are unrelated economies: farming CB1 at 500k/h says nothing about CB2, and
 * folding both into one "last 24h" number would be a meaningless average of two different jobs on
 * two different servers-in-practice. Each CB therefore gets its own {@link Bucket}, keyed by the id
 * {@link net.primeblocks.money.job.CityBuildDetector} reports; a player who cannot be placed on a
 * known CB (detection failed, or a server other than PrimeBlocks) lands in one default bucket that
 * keeps the plain, pre-CB-awareness filename, so nothing needs migrating.
 *
 * <p>Buckets are loaded lazily and only ever grow, so hopping between CBs during a session does not
 * repeatedly touch disk - once a CB's file has been read this run, switching back to it is memory
 * only until the next flush.
 */
public final class EarningsHistory {
	private static final Gson GSON = new GsonBuilder().create();
	private static final long RETENTION_MILLIS = 24L * 60 * 60 * 1000;
	private static final long FLUSH_INTERVAL_MILLIS = 15_000;

	public static final long HOUR_MILLIS = 60L * 60 * 1000;
	public static final long DAY_MILLIS = RETENTION_MILLIS;

	/** The default bucket's key, and the CB shown when none has been detected yet. */
	public static final String UNKNOWN_CB = "";

	/** Insertion-ordered so {@link #flush()} at shutdown writes in a stable, readable order. */
	private final Map<String, Bucket> buckets = new LinkedHashMap<>();
	private String activeCb = UNKNOWN_CB;

	public static EarningsHistory load() {
		return new EarningsHistory();
	}

	/** Which CB's bucket every call below now reads and writes. */
	public String activeCityBuild() {
		return activeCb;
	}

	/**
	 * Switches which CB's bucket is active. A no-op if it already is one - called every couple of
	 * seconds from detection, and re-resolving the same bucket each time would be wasted work.
	 */
	public void setActiveCityBuild(String cb) {
		String key = normalize(cb);

		if (!key.equals(activeCb)) {
			activeCb = key;
		}
	}

	public void add(JobEvent event) {
		bucket(activeCb).add(event);
	}

	/** Earnings of the last 60 minutes on the active CB, or since that counter was last cleared. */
	public Sum hourSum(long now) {
		return bucket(activeCb).hourSum(now);
	}

	/** Earnings of the last 24 hours on the active CB, or since that counter was last cleared. */
	public Sum daySum(long now) {
		return bucket(activeCb).daySum(now);
	}

	public void resetHourWindow() {
		bucket(activeCb).resetHourWindow();
	}

	public void resetDayWindow() {
		bucket(activeCb).resetDayWindow();
	}

	/** Whether the active CB has any event from the last 24 hours on record. */
	public boolean hasAnyData() {
		return bucket(activeCb).hasAnyData();
	}

	public void tick(long now) {
		for (Bucket bucket : buckets.values()) {
			bucket.tickFlush(now);
		}
	}

	/** Saves every CB touched this session, not just the active one - used on disconnect/shutdown. */
	public void flush() {
		for (Bucket bucket : buckets.values()) {
			bucket.flush();
		}
	}

	private Bucket bucket(String cb) {
		return buckets.computeIfAbsent(cb, Bucket::load);
	}

	private static String normalize(String cb) {
		return cb == null || cb.isBlank() ? UNKNOWN_CB : cb;
	}

	public record Sum(double money, double xp) {
	}

	/**
	 * One CB's worth of history: its own events, its own 60min/24h reset cutoffs, its own file.
	 *
	 * <p>The two windows carry their own cutoff timestamps rather than sharing one, so clearing the
	 * hour counter leaves the day counter alone and vice versa - dropping the underlying events
	 * instead would make either reset silently eat the other window too.
	 */
	private static final class Bucket {
		private final String cb;
		private final Deque<JobEvent> events = new ArrayDeque<>();
		private long hourCutoffMillis;
		private long dayCutoffMillis;
		private boolean dirty;
		private long lastFlushMillis;

		private Bucket(String cb) {
			this.cb = cb;
		}

		static Bucket load(String cb) {
			Bucket bucket = new Bucket(cb);
			Path file = path(cb);

			if (!Files.exists(file)) {
				return bucket;
			}

			try {
				JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
				StoredFile stored;

				if (root.isJsonArray()) {
					// Files written before the reset cutoffs (or CB buckets) existed were a bare
					// event array.
					stored = new StoredFile();
					stored.events = GSON.fromJson(root, StoredEntry[].class);
				} else {
					stored = GSON.fromJson(root, StoredFile.class);
				}

				if (stored != null) {
					bucket.hourCutoffMillis = stored.hourCutoff;
					bucket.dayCutoffMillis = stored.dayCutoff;
					long cutoff = System.currentTimeMillis() - RETENTION_MILLIS;

					if (stored.events != null) {
						for (StoredEntry entry : stored.events) {
							if (entry != null && entry.t >= cutoff) {
								bucket.events.addLast(new JobEvent(entry.t, entry.money, entry.xp, entry.job));
							}
						}
					}
				}
			} catch (IOException | JsonSyntaxException e) {
				PrimeMoneyClient.LOGGER.warn("Konnte {} nicht lesen", file, e);
			}

			return bucket;
		}

		void add(JobEvent event) {
			events.addLast(event);
			prune(event.timestampMillis());
			dirty = true;
		}

		private void prune(long now) {
			long cutoff = now - RETENTION_MILLIS;

			while (!events.isEmpty() && events.peekFirst().timestampMillis() < cutoff) {
				events.removeFirst();
			}
		}

		Sum hourSum(long now) {
			return sumSince(Math.max(now - HOUR_MILLIS, hourCutoffMillis));
		}

		Sum daySum(long now) {
			return sumSince(Math.max(now - DAY_MILLIS, dayCutoffMillis));
		}

		private Sum sumSince(long from) {
			double money = 0;
			double xp = 0;
			Iterator<JobEvent> it = events.descendingIterator();

			while (it.hasNext()) {
				JobEvent event = it.next();

				if (event.timestampMillis() < from) {
					break;
				}

				money += event.money();
				xp += event.xp();
			}

			return new Sum(money, xp);
		}

		void resetHourWindow() {
			hourCutoffMillis = System.currentTimeMillis();
			dirty = true;
		}

		void resetDayWindow() {
			dayCutoffMillis = System.currentTimeMillis();
			dirty = true;
		}

		boolean hasAnyData() {
			return !events.isEmpty();
		}

		void tickFlush(long now) {
			if (dirty && now - lastFlushMillis >= FLUSH_INTERVAL_MILLIS) {
				flush();
			}
		}

		/**
		 * Saves this bucket. Only the snapshot happens on the calling thread - copying the events
		 * into an array, a matter of microseconds. Turning them into JSON and putting them on disk
		 * is the slow part, and a day of them is a six-figure document, so that runs on
		 * {@link BackgroundWriter}.
		 */
		void flush() {
			prune(System.currentTimeMillis());

			StoredFile toStore = new StoredFile();
			toStore.hourCutoff = hourCutoffMillis;
			toStore.dayCutoff = dayCutoffMillis;
			toStore.events = events.stream()
					.map(e -> new StoredEntry(e.timestampMillis(), e.money(), e.xp(), e.jobLabel()))
					.toArray(StoredEntry[]::new);

			dirty = false;
			lastFlushMillis = System.currentTimeMillis();

			Path file = path(cb);
			BackgroundWriter.submit(() -> write(file, toStore));
		}

		private static void write(Path file, StoredFile toStore) {
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, GSON.toJson(toStore), StandardCharsets.UTF_8);
			} catch (IOException e) {
				PrimeMoneyClient.LOGGER.warn("Konnte {} nicht schreiben", file, e);
			}
		}

		private static Path path(String cb) {
			String fileName = cb.isEmpty() ? "primemoney-history.json" : "primemoney-history-" + cb + ".json";
			return FabricLoader.getInstance().getConfigDir().resolve(fileName);
		}
	}

	/** Plain field classes rather than records: keeps Gson (de)serialization simple and predictable. */
	private static final class StoredFile {
		long hourCutoff;
		long dayCutoff;
		StoredEntry[] events;
	}

	private static final class StoredEntry {
		long t;
		double money;
		double xp;
		String job;

		StoredEntry(long t, double money, double xp, String job) {
			this.t = t;
			this.money = money;
			this.xp = xp;
			this.job = job;
		}
	}
}
