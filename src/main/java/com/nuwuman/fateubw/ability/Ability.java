package com.nuwuman.fateubw.ability;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Function;

/**
 * Una habilidad del conjunto de ropa de un servant, que se usa con la tecla de habilidad.
 *
 * @param id     nombre en ability.fate_ubw.{id}
 * @param icon   ítem que la representa en el HUD
 * @param key    ítem cuyo cooldown usa ahora mismo (algunas cambian según el estado, p. ej. montado en Pegaso)
 * @param action devuelve true si se ha usado y debe empezar su cooldown
 */
public record Ability(String id, Item icon, Function<PlayerEntity, Item> key, Action action) {
    public interface Action {
        boolean use(ServerPlayerEntity player);
    }

    public Ability(String id, Item icon, Item key, Action action) {
        this(id, icon, player -> key, action);
    }
}
