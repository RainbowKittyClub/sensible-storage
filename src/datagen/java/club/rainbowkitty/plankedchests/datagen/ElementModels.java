package club.rainbowkitty.plankedchests.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/**
 * Box-UV unwrapping, and the small JSON files every display-entity part needs beside its geometry.
 *
 * <p>Shared by the two providers that transcribe a vanilla entity model into block-model
 * {@code elements} — {@link ChestElementModelProvider} and {@link ShulkerElementModelProvider}.
 * Both read a 64×64 entity sheet with {@code ModelPart.Cube}'s own unwrap, and the winding fix in
 * {@link #box} was expensive enough to find once that a second copy of it would be a liability.
 *
 * <p>The texture variable is the caller's, because the two name theirs differently
 * ({@code #chest}, {@code #shulker}) and a shared literal would be the wrong kind of coupling.
 */
final class ElementModels {
    // Vanilla's texture-size quirk: block/item model face "uv" is always 0-16 regardless of the
    // texture's real resolution, so pixel coordinates on a 64x64 sheet are scaled down by 16/64.
    static final float UV_SCALE = 16.0f / 64.0f;

    private ElementModels() {
    }

    /**
     * One {@code elements[]} cuboid, box-UV-unwrapped exactly like {@code ModelPart.Cube} — the
     * same formula every vanilla entity-model cube uses: {@code texOffs (u,v)} and {@code size
     * (w,h,d)} tile six faces across the sheet.
     *
     * @param textureKey the model's texture variable, without its {@code #}
     * @param hidden a face to omit entirely, or null for all six; the double-chest seam uses it so
     *     halves join cleanly
     */
    static JsonObject box(String textureKey, float x, float y, float z, float w, float h, float d,
            float texU, float texV, @Nullable Direction hidden) {
        return box(textureKey, x, y, z, w, h, d, texU, texV, hidden, false);
    }

    /**
     * As {@link #box}, for a model whose geometry was turned the right way up on the way in.
     *
     * <p>The distinction is which convention the vanilla model was authored in.
     * {@code ChestModel} uses the block one — {@code PartPose.ZERO}, boxes running up from the
     * block's own origin — so its cuboids transcribe verbatim and every flip is left to the UVs
     * here. {@code ShulkerModel} uses the mob one — {@code PartPose.offset(0, 24, 0)} with boxes
     * running back from −16, Y growing downward — so its cuboids only land inside the block after
     * a {@code 24 − y} conversion, and that conversion has already supplied the flip.
     *
     * <p>Doing both is a double flip, and it looks like one: the lid's inner face on top and every
     * side upside down.
     *
     * @param flippedY whether the caller already converted the geometry out of the mob convention
     */
    static JsonObject box(String textureKey, float x, float y, float z, float w, float h, float d,
            float texU, float texV, @Nullable Direction hidden, boolean flippedY) {
        float u0 = texU;
        float u1 = u0 + d;
        float u2 = u1 + w;
        float u22 = u2 + w;
        float u3 = u2 + d;
        float u4 = u3 + w;
        float v0 = texV;
        float v1 = v0 + d;
        float v2 = v1 + h;

        // Raw ModelPart.Cube assigns DOWN u1,v0,u2,v1 / UP u2,v1,u22,v0 / WEST u0,v1,u1,v2 /
        // NORTH u1,v1,u2,v2 / EAST u2,v1,u3,v2 / SOUTH u3,v1,u4,v2 (ModelPart$Cube's constructor).
        JsonObject faces = new JsonObject();
        if (flippedY) {
            // The caller flipped the geometry, so the up and down regions trade places and the
            // sides keep raw's v order. They still read mirrored left-right, which is vertex
            // winding rather than the flip, so the u-pairs stay swapped.
            putFace(faces, textureKey, Direction.UP, hidden, u1, v0, u2, v1);
            putFace(faces, textureKey, Direction.DOWN, hidden, u2, v1, u22, v0);
            putFace(faces, textureKey, Direction.WEST, hidden, u1, v1, u0, v2);
            putFace(faces, textureKey, Direction.NORTH, hidden, u2, v1, u1, v2);
            putFace(faces, textureKey, Direction.EAST, hidden, u3, v1, u2, v2);
            putFace(faces, textureKey, Direction.SOUTH, hidden, u4, v1, u3, v2);
            return element(faces, x, y, z, w, h, d);
        }

        // The side faces' u- and v-pairs are both swapped from the raw unwrap (v2,v1 instead of
        // v1,v2; u1,u0 instead of u0,u1 etc.): entity-cube vertex winding reads each side face
        // bottom-to-top and mirrored left-right relative to how a block model's "uv" is read,
        // which without the swap rendered upside down and horizontally mirrored.
        putFace(faces, textureKey, Direction.DOWN, hidden, u1, v0, u2, v1);
        putFace(faces, textureKey, Direction.UP, hidden, u2, v1, u22, v0);
        putFace(faces, textureKey, Direction.WEST, hidden, u1, v2, u0, v1);
        putFace(faces, textureKey, Direction.NORTH, hidden, u2, v2, u1, v1);
        putFace(faces, textureKey, Direction.EAST, hidden, u3, v2, u2, v1);
        putFace(faces, textureKey, Direction.SOUTH, hidden, u4, v2, u3, v1);

        return element(faces, x, y, z, w, h, d);
    }

