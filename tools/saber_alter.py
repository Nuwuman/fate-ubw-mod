# Saber Alter: sus recursos salen de los de Saber, recoloreados (azul -> negro, plata -> acero oscuro, oro -> rojo
# sangre), y el modelo es el mismo (el de Saber que hizo el usuario). Al cambiar la armadura o Excalibur de Saber, se
# vuelve a ejecutar. Uso: python tools/saber_alter.py
import base64, colorsys, io, json, os
from PIL import Image

A = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'fate_ubw')


def recolor(img):
    img = img.convert('RGBA')
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            deg = h * 360
            if s > 0.25 and 170 <= deg <= 260:          # azul y cian -> negro con un toque morado
                nr, ng, nb = colorsys.hsv_to_rgb(270 / 360, 0.35, 0.08 + v * 0.22)
            elif s > 0.3 and 25 <= deg <= 70:            # oro -> rojo sangre
                nr, ng, nb = colorsys.hsv_to_rgb(355 / 360, min(1, s + 0.15), 0.35 + v * 0.55)
            elif s <= 0.25:                              # plata y blanco -> acero oscuro
                nr, ng, nb = colorsys.hsv_to_rgb(250 / 360, 0.12, 0.08 + v * 0.32)
            else:
                nr, ng, nb = r / 255, g / 255, b / 255
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    return img


def recolor_file(src, dst):
    recolor(Image.open(src)).save(dst)


# Armadura: textura, geo, animación y el .bbmodel (para los iconos y por si el usuario quiere editarla)
arm = os.path.join(A, 'textures', 'item', 'armor')
recolor_file(os.path.join(arm, 'saber_armor.png'), os.path.join(arm, 'saber_alter_armor.png'))
for folder, ext in (('geo', '.geo.json'), ('animations', '.animation.json')):
    src = os.path.join(A, folder, 'item', 'armor', 'saber_armor' + ext)
    if os.path.exists(src):
        text = open(src, encoding='utf-8').read().replace('saber_armor', 'saber_alter_armor')
        open(os.path.join(A, folder, 'item', 'armor', 'saber_alter_armor' + ext), 'w', encoding='utf-8', newline='\n').write(text)
bb = json.load(open(os.path.join(A, 'geo', 'item', 'armor', 'saber_armor.geo.bbmodel'), encoding='utf-8'))
for t in bb.get('textures', []):
    buf = io.BytesIO()
    recolor(Image.open(io.BytesIO(base64.b64decode(t['source'].split(',', 1)[1])))).save(buf, 'PNG')
    t['source'] = 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()
    t['name'] = t['name'].replace('saber_armor', 'saber_alter_armor')
bb['name'] = 'saber_alter_armor'
json.dump(bb, open(os.path.join(A, 'geo', 'item', 'armor', 'saber_alter_armor.geo.bbmodel'), 'w', encoding='utf-8'), ensure_ascii=False)

# Excalibur Morgan: las texturas de Excalibur (normal, cargando, cargada y sus brillos) y sus modelos de ítem
item = os.path.join(A, 'textures', 'item')
for name in ('excalibur', 'excalibur_charging', 'excalibur_charged'):
    for suffix in ('', '_glowmask'):
        src = os.path.join(item, name + suffix + '.png')
        if os.path.exists(src):
            recolor_file(src, os.path.join(item, name.replace('excalibur', 'excalibur_morgan') + suffix + '.png'))
models = os.path.join(A, 'models', 'item')
for name in ('excalibur', 'excalibur_charging', 'excalibur_charged'):
    text = open(os.path.join(models, name + '.json'), encoding='utf-8').read().replace('fate_ubw:item/excalibur', 'fate_ubw:item/excalibur_morgan')
    open(os.path.join(models, name.replace('excalibur', 'excalibur_morgan') + '.json'), 'w', encoding='utf-8', newline='\n').write(text)
print('ok')
