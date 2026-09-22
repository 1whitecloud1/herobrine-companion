import bpy,json
rig=bpy.data.objects['Bones:Character'];char=bpy.data.objects['Character']
print(json.dumps({'modifiers':[{'type':m.type,'preserve_volume':getattr(m,'use_deform_preserve_volume',None)} for m in char.modifiers],
                  'bones':{b.name:{'segments':b.bbone_segments,'inherit_scale':b.inherit_scale} for b in rig.data.bones}}))
