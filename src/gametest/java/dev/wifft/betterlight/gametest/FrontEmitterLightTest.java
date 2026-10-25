package dev.wifft.betterlight.gametest;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * Front emitters (lit furnaces, jack o'lanterns) emit nothing in the light engine; their light only exists in the
 * client overlay. Runs a standalone block light engine over fake chunks (the test server's own engine doesn't process
 * block changes reliably in test areas), with Betterlight's mixins applied.
 */
public class FrontEmitterLightTest {
	private static final BlockPos SOURCE = new BlockPos(8, 32, 8);
	private static final BlockState FURNACE = Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH);
	private static final BlockState LIT_FURNACE = FURNACE.setValue(AbstractFurnaceBlock.LIT, true);
	private static final BlockState JACK_O_LANTERN = Blocks.JACK_O_LANTERN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, Direction.SOUTH);

	@GameTest
	public void furnaceLitInPlaceEmitsNothing(GameTestHelper helper) {
		TestWorld world = new TestWorld();
		world.set(SOURCE, FURNACE);
		world.set(SOURCE, LIT_FURNACE);
		world.assertDark(helper);
		helper.succeed();
	}

	@GameTest
	public void furnaceLoadedLitEmitsNothing(GameTestHelper helper) {
		TestWorld world = new TestWorld();
		world.blocks.put(SOURCE, LIT_FURNACE);
		world.engine.propagateLightSources(new ChunkPos(0, 0));
		world.engine.runLightUpdates();
		world.assertDark(helper);
		helper.succeed();
	}

	@GameTest
	public void furnaceRemovesTorchLight(GameTestHelper helper) {
		torchReplacedBy(helper, LIT_FURNACE);
	}

	@GameTest
	public void jackOLanternRemovesTorchLight(GameTestHelper helper) {
		torchReplacedBy(helper, JACK_O_LANTERN);
	}

	private static void torchReplacedBy(GameTestHelper helper, BlockState emitter) {
		TestWorld world = new TestWorld();
		world.set(SOURCE, Blocks.TORCH.defaultBlockState());
		helper.assertTrue(world.light(SOURCE.above()) > 0, "control: the torch should light the block above it");
		world.set(SOURCE, emitter);
		world.assertDark(helper);
		helper.succeed();
	}

	private static final class TestWorld {
		final Map<BlockPos, BlockState> blocks = new HashMap<>();
		final Map<Long, FakeChunk> chunks = new HashMap<>();
		final LevelLightEngine engine;

		TestWorld() {
			LightChunkGetter getter = new LightChunkGetter() {
				@Override
				public LightChunk getChunkForLighting(int x, int z) {
					return chunks.computeIfAbsent(ChunkPos.pack(x, z), k -> new FakeChunk(TestWorld.this, x, z));
				}

				@Override
				public BlockGetter getLevel() {
					return new FakeChunk(TestWorld.this, 0, 0);
				}
			};
			this.engine = new LevelLightEngine(getter, true, false);
			for (int cx = -1; cx <= 1; cx++) {
				for (int cz = -1; cz <= 1; cz++) {
					for (int sy = -1; sy <= 4; sy++) {
						this.engine.updateSectionStatus(SectionPos.of(cx, sy, cz), false);
					}
					this.engine.setLightEnabled(new ChunkPos(cx, cz), true);
					this.engine.retainData(new ChunkPos(cx, cz), true);
				}
			}
			this.engine.runLightUpdates();
		}

		void set(BlockPos pos, BlockState state) {
			this.blocks.put(pos, state);
			this.engine.checkBlock(pos);
			this.engine.runLightUpdates();
		}

		int light(BlockPos pos) {
			LayerLightEventListener listener = this.engine.getLayerListener(LightLayer.BLOCK);
			return listener.getLightValue(pos);
		}

		void assertDark(GameTestHelper helper) {
			StringBuilder found = new StringBuilder(" self=").append(this.light(SOURCE));
			boolean dark = this.light(SOURCE) == 0;
			for (Direction direction : Direction.values()) {
				int light = this.light(SOURCE.relative(direction));
				dark &= light == 0;
				found.append(' ').append(direction.getName()).append('=').append(light);
			}
			helper.assertTrue(dark, "block light around " + this.blocks.get(SOURCE) + " should be 0:" + found);
		}
	}

	private record FakeChunk(TestWorld world, int cx, int cz) implements LightChunk {
		@Override
		public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
			return null;
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			return this.world.blocks.getOrDefault(pos.immutable(), Blocks.AIR.defaultBlockState());
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return this.getBlockState(pos).getFluidState();
		}

		@Override
		public int getHeight() {
			return 64;
		}

		@Override
		public int getMinY() {
			return 0;
		}

		@Override
		public void findBlockLightSources(BiConsumer<BlockPos, BlockState> consumer) {
			this.world.blocks.forEach((pos, state) -> {
				if (state.getLightEmission() > 0 && SectionPos.blockToSectionCoord(pos.getX()) == this.cx && SectionPos.blockToSectionCoord(pos.getZ()) == this.cz) {
					consumer.accept(pos, state);
				}
			});
		}

		@Override
		public @Nullable ChunkSkyLightSources getSkyLightSources() {
			return null;
		}
	}
}
