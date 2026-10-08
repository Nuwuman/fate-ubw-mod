# Notas para revisar

## Mejorar los modelos

Ahora las armas son modelos JSON de ítem, que tienen tres límites: los cubos solo giran ±22,5°/45° en un eje, todo
tiene que caber entre −16 y 32, y nada se mueve salvo cambiando la textura.

1. **GeckoLib para las armas.** Ya es dependencia del mod (lo usan las armaduras y Pegaso), así que no hay que
   instalar nada más. Da:
   - giros libres y huesos animados: los cilindros de Ea girando de verdad, el viento de Invisible Air deshaciéndose
     al cargar Excalibur, la cadena de la daga de Rider colgando;
   - sin límite de tamaño, para que la Monohoshizao o la Gáe Bolg tengan su largo real;
   - **partes que brillan en la oscuridad** con `AutoGlowingGeoLayer` (una textura `_glowmask`): hoja dorada de
     Excalibur cargada, runas de UBW, líneas rojas de Ea, Trace On, gemas de Gilgamesh. Sirve también para armaduras.

   Coste: cada arma necesita su renderer y el generador tiene que escribir `geo` de ítem en vez de JSON; las posiciones
   en mano (primera y tercera persona) hay que reajustarlas. Propuesta: empezar por Ea y Excalibur.
2. **Player Animator** (biblioteca de Fabric de KosmX): animaciones propias del jugador, como alzar Excalibur con las
   dos manos de verdad, la postura de Tsubame Gaeshi o la ráfaga de Nine Lives. Es una dependencia nueva que habría
   que instalar en cada PC y servidor, así que no la he añadido sin tu permiso.
3. **Texturas con más resolución** en el generador (2 píxeles por unidad): más detalle en grabados y sombreado, a
   cambio de alejarse del estilo de Minecraft. Es un cambio pequeño.
4. **Blockbench a mano** para las piezas estrella, exportado como GeckoLib, si se quiere un acabado de artista.

## Líneas de voz

- Están generadas con las voces de Windows (Laura, Helena y Pablo) con eco y tono retocados: suenan a robot.
  Para cambiarlas basta con poner otros `.ogg` con el mismo nombre en `src/main/resources/assets/fate_ubw/sounds/voice/`.
- Son estéreo, así que no bajan de volumen con la distancia (solo las oyen los jugadores a menos de 16 bloques).
  Para hacerlas mono hace falta un ffmpeg con `libvorbis`. Hay uno en `Downloads\YoutubeDownloader.win-x64`, pero
  no he ejecutado programas de la carpeta de descargas; el de CapCut solo sabe hacer estéreo.
- Se apagan con `/gamerule fateVoiceLines false`.

## Problemas conocidos

- Las habilidades de Rider necesitan también la venda (4 piezas), como su bonus de conjunto.
- Las teclas R y G pueden chocar con otros mods: se cambian en Opciones → Controles → Fate: Unlimited Blade Works.
- Los ítems antiguos (Unlimited Blade Works, Trace On, Bellerophon, Gate of Babylon) ya no salen en el creativo ni
  tienen receta, pero los que ya tengas siguen funcionando.

## Sin probar

- Servidor dedicado y varios jugadores a la vez (el HUD y las habilidades usan red normal de Fabric, debería ir).
- Que el Marble restaure los bloques si el servidor se cae (el cierre normal sí está probado).
- Que las proyecciones de Trace On desaparezcan a los 60 s y que los cofres las rechacen.

## Pendiente de tu respuesta

- Ajustes de PvP: gamerule de porcentaje de daño contra jugadores y que los efectos sin daño respeten el PvP del servidor.
- Permiso para aceptar el EULA y probar en un servidor dedicado local.
