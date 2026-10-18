package club.rainbowkitty.sensiblestorage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;

/** Reads the block a block display draws, private to it. */
@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayAccessor {
    /** The block state it draws. */
    @Invoker("getBlockState")
    BlockState sensiblestorage$blockState();
}
