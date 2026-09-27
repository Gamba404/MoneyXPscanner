package net.primeblocks.money.hud;

import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.primeblocks.money.PrimeMoneyConfig;

/** Draws the money/XP overlay onto the in-game HUD. */
public final class MoneyXpHudElement implements HudElement {
	private final MoneyXpOverlayRenderer renderer;
	private final PrimeMoneyConfig config;

	public MoneyXpHudElement(MoneyXpOverlayRenderer renderer, PrimeMoneyConfig config) {
		this.renderer = renderer;
		this.config = config;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, DeltaTracker deltaTracker) {
		if (!config.hudEnabled) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player == null) {
			return;
		}

		List<HudLine> lines = renderer.buildLines();
		Font font = minecraft.font;
		float scale = config.scale();

		int boxX = MoneyXpOverlayRenderer.resolveX(config, context.guiWidth(),
				MoneyXpOverlayRenderer.scaledWidth(font, lines, scale));
		int boxY = MoneyXpOverlayRenderer.resolveY(config, context.guiHeight(),
				MoneyXpOverlayRenderer.scaledHeight(font, lines, scale));

		renderer.draw(context, font, lines, boxX, boxY, scale, config.backgroundColour());
	}
}
