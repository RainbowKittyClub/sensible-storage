#!/usr/bin/env python3
"""Turn this mod's datagen-produced block models into files a human can open.

Reads one or more generated geometry models (the ones with `elements`, under
`src/main/generated/assets/plankedchests/models/block/`) and writes:

  * a Blockbench-loadable Java Block/Item Model, with the cubes named and the
    texture bound to a real path instead of the `#key` the generated file leaves
    dangling, and
  * a Wavefront OBJ + MTL of the same geometry, for Blender.

The OBJ unwrap is not guesswork. It follows the four vanilla contracts that
decide where a block model's `uv` actually lands, read out of 26.2:

  FaceBakery.bakeVertex        position = FaceInfo[face].infos[i].select(from, to)
  CuboidFace.UVs.getVertexU    i in {0,1} -> uv[0], else uv[2]
  CuboidFace.UVs.getVertexV    i in {0,3} -> uv[1], else uv[3]
  FaceInfo                     the per-face vertex order reproduced below

`uv` is therefore unsorted on purpose: uv[0] > uv[2] mirrors the face, which is
how the shulker's inner wall faces are written, and this exporter carries that
through rather than normalising it away.

Usage:
  export_model.py --out DIR --name shulker_box \
      --texture assets/minecraft/textures/entity/shulker/shulker.png \
      --texture-ref minecraft:entity/shulker/shulker \
      --texture-key shulker \
      --part /path/shulker_base.json --part /path/shulker_lid.json \
      --cube-names west,east,north,south,floor,lid
"""

import argparse
import json
import os
import shutil

# net.minecraft.client.renderer.FaceInfo, verbatim. Each face lists its four
# vertices as (x, y, z) picks from the element's min/max corner - 0 for min,
# 1 for max - in the order the baker walks them.
FACE_VERTICES = {
    "down":  [(0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)],
    "up":    [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)],
    "north": [(1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)],
    "south": [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)],
    "west":  [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)],
    "east":  [(1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)],
}

FACE_NORMALS = {
    "down": (0, -1, 0), "up": (0, 1, 0),
    "north": (0, 0, -1), "south": (0, 0, 1),
    "west": (-1, 0, 0), "east": (1, 0, 0),
}


def vertex_uv(uv, index):
    """CuboidFace.UVs.getVertexU/getVertexV for one vertex of a face."""
    u = uv[0] if index in (0, 1) else uv[2]
    v = uv[1] if index in (0, 3) else uv[3]
    return u, v


def load_elements(paths, cube_names):
    """Every element across the given models, in order, each tagged with a name."""
    elements = []
    for path in paths:
        with open(path) as handle:
            elements.extend(json.load(handle)["elements"])
    if cube_names and len(cube_names) != len(elements):
        raise SystemExit(
            f"--cube-names has {len(cube_names)} entries for {len(elements)} elements")
    for index, element in enumerate(elements):
        element["name"] = cube_names[index] if cube_names else f"cube_{index + 1}"
    return elements


def write_blockbench(out_dir, name, elements, texture_key, texture_ref):
    """A Java Block model with named cubes and the texture actually bound.

    The shipped geometry files bind only `particle` and leave `#<key>` for a
    per-colour child to fill in, which Blockbench cannot resolve on its own.
    """
    model = {
        "credit": "planked-chests: exported from datagen, not a source of truth",
        "texture_size": [64, 64],
        "textures": {texture_key: texture_ref, "particle": f"#{texture_key}"},
        "elements": elements,
    }
    path = os.path.join(out_dir, "assets", "plankedchests", "models", "block", f"{name}.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as handle:
        json.dump(model, handle, indent=2)
        handle.write("\n")
    return path


def write_obj(out_dir, name, elements, texture_png):
    """The same geometry as OBJ + MTL, one object per cube."""
    obj_dir = os.path.join(out_dir, "blender")
    os.makedirs(obj_dir, exist_ok=True)
    shutil.copy(texture_png, os.path.join(obj_dir, os.path.basename(texture_png)))

    lines = [f"# planked-chests {name}, exported from datagen",
             "# 1 unit = 1 block; Minecraft axes (Y up, north -Z)",
             f"mtllib {name}.mtl", ""]
    # OBJ indices are 1-based and run across the whole file, so they are counted
    # here rather than per object.
    vertex_count = 0
    uv_count = 0
    normal_count = 0

    for element in elements:
        faces = element.get("faces", {})
        if not faces:
            continue
        lines.append(f"o {element['name']}")
        lines.append("usemtl texture")
        x0, y0, z0 = element["from"]
        x1, y1, z1 = element["to"]
        corners = ((x0 / 16, y0 / 16, z0 / 16), (x1 / 16, y1 / 16, z1 / 16))

        for face, data in faces.items():
            uv = data["uv"]
            nx, ny, nz = FACE_NORMALS[face]
            lines.append(f"vn {nx} {ny} {nz}")
            normal_count += 1
            for index, (px, py, pz) in enumerate(FACE_VERTICES[face]):
                lines.append(f"v {corners[px][0]:.6f} {corners[py][1]:.6f} {corners[pz][2]:.6f}")
                u, v = vertex_uv(uv, index)
                # Model uv is 0-16 over the whole sheet with v growing downward;
                # OBJ measures v from the bottom, hence the flip.
                lines.append(f"vt {u / 16:.6f} {1 - v / 16:.6f}")
            base = vertex_count
            vertex_count += 4
            uv_count += 4
            corner_refs = " ".join(
                f"{base + i + 1}/{base + i + 1}/{normal_count}" for i in range(4))
            lines.append(f"f {corner_refs}")
        lines.append("")

    with open(os.path.join(obj_dir, f"{name}.obj"), "w") as handle:
        handle.write("\n".join(lines))

    # illum 1 turns specular off; map_d is what makes Blender wire the sheet's
    # alpha up, which matters because most of this one is transparent.
    with open(os.path.join(obj_dir, f"{name}.mtl"), "w") as handle:
        handle.write("newmtl texture\n"
                     "Ka 1.000 1.000 1.000\n"
                     "Kd 1.000 1.000 1.000\n"
                     "d 1.000\n"
                     "illum 1\n"
                     f"map_Kd {os.path.basename(texture_png)}\n"
                     f"map_d {os.path.basename(texture_png)}\n")
    return os.path.join(obj_dir, f"{name}.obj")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--texture", required=True, help="PNG to copy beside the OBJ")
    parser.add_argument("--texture-ref", required=True, help="what the JSON binds, e.g. ns:path")
    parser.add_argument("--texture-key", required=True, help="the model's texture variable")
    parser.add_argument("--part", action="append", required=True, help="a generated model JSON")
    parser.add_argument("--cube-names", default="", help="comma-separated, one per element")
    args = parser.parse_args()

    names = [n for n in args.cube_names.split(",") if n]
    elements = load_elements(args.part, names)
    print(write_blockbench(args.out, args.name, elements, args.texture_key, args.texture_ref))
    print(write_obj(args.out, args.name, elements, args.texture))


if __name__ == "__main__":
    main()
