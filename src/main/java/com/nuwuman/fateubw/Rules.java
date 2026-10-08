package com.nuwuman.fateubw;

import com.nuwuman.fateubw.ability.Abilities;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

/**
 * Reglas de equilibrio ajustables sin recompilar:
 * <ul>
 *   <li>{@code /gamerule fateCooldownPercent 50}: todas las recargas a la mitad (100 = normal).</li>
 *   <li>{@code /gamerule fatePlayerDamagePercent 50}: el daño de las armas y habilidades del mod contra jugadores.</li>
 *   <li>{@code /gamerule fateMana false}: sin maná (solo cuentan las recargas).</li>
 * </ul>
 * Además, con el PvP del servidor desactivado los efectos que no son daño (atraer, petrificar, encadenar...) tampoco
 * afectan a otros jugadores.
 */
public final class Rules {
    public static final GameRules.Key<GameRules.IntRule> COOLDOWN_PERCENT = GameRuleRegistry.register(
            "fateCooldownPercent", GameRules.Category.MISC, GameRuleFactory.createIntRule(100, 0, 1000));
    public static final GameRules.Key<GameRules.IntRule> PLAYER_DAMAGE_PERCENT = GameRuleRegistry.register(
            "fatePlayerDamagePercent", GameRules.Category.MISC, GameRuleFactory.createIntRule(100, 0, 1000));
    public static final GameRules.Key<GameRules.BooleanRule> MANA = GameRuleRegistry.register(
            "fateMana", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));

    private Rules() {
    }

    public static void register() {
        // Las claves se registran al cargar la clase
    }

    /** La recarga ajustada por fateCooldownPercent. */
    public static int scaled(World world, int ticks) {
        return world.isClient ? ticks : ticks * world.getGameRules().getInt(COOLDOWN_PERCENT) / 100;
    }

    /** Pone una recarga (solo en el servidor, que la sincroniza con el cliente) ajustada por la gamerule. */
    public static void cooldown(PlayerEntity player, Item key, int ticks) {
        if (player.getWorld().isClient) return;
        int scaled = scaled(player.getWorld(), ticks);
        if (scaled > 0) player.getItemCooldownManager().set(key, scaled);
    }

    /** ¿Lista para usarse? Comprueba la recarga y el maná, y avisa de lo que falte. */
    public static boolean ready(PlayerEntity player, Item key, int cooldown, String ability) {
        if (player.getItemCooldownManager().isCoolingDown(key)) {
            if (!player.getWorld().isClient) FateUBW.cooldownMessage(player, key, cooldown, ability);
            return false;
        }
        return com.nuwuman.fateubw.ability.Mana.has(player, key);
    }

    /** Se ha usado: empieza la recarga y gasta el maná. */
    public static void commit(PlayerEntity player, Item key, int cooldown) {
        cooldown(player, key, cooldown);
        com.nuwuman.fateubw.ability.Mana.spend(player, key);
    }

    /** ¿Puede {@code user} afectar a {@code target}? Respeta el PvP del servidor y el fuego amigo de los equipos. */
    public static boolean canAffect(Entity user, Entity target) {
        if (!(target instanceof ServerPlayerEntity victim) || !(user instanceof PlayerEntity attacker) || victim == attacker) return true;
        return victim.shouldDamagePlayer(attacker);
    }

    /** ¿Viene este daño de algo del mod (sus tipos de daño, sus entidades, o alguien vestido o armado de servant)? */
    public static boolean fromFate(DamageSource source) {
        if (source.getTypeRegistryEntry().getKey().map(k -> k.getValue().getNamespace().equals(FateUBW.MOD_ID)).orElse(false)) return true;
        Entity direct = source.getSource();
        if (direct != null && Registries.ENTITY_TYPE.getId(direct.getType()).getNamespace().equals(FateUBW.MOD_ID)) return true;
        return source.getAttacker() instanceof PlayerEntity attacker && (!Abilities.of(attacker).isEmpty()
                || Registries.ITEM.getId(attacker.getMainHandStack().getItem()).getNamespace().equals(FateUBW.MOD_ID));
    }

    /** Daño a un jugador ajustado por fatePlayerDamagePercent si viene de otro jugador con cosas del mod. */
    public static float playerDamage(Entity victim, DamageSource source, float amount) {
        if (!(victim instanceof PlayerEntity) || victim.getWorld().isClient || source.getAttacker() == victim
                || !(source.getAttacker() instanceof PlayerEntity) || !fromFate(source)) return amount;
        return amount * victim.getWorld().getGameRules().getInt(PLAYER_DAMAGE_PERCENT) / 100.0F;
    }
}
