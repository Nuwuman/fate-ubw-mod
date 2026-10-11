# Notas para revisar

## Para mañana: lo que necesita tu intervención

1. **Voces mono**: ahora son estéreo y no bajan con la distancia (solo las oyen los jugadores a menos de 16 bloques).
   Para hacerlas mono hace falta un ffmpeg con `libvorbis`. Hay uno en `Downloads\YoutubeDownloader.win-x64`, pero no
   ejecuto programas de la carpeta de descargas sin tu permiso. Si encuentras voces mejores que las de Windows,
   basta con poner los `.ogg` con el mismo nombre en `src/main/resources/assets/fate_ubw/sounds/voice/`.
2. **EULA**: permiso para aceptarlo y probar el mod en un servidor dedicado local, con dos clientes a la vez. Es la
   única forma de probar de verdad la Guerra del Santo Grial (necesita dos jugadores) y el PvP.

## Pendiente de comprobar o decidir (sesión de octubre)

- **Escudo de Mash**: en v2.18.16 lo giré 180° porque la cara de los detalles miraba hacia Mash. Confirmar en juego que
  ahora mira hacia fuera; si no, volver a `[90, 90, 0]` en Display (thirdperson) de `tools/blockbench/mash_shield.bbmodel`.
- **Escudo pegado al brazo**: con el modelo de ítem no queda perfecto (de frente se inclina). Si molesta, pasarlo a
  la armadura de Mash (hueso del brazo) en vez de ítem en la mano.
- **Cámara de los Noble Phantasm**: los haces duran 2 s más (v2.18.9) pero la cinemática no; alargarla si se quiere.
- **Liga de la pierna izquierda de Mash**: el usuario la quitó en su modelo; confirmar que fue a propósito.
- **Choque de Noble Phantasms (QTE)** y **logros de choque/Guerra del Grial**: sin probar con dos jugadores.
- **Voces**: pack personal en `Instances/xxxx/resourcepacks/Fate UBW - Voces` con 5 voces de FGO (Excalibur, Ea,
  Gáe Bolg, UBW al pulsar la habilidad, An Gal Ta); el resto en silencio. Se le pasa como .zip al usuario si cambia.

## Ideas que quedan para más adelante

Ordenadas por lo que aportan frente a lo que cuestan.

**Rápidas**
- **Rider con 3 piezas**: que sus habilidades funcionen sin la venda, como la capucha de Caster o la tiara de Ishtar.
- **Maanna en GeckoLib**: que brille en la oscuridad y que la gema y las alas se animen al cargar An Gal Ta Kigal Shè,
  como Excalibur.
- **Voz de Ishtar** para sus habilidades además del Noble Phantasm.

**Medianas**
- **Más servants enemigos**: Rider (a lomos de Pegaso), Caster (flotando y lanzando magia), Archer (a distancia,
  con Kanshō y Bakuya de cerca) e Ishtar (desde el aire). Reutilizan sus armaduras y ataques.
- **Servants aliados**: invocar un servant que luche a tu lado en vez de llevar su equipo.
- **Servants enemigos guardando estructuras** (ahora aparecen sueltos de noche): el templo Ryuudou, la casa de Emiya,
  la iglesia de Kotomine, con botín.
- **Guerra del Santo Grial más completa**: marcador con quién queda, zona que se cierra como un battle royale o límite
  de tiempo.

**Grandes**
- **Nuevos servants**: Rin y Sakura con kit propio, o clases extra como Ruler y Avenger (Jeanne, Jeanne Alter).
- **Jefe final, la Sombra / Angra Mainyu**: sale del Grial corrompido al terminar la Guerra.

- **Más posturas**: Caladbolg, Sky Boat, Manifestation of Beauty, Presence Concealment, Spatial Transfer y una de carga
  para Nine Lives (el hacha al hombro).

**Más ideas (segunda tanda)**
- **Encantamientos para las armas que no tienen**: Maanna ("Venus Overdrive": An Gal Ta Kigal Shè más grande cuanto
  más maná te quede), Rho Aias ("Seven Petals": el escudo para un golpe más por nivel), y uno normal (I-III) para
  Nine Lives y Tsubame Gaeshi.
- **Mejora del Reality Marble**: dentro de Unlimited Blade Works, las armas proyectadas copian los encantamientos del
  arma analizada con Trace On.
- **Botín de Fuyuki**: cofres de aldeas y estructuras con algo de probabilidad de círculo de invocación, catalizador o
  libro legendario.
- **Recarga de maná junto al Grial o de noche**: un bloque u objeto de "línea ley" que acelera el maná a su lado.
- **Ajuste para el choque de Noble Phantasms y la cámara**: si gustan, una opción para apagar la cámara y afinar
  cuánto dura el choque.

**Ajustes pendientes de decidir**
- Ishtar: otros colores (la ropa blanca de su tercera ascensión), otras habilidades u otro catalizador (ahora diamante).
- Gáe Bolg con Better Combat: ahora es "lanza" (combo de estocadas); "tridente" sería una sola estocada.
- Proporción de armas vanilla y del mod clavadas en Unlimited Blade Works (ahora mitad y mitad); se podrían añadir
  hachas o la maza.

## Problemas conocidos

- Las animaciones de Player Animator solo se ven en tercera persona y para los demás; en primera persona sigues
  viendo los brazos normales.
- Las habilidades de Rider necesitan también la venda (4 piezas), como su bonus de conjunto.
- Las teclas R, G y V pueden chocar con otros mods: se cambian en Opciones → Controles → Fate: Unlimited Blade Works.
- Los ítems antiguos (Unlimited Blade Works, Trace On, Bellerophon, Gate of Babylon) ya no salen en el creativo ni
  tienen receta, pero los que ya tengas siguen funcionando.

## Sin probar

- Dos Excalibur o dos Ea a la vez: cada una debería animarse por su cuenta (ahora tienen identificador propio).
- Servidor dedicado y varios jugadores a la vez (HUD, habilidades, maná y Sellos usan red normal de Fabric).
- La Guerra del Santo Grial con dos o más jugadores, y `fatePlayerDamagePercent` (necesitan dos jugadores).
- Que el Marble restaure los bloques si el servidor se cae (el cierre normal sí está probado).
- Que las proyecciones de Trace On desaparezcan a los 60 s y que los cofres las rechacen.
- Que Lancer y Assassin aparezcan solos de noche (solo probados con huevo y /summon).
- Better Combat: los `weapon_attributes` de las armas no están probados en el entorno de pruebas (habría que descargar
  Better Combat y Cloth Config); comprobar en juego que cada arma usa sus animaciones de golpe.
- La Guerra del Santo Grial guardada con dos o más jugadores (solo probada empezando con uno).
- Fresh Animations: Player Extension con las armaduras de GeckoLib (podrían desalinearse al correr o saltar).
