package dev.wifft.betterlight.mixin.client;

import dev.wifft.betterlight.duck.MovingBlockCellsHolder;
import dev.wifft.betterlight.duck.MovingBlockShadowsHolder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Regions are created on the main thread at extraction: copy the falling block cells inside them for the compile threads. */
@Mixin(RenderRegionCache.class)
public abstract class RenderRegionCacheMixin {
	@Inject(method = "createRegion", at = @At("RETURN"))
	private void betterlight$copyMovingBlockCells(ClientLevel level, long sectionNode, CallbackInfoReturnable<RenderSectionRegion> cir) {
		((MovingBlockCellsHolder) cir.getReturnValue()).betterlight$setMovingBlockCells(
			((MovingBlockShadowsHolder) level).betterlight$getMovingBlockShadows()
				.forRegion(SectionPos.x(sectionNode) - 1, SectionPos.y(sectionNode) - 1, SectionPos.z(sectionNode) - 1)
		);
	}
}
