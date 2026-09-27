package net.primeblocks.money.job;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

import net.primeblocks.money.mixin.PlayerTabOverlayAccessor;

/**
 * Works out which citybuild the player is on, so earnings can be kept separate per CB - farming
 * CB1 at 500k/h says nothing about CB2, and mixing the two into one "last 24h" figure would be
 * meaningless the moment a player splits time between them.
 *
 * <p>Ported from PrimeSlots' {@code CityBuildDetector}, which solved the same problem for the same
 * server: PrimeBlocks writes the current citybuild into the tab list footer - "Du befindest dich
 * derzeit auf: CityBuild-1 (Farmwelt-5)" - checked first because it stays correct even inside a farm
 * world, which otherwise carries no CB marker of its own. The farm and plot worlds belonging to a
 * CB are therefore folded into that CB's own bucket automatically, with no special-casing needed -
 * the footer already names the owning CB from inside them.
 */
public final class CityBuildDetector {
	/**
	 * Matches the long form the tab footer uses ("CityBuild-1"), the short form ("CB2", "CB 3",
	 * "CB-2") in case it appears elsewhere, and the plot world's own footer spelling
	 * ("CityBuild-1A", "-1B", ...) - a plot instance letter directly glued to the number, with no
	 * word boundary between them for {@code \b} to anchor on. The trailing letter is matched but
	 * not captured, so a plot instance folds into the same CB as its farm worlds instead of one
	 * that never matched anything before this and quietly became its own, empty bucket.
	 */
	private static final Pattern CITYBUILD =
			Pattern.compile("\\b(?:cb|citybuild)\\s*-?\\s*([1-9][0-9]?)[a-z]?\\b",
					Pattern.CASE_INSENSITIVE);

	private CityBuildDetector() {
	}

	/** The CB id (e.g. {@code "cb2"}) inferred from the server, or {@code null} if undetermined. */
	public static String detect(Minecraft minecraft) {
		for (String candidate : candidateTexts(minecraft)) {
			String cb = fromText(candidate);

			if (cb != null) {
				return cb;
			}
		}

		return null;
	}

	/** The CB id mentioned in one string, or {@code null}. */
	public static String fromText(String text) {
		if (text == null) {
			return null;
		}

		Matcher matcher = CITYBUILD.matcher(text);
		return matcher.find() ? "cb" + matcher.group(1) : null;
	}

	/** Every server-provided string the detector inspects, most authoritative first. */
	private static List<String> candidateTexts(Minecraft minecraft) {
		List<String> texts = new ArrayList<>();
		ClientPacketListener connection = minecraft.getConnection();

		if (connection == null) {
			return texts;
		}

		addTabList(minecraft, texts);

		Scoreboard scoreboard = connection.scoreboard();
		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);

		if (sidebar != null) {
			texts.add(sidebar.getDisplayName().getString());

			for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
				if (!entry.isHidden()) {
					texts.add(entry.ownerName().getString());
				}
			}
		}

		for (Objective objective : scoreboard.getObjectives()) {
			texts.add(objective.getName());
			texts.add(objective.getDisplayName().getString());
		}

		if (minecraft.level != null) {
			texts.add(minecraft.level.dimension().identifier().toString());
		}

		if (minecraft.getCurrentServer() != null) {
			texts.add(minecraft.getCurrentServer().ip);
			texts.add(minecraft.getCurrentServer().name);
		}

		texts.removeIf(text -> text == null || text.isBlank());
		return texts;
	}

	private static void addTabList(Minecraft minecraft, List<String> texts) {
		if (minecraft.gui == null) {
			return;
		}

		PlayerTabOverlay tabList = minecraft.gui.hud.getTabList();

		if (!(tabList instanceof PlayerTabOverlayAccessor accessor)) {
			return;
		}

		// Footer before header: the footer is where the current server is named.
		addComponent(texts, accessor.primemoney$getFooter());
		addComponent(texts, accessor.primemoney$getHeader());
	}

	private static void addComponent(List<String> texts, Component component) {
		if (component != null) {
			texts.add(component.getString());
		}
	}
}
