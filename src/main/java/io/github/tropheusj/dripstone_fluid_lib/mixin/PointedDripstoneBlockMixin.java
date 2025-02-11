package io.github.tropheusj.dripstone_fluid_lib.mixin;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.tropheusj.dripstone_fluid_lib.Constants;
import io.github.tropheusj.dripstone_fluid_lib.DripstoneInteractingFluid;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PointedDripstoneBlock;
import net.minecraft.block.PointedDripstoneBlock.DrippingFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.WorldView;
import net.minecraft.world.event.GameEvent;

@Mixin(value = PointedDripstoneBlock.class, priority = 1200)
public abstract class PointedDripstoneBlockMixin {

	@Shadow
	private static boolean isHeldByPointedDripstone(BlockState state, WorldView world, BlockPos pos) {
		throw new RuntimeException("Mixin application failed!");
	}

	@Shadow
	@Nullable
	private static BlockPos getTipPos(BlockState state, WorldAccess world, BlockPos pos, int range, boolean allowMerged) {
		throw new RuntimeException("Mixin application failed!");
	}

	@Shadow
	private static Fluid getDripFluid(World world, Fluid fluid) {
		throw new RuntimeException("Mixin application failed!");
	}

	@Shadow
	@Nullable
	private static BlockPos getCauldronPos(World world, BlockPos pos, Fluid fluid) {
		throw new RuntimeException("Mixin application failed!");
	}

	@Shadow
	private static Optional<DrippingFluid> getFluid(World world, BlockPos pos, BlockState state) {
		throw new RuntimeException("Mixin application failed!");
	}

	@Inject(method = "dripTick", at = @At("HEAD"), cancellable = true)
	private static void onDripTick(BlockState state, ServerWorld world, BlockPos pos, float dripChance, CallbackInfo ci) {
		if (isHeldByPointedDripstone(state, world, pos)) {
			Optional<PointedDripstoneBlock.DrippingFluid> optional = getFluid(world, pos, state);
			if (optional.isPresent()) {
				Fluid fluid = optional.get().fluid();
				float f;
				if (fluid == Fluids.WATER) {
					f = 0.17578125F;
				} else {
					if (fluid != Fluids.LAVA) {
						// custom fluid chance
						if (fluid instanceof DripstoneInteractingFluid customFluid) {
							f = customFluid.getFluidDripChance(world, optional.get());
						} else return;
					}

					f = 0.05859375F;
				}

				if (!(dripChance >= f)) {
					BlockPos blockPos = getTipPos(state, world, pos, 11, false);
					if (blockPos != null) {
						if (optional.get().sourceState().isOf(Blocks.MUD) && fluid == Fluids.WATER) {
							BlockState blockState = Blocks.CLAY.getDefaultState();
							world.setBlockState(optional.get().pos(), blockState);
							Block.pushEntitiesUpBeforeBlockChange(
									optional.get().sourceState(), blockState, world, optional.get().pos()
							);
							world.emitGameEvent(GameEvent.BLOCK_CHANGE, optional.get().pos(), GameEvent.Emitter.of(blockState));
							world.syncWorldEvent(WorldEvents.POINTED_DRIPSTONE_DRIPS, blockPos, 0);
						} else {
							BlockPos blockPos2 = getCauldronPos(world, blockPos, fluid);
							if (blockPos2 != null) {
								world.syncWorldEvent(WorldEvents.POINTED_DRIPSTONE_DRIPS, blockPos, 0);
								int i = blockPos.getY() - blockPos2.getY();
								int j = 50 + i;
								BlockState blockState2 = world.getBlockState(blockPos2);
								world.createAndScheduleBlockTick(blockPos2, blockState2.getBlock(), j);
							}
						}
					}
				}
			}
		}
		ci.cancel();
	}

	@Inject(method = "createParticle(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/fluid/Fluid;)V",
			at = @At("HEAD"), cancellable = true)
	private static void onCreateParticle(World world, BlockPos pos, BlockState state, Fluid fluid, CallbackInfo ci) {
		Vec3d modelOffset = state.getModelOffset(world, pos);
		double x = pos.getX() + 0.5 + modelOffset.x;
		double y = ((pos.getY() + 1) - 0.6875F) - 0.0625;
		double z = pos.getZ() + 0.5 + modelOffset.z;
		Fluid dripFluid = getDripFluid(world, fluid);
		ParticleEffect particleEffect;
		if (dripFluid instanceof DripstoneInteractingFluid interactingFluid) {
			particleEffect = Constants.FLUIDS_TO_PARTICLES.get(interactingFluid).hang();
		} else {
			particleEffect = dripFluid.isIn(FluidTags.LAVA) ? ParticleTypes.DRIPPING_DRIPSTONE_LAVA : ParticleTypes.DRIPPING_DRIPSTONE_WATER;
		}
		world.addParticle(particleEffect, x, y, z, 0.0, 0.0, 0.0);
		ci.cancel();
	}

	@Inject(method = "canGrow(Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;)Z", at = @At("HEAD"), cancellable = true)
	private static void onCanGrow(BlockState dripstoneBlockState, BlockState fluidState, CallbackInfoReturnable<Boolean> cir) {
		Fluid fluid = fluidState.getFluidState().getFluid();
		boolean growsDripstone = fluidState.isOf(Blocks.WATER);
		if (fluid instanceof DripstoneInteractingFluid interactingFluid) {
			growsDripstone = interactingFluid.growsDripstone(fluidState);
		}

		cir.setReturnValue(dripstoneBlockState.isOf(Blocks.DRIPSTONE_BLOCK) && growsDripstone && fluidState.getFluidState().isStill());
	}

	@Inject(method = "isFluidLiquid", at = @At("HEAD"), cancellable = true)
	private static void dripstone_fluid_lib$makeCustomFluidsValid(Fluid fluid, CallbackInfoReturnable<Boolean> cir) {
		if (fluid instanceof DripstoneInteractingFluid) {
			cir.setReturnValue(true);
		}
	}
}
