package com.nuwuman.fateubw.grail;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Círculo de invocación: click derecho sobre el suelo con un catalizador en la otra mano. El ritual dura 3 s y
 * deja el equipo completo del servant que atrae el catalizador (sin catalizador, uno al azar).
 */
public class SummoningCircleItem extends Item {
    private static final int RITUAL_TICKS = 60;
    private static final DustParticleEffect CIRCLE = new DustParticleEffect(new Vector3f(0.85F, 0.1F, 0.15F), 1.2F);
    private static final DustParticleEffect RUNE = new DustParticleEffect(new Vector3f(0.4F, 0.7F, 1.0F), 1.0F);

    private record Ritual(ServerWorld world, Vec3d center, Servants.Servant servant, UUID master, int[] age) {
    }

    private static final List<Ritual> RITUALS = new ArrayList<>();

    public SummoningCircleItem(Item.Settings settings) {
        super(settings);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> RITUALS.removeIf(SummoningCircleItem::tick));
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (!(context.getWorld() instanceof ServerWorld world) || player == null) return ActionResult.SUCCESS;
        Hand other = context.getHand() == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
        ItemStack catalyst = player.getStackInHand(other);
        Servants.Servant servant = Servants.byCatalyst(catalyst, world.random);
        if (!player.isCreative()) {
            context.getStack().decrement(1);
            if (Servants.isCatalyst(catalyst)) catalyst.decrement(1);
        }
        Vec3d center = Vec3d.ofBottomCenter(context.getBlockPos().up());
        if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)
            com.nuwuman.fateubw.Achievements.summoned(serverPlayer, servant.id());
        RITUALS.add(new Ritual(world, center, servant, player.getUuid(), new int[]{0}));
        world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1.5F, 0.6F);
        player.sendMessage(Text.translatable("message.fate_ubw.summoning").formatted(Formatting.RED, Formatting.ITALIC), true);
        return ActionResult.CONSUME;
    }

    // El círculo se dibuja y gira, las runas suben y al final cae un rayo y aparece el equipo
    private static boolean tick(Ritual ritual) {
        int age = ++ritual.age()[0];
        ServerWorld world = ritual.world();
        Vec3d c = ritual.center();
        float progress = Math.min(1.0F, age / (float) RITUAL_TICKS);
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI / 12 + age * 0.05;
            world.spawnParticles(CIRCLE, c.x + Math.cos(angle) * 2.0, c.y + 0.05, c.z + Math.sin(angle) * 2.0, 1, 0.0, 0.0, 0.0, 0.0);
        }
        // Estrella de cinco puntas dentro del círculo
        for (int p = 0; p < 5; p++) {
            double a0 = p * Math.PI * 2 / 5 - age * 0.03, a1 = (p + 2) * Math.PI * 2 / 5 - age * 0.03;
            for (int s = 0; s < 6; s++) {
                double t = s / 6.0;
                world.spawnParticles(RUNE, c.x + (Math.cos(a0) * (1 - t) + Math.cos(a1) * t) * 1.8, c.y + 0.05,
                        c.z + (Math.sin(a0) * (1 - t) + Math.sin(a1) * t) * 1.8, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (age % 4 == 0) world.spawnParticles(ParticleTypes.END_ROD, c.x, c.y + 0.2, c.z, 4, 0.8, 0.1, 0.8, 0.05 + progress * 0.2);
        if (age % 20 == 0) world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 2.0F, 0.5F + progress);
        if (age < RITUAL_TICKS) return false;

        LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
        if (lightning != null) {
            lightning.setCosmetic(true);
            lightning.refreshPositionAfterTeleport(c);
            world.spawnEntity(lightning);
        }
        world.spawnParticles(ParticleTypes.FLASH, c.x, c.y + 1.0, c.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y + 1.0, c.z, 80, 0.6, 1.0, 0.6, 0.4);
        for (Item piece : ritual.servant().armor()) drop(world, c, piece);
        for (Item weapon : ritual.servant().weapons()) drop(world, c, weapon);
        PlayerEntity master = world.getPlayerByUuid(ritual.master());
        if (master != null) {
            master.sendMessage(Text.translatable("message.fate_ubw.summoned",
                    Text.translatable("servant.fate_ubw." + ritual.servant().id())).formatted(Formatting.GOLD, Formatting.BOLD), true);
        }
        return true;
    }

    private static void drop(ServerWorld world, Vec3d at, Item item) {
        ItemEntity entity = new ItemEntity(world, at.x, at.y + 0.5, at.z, new ItemStack(item));
        entity.setVelocity(world.random.nextGaussian() * 0.08, 0.3, world.random.nextGaussian() * 0.08);
        world.spawnEntity(entity);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.summoning_circle.tooltip").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.fate_ubw.summoning_circle.tooltip.catalysts").formatted(Formatting.DARK_GRAY));
    }
}
