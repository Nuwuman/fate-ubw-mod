# Notas para revisar

## Para mañana: lo que necesita tu intervención

1. **Voces mono**: ahora son estéreo y no bajan con la distancia (solo las oyen los jugadores a menos de 16 bloques).
   Para hacerlas mono hace falta un ffmpeg con `libvorbis`. Hay uno en `Downloads\YoutubeDownloader.win-x64`, pero no
   ejecuto programas de la carpeta de descargas sin tu permiso. Si encuentras voces mejores que las de Windows,
   basta con poner los `.ogg` con el mismo nombre en `src/main/resources/assets/fate_ubw/sounds/voice/`.
2. **EULA**: permiso para aceptarlo y probar el mod en un servidor dedicado local, con dos clientes a la vez. Es la
   única forma de probar de verdad la Guerra del Santo Grial (necesita dos jugadores) y el PvP.

## Ideas que quedan para más adelante

- **Excalibur con GeckoLib**, como Ea: hoja que brilla en la oscuridad al cargar y el viento de Invisible Air
  deshaciéndose de verdad. Es más delicado que Ea porque Excalibur cambia de postura al cargar (dos modelos JSON
  distintos) y habría que hacerlo con animaciones.
- **Más armas en GeckoLib** con partes que brillan: Gáe Bolg (runas), Rule Breaker, Hrunting.
- **Servants aliados**: invocar un servant que luche a tu lado en vez de llevar su equipo.
- **Servants enemigos guardando estructuras** (ahora aparecen sueltos de noche).
- **Guerra del Santo Grial guardada**: ahora vive en memoria; si el servidor se reinicia, la guerra termina.

## Problemas conocidos

- Las animaciones de Player Animator solo se ven en tercera persona y para los demás; en primera persona sigues
  viendo los brazos normales.

- Las habilidades de Rider necesitan también la venda (4 piezas), como su bonus de conjunto.
- Las teclas R, G y V pueden chocar con otros mods: se cambian en Opciones → Controles → Fate: Unlimited Blade Works.
- Los ítems antiguos (Unlimited Blade Works, Trace On, Bellerophon, Gate of Babylon) ya no salen en el creativo ni
  tienen receta, pero los que ya tengas siguen funcionando.
- Todas las Ea comparten la animación: si un jugador carga la suya, los cilindros de todas giran deprisa.
- Invisible Air también oculta Excalibur en el icono del inventario (se ve el remolino).

## Sin probar

- Servidor dedicado y varios jugadores a la vez (HUD, habilidades, maná y Sellos usan red normal de Fabric).
- La Guerra del Santo Grial con dos o más jugadores, y `fatePlayerDamagePercent` (necesitan dos jugadores).
- Que el Marble restaure los bloques si el servidor se cae (el cierre normal sí está probado).
- Que las proyecciones de Trace On desaparezcan a los 60 s y que los cofres las rechacen.
- Que Lancer y Assassin aparezcan solos de noche (solo probados con huevo y /summon).