    /**
     * The inward-facing twin of a transcribed cube — the same box with {@code from} and {@code to}
     * swapped — which is the only way a block model can show a cube's inside.
     *
     * <p>A block model culls back faces; the entity renderers these cubes come from do not. So a
     * faithful transcription of a shulker box or a chest shows nothing of its own interior, which
     * for an open one is most of what there is to see. Adding this twin beside the original
     * restores exactly what no-cull drew, and nothing else: an inward surface on every wall,
     * coincident with the outward one, so only ever one of the pair faces the camera and the two
     * never fight over a pixel.
     *
     * <p>The swap is not a quirk of the file format but a consequence of how the baker reads it.
     * {@code FaceBakery}'s vertex picker takes {@code from} for every {@code MIN_*} extent and
     * {@code to} for every {@code MAX_*}, with no sorting, so inverting the corners puts each face
     * on the opposite wall still pointing the way its own name says — that is, inward. Vanilla
     * validates only that each coordinate falls within -16..32, and {@code recalculateWinding}
     * leaves these quads alone because each already agrees with its own computed normal.
     *
     * <p>Each face therefore moves to its opposite key and takes one mirrored axis, since a quad
     * seen from behind is a quad mirrored: the four sides swap their v pair, up and down their u.
     */
    static JsonObject invertedTwin(JsonObject element) {
        JsonObject twin = new JsonObject();
        twin.add("from", copyVec(element.getAsJsonArray("to")));
        twin.add("to", copyVec(element.getAsJsonArray("from")));

        JsonObject faces = new JsonObject();
        JsonObject source = element.getAsJsonObject("faces");
        for (Direction dir : Direction.values()) {
            JsonElement face = source.get(dir.getSerializedName());
            if (face == null) {
                continue;
            }
            faces.add(dir.getOpposite().getSerializedName(),
                    mirroredFace(face.getAsJsonObject(), dir.getAxis() == Direction.Axis.Y));
        }
        twin.add("faces", faces);
        return twin;
    }

    // {"textures":{"particle":"#<key>"},"elements":[...]}
    static JsonObject model(String textureKey, JsonObject... elements) {
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", "#" + textureKey);
        JsonArray array = new JsonArray();
        for (JsonObject element : elements) {
            array.add(element);
        }
        JsonObject root = new JsonObject();
        root.add("textures", textures);
        root.add("elements", array);
        return root;
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
    static JsonObject itemDefinition(Identifier model) {
        JsonObject def = new JsonObject();
        def.addProperty("type", "minecraft:model");
        def.addProperty("model", model.toString());
        JsonObject root = new JsonObject();
        root.add("model", def);
        return root;
    }

    // Constructs an element with bounding box and faces.
    private static JsonObject element(JsonObject faces, float x, float y, float z,
            float w, float h, float d) {
        JsonObject element = new JsonObject();
        element.add("from", vec(x, y, z));
        element.add("to", vec(x + w, y + h, z + d));
        element.add("faces", faces);
        return element;
    }

    // Adds a scaled face to the faces object, or skips it if hidden.
    private static void putFace(JsonObject faces, String textureKey, Direction face,
            @Nullable Direction hidden, float u0, float v0, float u1, float v1) {
        if (face == hidden) {
            return;
        }
        JsonArray uv = new JsonArray();
        uv.add(u0 * UV_SCALE);
        uv.add(v0 * UV_SCALE);
        uv.add(u1 * UV_SCALE);
        uv.add(v1 * UV_SCALE);
        JsonObject faceObj = new JsonObject();
        faceObj.add("uv", uv);
        faceObj.addProperty("texture", "#" + textureKey);
        faces.add(face.getSerializedName(), faceObj);
    }

    // One face of the twin: the same patch of sheet with a single axis mirrored, which is what the
    // original quad looks like from the other side.
    private static JsonObject mirroredFace(JsonObject face, boolean vertical) {
        JsonArray uv = face.getAsJsonArray("uv");
        JsonArray mirrored = new JsonArray();
        // Vertical faces are mirrored in u and the four sides in v, which is the vertex order the
        // baker walks each by rather than anything about the texture.
        int[] order = vertical ? new int[] {2, 1, 0, 3} : new int[] {0, 3, 2, 1};
        for (int index : order) {
            mirrored.add(uv.get(index).getAsFloat());
        }

        JsonObject result = new JsonObject();
        result.add("uv", mirrored);
        result.addProperty("texture", face.get("texture").getAsString());
        return result;
    }

    // Copies a JSON array of floats.
    private static JsonArray copyVec(JsonArray source) {
        JsonArray copy = new JsonArray();
        for (JsonElement value : source) {
            copy.add(value.getAsFloat());
        }
        return copy;
    }

    // Creates a JSON array from three coordinates.
    private static JsonArray vec(float x, float y, float z) {
        JsonArray array = new JsonArray();
        array.add(x);
        array.add(y);
        array.add(z);
        return array;
    }
}
