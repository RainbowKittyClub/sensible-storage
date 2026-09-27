package club.rainbowkitty.sensiblestorage.datagen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import club.rainbowkitty.kalic0re.common.datagen.PngAssets;
import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.block.BookshelfBlocks;
import club.rainbowkitty.sensiblestorage.block.CraftingTableBlocks;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * Each wood's planks, copied through untouched to {@code textures/block/planks/<wood>}, for the
 * faces vanilla draws as bare planks: a crafting table's bottom and a bookshelf's two ends.
 *
 * <p>Copied rather than referenced as {@code <mod>:block/<wood>_planks}, because a client with only
 * the resource pack need not have a modded wood's textures, and a bookshelf's end is the top face
 * of its inventory icon.
 */
public final class PlankSpriteProvider implements DataProvider {
    private final PackOutput.PathProvider textures;

    /**
     * Constructs a provider for the copied plank sprites.
     *
     * @param output the Fabric datagen output
     */
    public PlankSpriteProvider(FabricPackOutput output) {
        this.textures = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures");
    }

    /** The copied plank sprite for a wood, e.g. {@code sensiblestorage:block/planks/birch}. */
    public static Identifier sprite(WoodType wood) {
        return SensibleStorage.id("block/planks/" + wood.id());
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        ClassLoader loader = PlankSpriteProvider.class.getClassLoader();
        Set<WoodType> woods = new LinkedHashSet<>(CraftingTableBlocks.tables().keySet());
        woods.addAll(BookshelfBlocks.bookshelves().keySet());
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (WoodType wood : woods) {
            writes.add(PngAssets.save(cache, this.textures.file(sprite(wood), "png"),
                    WoodTints.planks(loader, wood), "sensibleStoragePlankSprites"));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Sensible Storage Plank Sprites";
    }
}
