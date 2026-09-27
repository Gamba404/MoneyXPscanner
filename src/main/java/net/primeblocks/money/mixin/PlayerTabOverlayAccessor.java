package net.primeblocks.money.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;

/**
 * Exposes the tab list header and footer, which vanilla only lets you write.
 *
 * <p>PrimeBlocks names the current citybuild in the tab footer ("Du befindest dich derzeit auf:
 * CityBuild-1 (Farmwelt-5)"), which {@link net.primeblocks.money.job.CityBuildDetector} reads to
 * tell CB1/CB2/CB3 apart. Same idea PrimeSlots already uses for the same server.
 */
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
	@Accessor("header")
	Component primemoney$getHeader();

	@Accessor("footer")
	Component primemoney$getFooter();
}
