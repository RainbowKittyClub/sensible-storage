package club.rainbowkitty.plankedchests.datagen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

// Identifier helpers shared by this package's providers.
final class Ids {
    private Ids() {
    }

    // A block's registry path, which is how every provider here names that block's generated files.
    static String blockPath(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }
}
