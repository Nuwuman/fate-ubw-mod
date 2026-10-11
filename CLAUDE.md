# Fate UBW (mod de Fabric 1.21.1)

Notas de trabajo para Claude. Lo pendiente y las ideas están en `docs/notas.md`; lo que hace cada cosa, en `README.md`.

## Publicar una versión (el usuario lo espera tras cada cambio)
1. Subir `mod_version` en `gradle.properties`.
2. `JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew build -q`
3. Copiar `build/libs/fate_ubw-X.jar` a `C:\Users\nuwuman\curseforge\minecraft\Instances\xxxx\mods` y borrar el jar anterior.
4. `git add -A`, commit (mensaje en español, con `Co-Authored-By`) y **push** (commit implica push).
5. `gh release create vX build/libs/fate_ubw-X.jar --repo Nuwuman/fate-ubw-mod --target main --title vX --notes-file <notas>`
   (si GitHub no responde, reintentar push y release).

## Modelos y texturas
- **Generador**: `java tools/ServantAssets.java src/main/resources/assets/fate_ubw`. Después, `git checkout` de
  `geo/item/armor/lancer_armor.geo.json` (solo cambia saltos de línea) y descartar los archivos que solo cambian en CRLF
  (`git diff --ignore-cr-at-eol --name-only` da los reales). No sobrescribe lo retocado a mano (`tools/generated-hashes.txt`).
- **Modelos del usuario** (no regenerar; se convierten desde su `.bbmodel`):
  - Armaduras GeckoLib: `python tools/bbmodel_to_geo.py <in.bbmodel> <out.geo.json> [textura.png]` (ignora el jugador de
    referencia `root`, coge el tamaño de la textura usada). Rider, Berserker, Mash, Saber.
  - Ítems Java: `python tools/bbmodel_to_item.py <in.bbmodel> <modelo.json> <textura.png> <id>` (hornea giros de 90°,
    admite varias texturas → `_1.png`). Espada-hacha de Berserker y escudo de Mash, guardados en `tools/blockbench/`.
  - Al cambiar un modelo del usuario, guardar también su `.bbmodel` en el mod.
- Las piezas deben **cubrir el brazo/pierna del jugador (4×4) con margen**, o la skin asoma (pasó en Saber, Berserker).
- Ropa de servants: lo que va al aire deja ver la skin; sin pelo (tiaras/coronas solas).

## Probar
- `FATE_SHOWCASE=<escenas> ./gradlew runShowcase -q` (escenas en `src/showcase/.../Showcase.java`; capturas en
  `run-showcase/screenshots`). El PC de pruebas es lento: congelar con `tick freeze`, leer entidades del cliente.
- Para elegir posiciones de display, crear variantes con `custom_model_data` y una escena temporal; quitarlas después.
- No usar `rm -rf` con comodines (el control de seguridad lo bloquea); borrar archivos concretos.

## Preferencias del usuario
- Habla en español; prefiere que se use su referencia de imagen cuando la da y que se muestre una foto del resultado.
- Voces de anime/FGO: nunca dentro del mod (derechos). Van en su resource pack personal
  `Instances/xxxx/resourcepacks/Fate UBW - Voces` (sounds.json con `replace`; las líneas sin voz en silencio).
- No ejecutar programas de la carpeta Descargas sin permiso (ffmpeg de YoutubeDownloader). El de CapCut solo saca
  Vorbis en estéreo.
