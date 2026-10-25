package dev.wifft.betterlight.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.platform.Lighting;
import java.nio.ByteBuffer;
import net.minecraft.world.level.CardinalLighting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two extra vec4 in the Lighting UBO, read by include/light.glsl:
 * CardinalA = down, up, north, south; CardinalB = west, east, per-face flag, unused.
 * LEVEL gets the dimension's CardinalLighting and per-face terrain shading; the GUI/item entries keep vanilla two-light shading.
 */
@Mixin(Lighting.class)
public abstract class LightingMixin {
	@Unique
	private CardinalLighting.Type betterlight$levelType = CardinalLighting.Type.DEFAULT;

	@WrapOperation(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140SizeCalculator;get()I"))
	private static int betterlight$growUbo(Std140SizeCalculator calculator, Operation<Integer> original) {
		return original.call(calculator.putVec4().putVec4());
	}

	@Inject(method = "updateLevel", at = @At("HEAD"))
	private void betterlight$rememberLevelType(CardinalLighting.Type type, CallbackInfo ci) {
		this.betterlight$levelType = type;
	}

	@WrapOperation(method = "updateBuffer", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;get()Ljava/nio/ByteBuffer;"))
	private ByteBuffer betterlight$putCardinal(Std140Builder builder, Operation<ByteBuffer> original, @Local(argsOnly = true) Lighting.Entry entry) {
		boolean level = entry == Lighting.Entry.LEVEL;
		CardinalLighting cardinal = level ? this.betterlight$levelType.get() : CardinalLighting.DEFAULT;
		builder.putVec4(cardinal.down(), cardinal.up(), cardinal.north(), cardinal.south())
			.putVec4(cardinal.west(), cardinal.east(), level ? 1.0F : 0.0F, 0.0F);
		return original.call(builder);
	}
}
