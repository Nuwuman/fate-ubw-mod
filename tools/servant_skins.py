# Pieles 64x64 de los servants enemigos que se añadieron después del generador (casi todo lo tapa su ropa).
# Mismo dibujo que skin() de tools/ServantAssets.java: piel, pelo, ojos y boca. Uso: python tools/servant_skins.py
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'fate_ubw', 'textures', 'entity', 'servant')
SKINS = {  # piel, pelo, ojos
    'saber': (0xf0d4bc, 0xe8c860, 0x2f9a5a),
    'archer': (0xa8724e, 0xe6e6e6, 0x707a90),
    'rider': (0xf0dcd0, 0x7a3fa0, 0x8a40c0),
    'gilgamesh': (0xf0d0b0, 0xf0c830, 0xc01020),
    'caster': (0xead6c8, 0x4a3f8a, 0x4060d0),
}


def rgb(c, k=1.0):
    return tuple(min(255, int(((c >> s) & 255) * k)) for s in (16, 8, 0)) + (255,)


for name, (skin, hair, eye) in SKINS.items():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    px = img.load()
    for y in range(16, 64):
        for x in range(64):
            px[x, y] = rgb(skin, 0.95 + 0.05 * ((x + y) % 2))
    for y in range(16):
        for x in range(32):
            px[x, y] = rgb(hair if (y < 8 or (y < 11 and x not in (9, 14))) else skin)
    for x in range(8, 16):
        for y in range(8, 16):
            px[x, y] = rgb(hair if y < 10 else skin)          # cara
    for x in range(24, 32):
        for y in range(8, 16):
            px[x, y] = rgb(hair)                                # nuca
    for ex in (9, 14):
        px[ex, 12] = (255, 255, 255, 255)
        px[10 if ex == 9 else 13, 12] = rgb(eye)
    px[11, 14] = rgb(skin, 0.7)
    px[12, 14] = rgb(skin, 0.7)
    img.save(os.path.join(OUT, name + '.png'))
print('ok')
