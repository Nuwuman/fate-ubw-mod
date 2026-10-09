package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.PlayerAnims;
import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.WeakHashMap;

/** Reproduce las animaciones de Player Animator: las de un golpe llegan del servidor, las posturas se deducen del ítem en uso. */
public final class PlayerAnimsClient {
    private static final Identifier LAYER = FateUBW.id("servant");
    private record Pose(String name, IAnimation animation) {}
    private static final Map<AbstractClientPlayerEntity, Pose> POSES = new WeakHashMap<>();

    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1000, player -> new ModifierLayer<>());
        PlayerAnims.clientPlay = (player, animation) -> {
            if (player instanceof AbstractClientPlayerEntity client) play(client, animation, 2);
        };
        ClientPlayNetworking.registerGlobalReceiver(PlayerAnims.PlayPayload.ID, (payload, context) -> {
            if (context.client().world != null
                    && context.client().world.getEntityById(payload.entity()) instanceof AbstractClientPlayerEntity player) {
                play(player, payload.animation(), 2);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;
            for (AbstractClientPlayerEntity player : client.world.getPlayers()) holdPose(player);
        });
    }

    // La postura sigue al ítem en uso; al soltarlo se deshace, salvo que ya la haya sustituido un golpe del servidor
    private static void holdPose(AbstractClientPlayerEntity player) {
        String wanted = pose(player);
        Pose current = POSES.get(player);
        if (current != null && current.name().equals(wanted)) return;
        ModifierLayer<IAnimation> layer = layer(player);
        if (current != null && layer != null && layer.getAnimation() == current.animation()) {
            layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(5, Ease.INOUTSINE), null);
        }
        if (wanted == null) POSES.remove(player);
        else POSES.put(player, new Pose(wanted, play(player, wanted, 4)));
    }

    private static String pose(AbstractClientPlayerEntity player) {
        if (!player.isUsingItem()) return null;
        ItemStack stack = player.getActiveItem();
        if (stack.isOf(FateUBW.EXCALIBUR)) return "excalibur_charge";
        if (stack.isOf(FateUBW.EA)) return "enuma_elish_charge";
        if (stack.isOf(FateUBW.MONOHOSHIZAO)) return "tsubame_stance";
        return null;
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layer(AbstractClientPlayerEntity player) {
        return PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER) instanceof ModifierLayer<?> layer
                ? (ModifierLayer<IAnimation>) layer : null;
    }

    private static IAnimation play(AbstractClientPlayerEntity player, String name, int fade) {
        IPlayable playable = PlayerAnimationRegistry.getAnimation(FateUBW.id(name));
        ModifierLayer<IAnimation> layer = layer(player);
        if (playable == null || layer == null) return null;
        IAnimation animation = playable.playAnimation();
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fade, Ease.INOUTSINE), animation);
        return animation;
    }
}
