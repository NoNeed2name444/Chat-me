"""Run with: blender --background --python pipeline/blender_export.py -- --input X --output Y."""
import argparse
import bpy

argv = bpy.sys.argv[bpy.sys.argv.index("--") + 1:]
parser = argparse.ArgumentParser()
parser.add_argument("--input", required=True)
parser.add_argument("--output", required=True)
args = parser.parse_args(argv)

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.obj(filepath=args.input)
for item in bpy.context.scene.objects:
    if item.type != "MESH":
        continue
    bpy.context.view_layer.objects.active = item
    item.select_set(True)
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    # Deliberately no smoothing: review must approve any shape-altering operation.
    item.select_set(False)
bpy.ops.wm.usd_export(filepath=args.output, export_materials=True)
