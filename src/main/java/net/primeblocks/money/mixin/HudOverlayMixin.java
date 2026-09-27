package net.primeblocks.money.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;

import net.primeblocks.money.debug.DebugFileLogger;
import net.primeblocks.money.debug.TextDump;
import net.primeblocks.money.job.OverlayMessageBus;

/**
 * Taps every overlay/action-bar message at its single choke point, {@code Hud#setOverlayMessage}.
 *
 * <p>Minecraft has two independent ways to put text above the hotbar: a system chat message with
 * its {@code overlay} flag set (which {@code ClientReceiveMessageEvents.ALLOW_GAME} sees), and the
 * dedicated {@code ClientboundSetActionBarTextPacket} - the packet behind {@code /title actionbar}
 * - which {@code ClientPacketListener#setActionBarText} forwards straight here, bypassing the chat
 * event system entirely. PrimeBlocks' live job HUD comes down that second path, which is why
 * listening on chat events alone never saw it.
 */
@Mixin(Hud.class)
public class HudOverlayMixin {
	@Inject(method = "setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"))
	private void primemoney$onSetOverlayMessage(Component string, boolean animate, CallbackInfo ci) {
		if (string == null) {
			return;
		}

		String text = string.getString();

		if (DebugFileLogger.verbose) {
			DebugFileLogger.log("[OVERLAY-DIRECT] " + TextDump.escapeNonAscii(text));
		}

		OverlayMessageBus.publish(text);
	}
}
