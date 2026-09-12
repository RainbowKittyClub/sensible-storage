package club.rainbowkitty.plankedchests.datagen;

import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

/**
 * The small JSON files a display-entity part needs beside its geometry: the thin per-wood model
 * that binds one sheet to a shared shape, and the item definition every model here is reached
 * through.
 *
 * <p>The box-UV unwrapping those shapes are built from is {@code EntityModelBoxes}, shaded in from
 * {@code rkcore}.
 */
final class ElementModels {
    private ElementModels() {
    }

    // {"parent":"<geometry>","textures":{"<key>":"<sprite>"}}
    static JsonObject variantModel(Identifier geometry, String textureKey, Identifier sprite) {
        JsonObject textures = new JsonObject();
        textures.addProperty(textureKey, sprite.toString());
        JsonObject root = new JsonObject();
        root.addProperty("parent", geometry.toString());
        root.add("textures", textures);
        return root;
    }

    // {"model":{"type":"minecraft:model","model":"<model>"}}
    // Every item definition this mod writes has this one-model shape, whether it points at a
    // flat icon or at a display-entity part.
    static JsonObject itemDefinition(Identifier model) {
        JsonObject def = new JsonObject();
        def.addProperty("type", "minecraft:model");
        def.addProperty("model", model.toString());
        JsonObject root = new JsonObject();
        root.add("model", def);
        return root;
    }
}
