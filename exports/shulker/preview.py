"""Render an exported OBJ the way the game would draw it, without the game.

    blender -b -P preview.py -- blender/shulker_box.obj out/shulker

Writes `<prefix>_closed.png`, `<prefix>_open.png` and `<prefix>_base.png`.

Three things here are not decoration:

**Back faces are deleted, not shaded away.** A block model culls them and the
entity model this was transcribed from does not, which is the whole reason each
part carries an inverted twin — so a preview that drew both sides would say
nothing about whether the model works. Culling is done by deleting the polygons
that face away from this fixed orthographic camera, rather than by a Backfacing
shader mix, because the twin sits exactly coincident with its original: a mix
leaves the renderer with two surfaces at one depth and it speckles, where
deleting leaves exactly the one quad the game would rasterise.

**The lights cast no shadows.** Minecraft lights an item model with plain
directional terms and no occlusion at all, so a ray-traced interior would go
black behind the lid and misreport a model that is fine.

**The sheet is sampled nearest-neighbour** with straight alpha, since a 64x64
entity sheet interpolated smoothly is unreadable and much of it is transparent
on purpose.

The open pose is `ShulkerCartDisplay` at progress 1: the lid rises LID_RISE
(0.5 block) and turns LID_TURN (270 degrees) about the vertical axis through
the box centre.
"""

import math
import sys

import bmesh
import bpy
import mathutils

LID_RISE = 0.5
LID_TURN = 270.0

obj_path, out_prefix = sys.argv[-2], sys.argv[-1]

# Direction the camera looks, and where it sits relative to what it is framing.
# High and to the side, so an open lid shows the cavity and not just a rim.
VIEW_OFFSET = mathutils.Vector((1.0, -1.0, 1.4)).normalized()


def world_bounds(objects):
    """Min and max corner across the given objects, in world space."""
    corners = [obj.matrix_world @ mathutils.Vector(corner)
               for obj in objects for corner in obj.bound_box]
    return (mathutils.Vector([min(c[i] for c in corners) for i in range(3)]),
            mathutils.Vector([max(c[i] for c in corners) for i in range(3)]))


def setup_material(mat):
    tree = mat.node_tree
    if tree is None:
        return
    bsdf = tree.nodes.get('Principled BSDF')
    tex = next((n for n in tree.nodes if n.type == 'TEX_IMAGE'), None)
    if not (bsdf and tex):
        return
    tex.interpolation = 'Closest'
    tex.image.alpha_mode = 'STRAIGHT'
    tree.links.new(bsdf.inputs['Alpha'], tex.outputs['Alpha'])
    bsdf.inputs['Roughness'].default_value = 1.0
    bsdf.inputs['Specular IOR Level'].default_value = 0.0


def cull_backfaces(objects, view_direction):
    """Delete every polygon facing away from the camera.

    Exact for an orthographic camera, and the only culling that behaves when a
    model carries coincident inward and outward surfaces.
    """
    for obj in objects:
        mesh = bmesh.new()
        mesh.from_mesh(obj.data)
        rotation = obj.matrix_world.to_3x3()
        facing_away = [face for face in mesh.faces
                       if (rotation @ face.normal).normalized().dot(view_direction) >= 0]
        bmesh.ops.delete(mesh, geom=facing_away, context='FACES')
        mesh.to_mesh(obj.data)
        mesh.free()


def add_light(scene, name, energy, rotation):
    data = bpy.data.lights.new(name, type='SUN')
    data.energy = energy
    # No occlusion, as the item shader has none.
    data.use_shadow = False
    light = bpy.data.objects.new(name, data)
    scene.collection.objects.link(light)
    light.rotation_euler = rotation
    return light


def build(open_pose, hide_lid):
    """A fresh scene in the given pose, culled and lit, ready to render."""
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.wm.obj_import(filepath=obj_path)
    for mat in bpy.data.materials:
        setup_material(mat)

    meshes = [obj for obj in bpy.data.objects if obj.type == 'MESH']
    # Both the lid cube and its inverted twin, whichever names the export used.
    lid_parts = [obj for obj in meshes if obj.name.startswith('lid')]

    # The importer converts the OBJ's Y-up into Blender's Z-up, which also moves
    # the model off the axes it was written on, so placement is measured off
    # what actually landed rather than off the block coordinates in the file.
    low, high = world_bounds(meshes)
    centre = (low + high) * 0.5
    block = max(high - low)

    if open_pose:
        axis = mathutils.Vector((centre.x, centre.y, 0.0))
        # Composed onto matrix_world rather than set as a location and rotation:
        # the importer leaves its own axis conversion in that matrix, and the
        # turn is about the box's vertical centre line, not the object's origin.
        lift = (mathutils.Matrix.Translation(axis + mathutils.Vector((0, 0, LID_RISE * block)))
                @ mathutils.Matrix.Rotation(math.radians(LID_TURN), 4, 'Z')
                @ mathutils.Matrix.Translation(-axis))
        for part in lid_parts:
            part.matrix_world = lift @ part.matrix_world
    if hide_lid:
        # Partitioned before the removal, since a removed object cannot be named.
        meshes = [obj for obj in meshes if not obj.name.startswith('lid')]
        for part in lid_parts:
            bpy.data.objects.remove(part, do_unlink=True)

    scene = bpy.context.scene
    # Cycles on CPU: EEVEE wants a GL context and segfaults under `blender -b`.
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.samples = 32
    scene.render.resolution_x = 640
    scene.render.resolution_y = 640
    scene.render.film_transparent = True
    scene.view_settings.view_transform = 'Standard'

    world = bpy.data.worlds.new('world')
    world.use_nodes = True
    background = world.node_tree.nodes['Background']
    background.inputs['Color'].default_value = (1, 1, 1, 1)
    background.inputs['Strength'].default_value = 0.35
    scene.world = world

    add_light(scene, 'key', 2.2, (math.radians(45), 0, math.radians(35)))
    add_light(scene, 'fill', 1.6, (math.radians(115), 0, math.radians(215)))

    cam_data = bpy.data.cameras.new('cam')
    cam_data.type = 'ORTHO'
    # Room for the block plus the half-block the lid rises out of the top.
    cam_data.ortho_scale = block * 2.2
    cam = bpy.data.objects.new('cam', cam_data)
    scene.collection.objects.link(cam)
    scene.camera = cam
    # Aimed by tracking rather than by Euler angles, so the framing does not
    # depend on getting Blender's rotation order right.
    target = centre + mathutils.Vector((0, 0, block * 0.15))
    cam.location = target + VIEW_OFFSET * block * 4.0
    cam.rotation_euler = (-VIEW_OFFSET).to_track_quat('-Z', 'Y').to_euler()

    cull_backfaces(meshes, -VIEW_OFFSET)
    return scene


def render(path, open_pose=False, hide_lid=False):
    scene = build(open_pose, hide_lid)
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


render(out_prefix + '_closed.png')
render(out_prefix + '_open.png', open_pose=True)
# The base alone, for looking at the cavity with nothing above it.
render(out_prefix + '_base.png', open_pose=True, hide_lid=True)
