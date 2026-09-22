import bpy
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
WORK=ROOT/'build/scythe_mocap'
rig=bpy.data.objects['Bones:Character']
weapon=bpy.data.objects['Poem of the End | 原版镰刀']
char=bpy.data.objects['Character']
def mat(m): return [list(row) for row in m]
info={'file':bpy.data.filepath,'scene':bpy.context.scene.name,
      'objects':[{'name':o.name,'type':o.type,'hide_render':o.hide_render,'parent':o.parent.name if o.parent else None} for o in bpy.context.scene.objects],
      'rig_world':mat(rig.matrix_world),'weapon_world':mat(weapon.matrix_world),
      'weapon_modifiers':[{'name':m.name,'type':m.type} for m in weapon.modifiers],
      'bones':{b.name:{'parent':b.parent.name if b.parent else None,'rest':mat(b.matrix_local),
        'length':b.length,'use_connect':b.use_connect,'pose_basis':mat(rig.pose.bones[b.name].matrix_basis),
        'constraints':[{'type':c.type,'name':c.name,'mute':c.mute} for c in rig.pose.bones[b.name].constraints]} for b in rig.data.bones},
      'rig_drivers':len(rig.animation_data.drivers) if rig.animation_data else 0,
      'character_modifiers':[{'name':m.name,'type':m.type} for m in char.modifiers],
      'actions':[{'name':a.name,'frames':list(a.frame_range)} for a in bpy.data.actions],
      'images':[{'name':im.name,'packed':bool(im.packed_file),'path':im.filepath} for im in bpy.data.images],
      'ground':bpy.data.objects['Studio | ground'].location.z}
(WORK/'target_inspection.json').write_text(json.dumps(info,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:info[k] for k in ['rig_world','weapon_modifiers','character_modifiers','rig_drivers','bones','images']},ensure_ascii=True))
