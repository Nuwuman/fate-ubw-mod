# Convierte un modelo de ítem JSON (Java Block/Item) en un .bbmodel de Blockbench con su textura incrustada.
# Uso: python tools/item_to_bbmodel.py <modelo.json> <textura.png> <salida.bbmodel>
import sys, json, base64, uuid

src, png, out = sys.argv[1:4]
m = json.load(open(src, encoding='utf-8'))
data = base64.b64encode(open(png, 'rb').read()).decode()
elements = []
for i, e in enumerate(m['elements']):
    el = {"name": "cube", "box_uv": False, "render_order": "default", "rescale": False, "locked": False,
          "shade_direction_override": "", "light_emission": 0, "export": True, "scope": 0,
          "allow_mirror_modeling": True, "from": e['from'], "to": e['to'], "autouv": 0, "color": i % 8,
          "origin": [8, 8, 8], "faces": {}, "type": "cube", "uuid": str(uuid.uuid4())}
    r = e.get('rotation')
    if r:
        el['origin'] = r['origin']
        el['rotation'] = [r['angle'] if a == r['axis'] else 0 for a in 'xyz']
    for n, f in e['faces'].items():
        face = {"uv": f['uv'], "texture": 0}
        if f.get('rotation'):
            face['rotation'] = f['rotation']
        el['faces'][n] = face
    elements.append(el)
w = h = 64
d = {"meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False}, "name": out.split('/')[-1][:-8],
     "parent": "", "java_block_version": "26.3", "ambientocclusion": True, "front_gui_light": True,
     "visible_box": [1, 1, 0], "variable_placeholders": "", "multi_file_ruleset": "", "variable_placeholder_buttons": [],
     "unhandled_root_fields": {}, "resolution": {"width": 16, "height": 16}, "elements": elements, "groups": [],
     "outliner": [e['uuid'] for e in elements],
     "textures": [{"name": png.split('/')[-1], "path": "", "folder": "item", "namespace": "fate_ubw", "id": "0",
                   "group": "", "scope": 0, "width": w, "height": h, "uv_width": 16, "uv_height": 16, "particle": True,
                   "use_as_default": False, "layers_enabled": False, "sync_to_project": "", "file_format": "png",
                   "render_mode": "default", "render_sides": "auto", "wrap_mode": "limited", "pbr_channel": "color",
                   "fps": 7, "force_translucent": False, "frame_time": 1, "frame_order_type": "loop",
                   "frame_order": "", "frame_interpolate": False, "visible": True, "internal": True, "saved": False,
                   "uuid": str(uuid.uuid4()), "source": "data:image/png;base64," + data}],
     "display": m.get('display', {})}
json.dump(d, open(out, 'w', encoding='utf-8'), ensure_ascii=False)
print(len(elements), 'piezas')
