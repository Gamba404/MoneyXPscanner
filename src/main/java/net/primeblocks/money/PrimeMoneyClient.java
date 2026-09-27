package net.primeblocks.money;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import net.primeblocks.money.debug.DebugFileLogger;
import net.primeblocks.money.debug.TrackingDiagnostics;
import net.primeblocks.money.io.BackgroundWriter;
import net.primeblocks.money.hud.MoneyXpHudEditScreen;
import net.primeblocks.money.hud.MoneyXpHudElement;
import net.primeblocks.money.hud.MoneyXpOverlayRenderer;
import net.primeblocks.money.job.CityBuildDetector;
import net.primeblocks.money.job.JobChatListener;
import net.primeblocks.money.job.JobHudTracker;
import net.primeblocks.money.job.JobStatsRegistry;
import net.primeblocks.money.tracking.EarningsHistory;
import net.primeblocks.money.tracking.ProjectionAccuracy;

/**
 * Client entrypoint.
 *
 * <p>Earnings come from PrimeBlocks' own live job HUD, tapped by {@link JobHudTracker} - see there
 * for why that is the source rather than the chat payout messages. {@link JobStatsRegistry} keeps
 * the per-job and combined running figures, {@link EarningsHistory} the 60min/24h totals, and
 * {@link MoneyXpOverlayRenderer} draws it all.
 */
public final class PrimeMoneyClient implements ClientModInitializer {
	public static final String MOD_ID = "primemoney";
	public static final Logger LOGGER = LoggerFactory.getLogger("PrimeMoney");

	private static final Identifier HUD_ELEMENT_ID = Identifier.fromNamespaceAndPath(MOD_ID, "money_xp_overlay");

	/** How often the tab list/scoreboard are scanned for which CB the player is currently on. */
	private static final long CB_CHECK_INTERVAL_MILLIS = 2000;

	@Override
	public void onInitializeClient() {
		PrimeMoneyConfig config = PrimeMoneyConfig.load();
		// Rewritten on every start so fields added by a mod update show up in the file with their
		// defaults instead of staying invisible until it is deleted by hand.
		config.save();

		DebugFileLogger.start();
		DebugFileLogger.verbose = config.debugLogAll;

		JobStatsRegistry registry = new JobStatsRegistry();
		// Samples older than the longest window anyone asks for are dead weight.
		registry.setRetentionMillis(config.rateLongWindowMillis());

		EarningsHistory history = EarningsHistory.load();
		ProjectionAccuracy accuracy = new ProjectionAccuracy();
		TrackingDiagnostics diagnostics = new TrackingDiagnostics(config, registry, accuracy);

		JobHudTracker hudTracker = new JobHudTracker(config, registry, history, diagnostics);
		hudTracker.register();

		new JobChatListener(config, hudTracker).register();

		MoneyXpOverlayRenderer renderer = new MoneyXpOverlayRenderer(registry, history, config);
		TrackerControls controls = new TrackerControls(registry, history);
		HudElementRegistry.addLast(HUD_ELEMENT_ID, new MoneyXpHudElement(renderer, config));

		// Ordinary key mappings, so they appear under Options -> Controls -> Miscellaneous and can
		// be rebound there. F8/F9 are unbound in vanilla.
		KeyMapping editKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.primemoney.edit_hud", GLFW.GLFW_KEY_F8, KeyMapping.Category.MISC));
		KeyMapping toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.primemoney.toggle_hud", GLFW.GLFW_KEY_F9, KeyMapping.Category.MISC));

		// Throttled: scanning the tab list and scoreboard for the CB marker is cheap once, not
		// worth doing sixty to two-hundred-forty times a second.
		long[] lastCbCheckMillis = {0};
		String[] lastLoggedCb = {null};

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			long now = System.currentTimeMillis();
			DebugFileLogger.verbose = config.debugLogAll;

			if (now - lastCbCheckMillis[0] >= CB_CHECK_INTERVAL_MILLIS) {
				lastCbCheckMillis[0] = now;
				updateCityBuild(client, history, config, lastLoggedCb);
			}

			hudTracker.tick(now);
			history.tick(now);
			accuracy.tick(registry.combined().rateMeter(), config, now);
			diagnostics.tick(now);
			DebugFileLogger.tick(now);
			handleKeys(client, editKey, toggleKey, renderer, config, controls);
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			registry.reset();
			accuracy.clear();
			// Right away rather than waiting for the next throttled check, so the very first
			// numbers shown after joining are already scoped to the correct CB.
			updateCityBuild(client, history, config, lastLoggedCb);
		});
		// Also fires on every hop to the farm or plot world, so nothing here may block: the work is
		// queued onto the background writer and the client carries on loading.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			hudTracker.onDisconnect();
			history.flush();
			DebugFileLogger.flush();
		});

		// Quitting is the one moment worth waiting a beat for, so the last minutes still land.
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			hudTracker.onDisconnect();
			history.flush();
			DebugFileLogger.flush();
			BackgroundWriter.shutdown();
		});
	}

	/**
	 * Re-checks which CB the player is on and, if it changed, switches the history over to that
	 * CB's own bucket - see {@link EarningsHistory#setActiveCityBuild}.
	 *
	 * <p>The 60min/24h totals and their resets are the only thing this affects. The live "seit
	 * Login" session deliberately keeps running across a CB switch rather than resetting - it
	 * answers "what have I made since I logged in", not "on this CB specifically", and a player
	 * who hops from CB1 to CB2 mid-session would otherwise see their own current total vanish.
	 */
	private static void updateCityBuild(Minecraft client, EarningsHistory history,
			PrimeMoneyConfig config, String[] lastLoggedCb) {
		String detected = CityBuildDetector.detect(client);
		history.setActiveCityBuild(detected);

		if (config.debugLogTracking) {
			String active = history.activeCityBuild();

			if (!active.equals(lastLoggedCb[0])) {
				lastLoggedCb[0] = active;
				DebugFileLogger.log("[CB] aktiv: " + (active.isEmpty() ? "unbekannt" : active));
			}
		}
	}

	private static void handleKeys(Minecraft client, KeyMapping editKey, KeyMapping toggleKey,
			MoneyXpOverlayRenderer renderer, PrimeMoneyConfig config, TrackerControls controls) {
		boolean edit = drain(editKey);
		boolean toggle = drain(toggleKey);

		if (client.player == null || client.gui.screen() != null) {
			return;
		}

		if (toggle) {
			config.hudEnabled = !config.hudEnabled;
			config.save();
		}

		if (edit) {
			client.gui.setScreen(new MoneyXpHudEditScreen(renderer, config, controls));
		}
	}

	private static boolean drain(KeyMapping key) {
		boolean pressed = false;

		while (key.consumeClick()) {
			pressed = true;
		}

		return pressed;
	}
}
