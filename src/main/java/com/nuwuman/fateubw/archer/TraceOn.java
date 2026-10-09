package com.nuwuman.fateubw.archer;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.lancer.GaeBolgItem;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Trace On, la proyección de EMIYA (habilidad del conjunto de Archer).
 * Agachado: analiza el arma de quien miras (o la de tu mano) y la guarda en la memoria del jugador.
 * Normal: proyecta una copia en un hueco libre de la barra y la selecciona; sin nada analizado, Kanshō y Bakuya.
 * Las proyecciones se desvanecen al minuto, no salen del inventario y, si las tiras, estallan (Broken Phantasm).
 */
public final class TraceOn {
    public static final int COOLDOWN = 20;
    private static final String TAG = "fate_ubw_projection"; // tick del mundo en que se desvanece
    private static final int LIFETIME = 20 * 60;
    private static final double ANALYZE_RANGE = 24.0;
    // El arma analizada, guardada en el jugador (sobrevive a la muerte)
    private static final AttachmentType<NbtCompound> MEMORY = AttachmentRegistry.<NbtCompound>builder()
            .persistent(NbtCompound.CODEC).copyOnDeath().buildAndRegister(FateUBW.id("trace_memory"));
    // Proyecciones lanzadas, a la espera de chocar para estallar
    private static final List<ItemEntity> THROWN = new ArrayList<>();

    private TraceOn() {
    }

    public static boolean isProjection(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.contains(TAG);
    }

    private static ItemStack memory(PlayerEntity player) {
        NbtCompound nbt = player.getAttached(MEMORY);
        return nbt == null ? ItemStack.EMPTY : ItemStack.fromNbt(player.getRegistryManager(), nbt).orElse(ItemStack.EMPTY);
    }

    private static ItemStack project(ItemStack original, long expiry) {
        ItemStack copy = original.copyWithCount(1);
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, copy, nbt -> nbt.putLong(TAG, expiry));
        copy.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        copy.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.translatable("item.fate_ubw.projection")
                .styled(s -> s.withItalic(false).withColor(Formatting.AQUA)))));
        return copy;
    }

    /** La habilidad: agachado analiza (sin cooldown), si no proyecta. */
    public static boolean use(ServerPlayerEntity player) {
        if (player.isSneaking()) {
            analyze(player);
            return false;
        }
        return project(player);
    }

    private static void analyze(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        LivingEntity target = GaeBolgItem.findTarget(world, player, ANALYZE_RANGE, 0.97);
        ItemStack source = target != null ? target.getMainHandStack() : ItemStack.EMPTY;
        if (!source.isDamageable()) source = player.getMainHandStack();
        if (!source.isDamageable()) source = player.getOffHandStack();
        if (!source.isDamageable()) {
            player.sendMessage(Text.translatable("message.fate_ubw.trace_on.nothing").formatted(Formatting.GRAY), true);
            return;
        }
        NbtElement nbt = source.copyWithCount(1).encode(player.getRegistryManager());
        if (nbt instanceof NbtCompound compound) player.setAttached(MEMORY, compound);
        player.sendMessage(Text.translatable("message.fate_ubw.trace_on.analyzed", source.getName()).formatted(Formatting.AQUA), true);
        if (target != null && target.getMainHandStack() == source) {
            // Una línea de chispas hasta el arma analizada
            Vec3d from = player.getEyePos(), to = target.getBoundingBox().getCenter();
            for (int i = 0; i <= 12; i++) {
                Vec3d p = from.lerp(to, i / 12.0);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8F, 1.6F);
    }

    private static boolean project(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        // Un hueco libre de la barra, empezando por la mano
        int slot = inventory.getStack(inventory.selectedSlot).isEmpty() ? inventory.selectedSlot : -1;
        for (int i = 0; i < PlayerInventory.getHotbarSize() && slot < 0; i++) {
            if (inventory.getStack(i).isEmpty()) slot = i;
        }
        if (slot < 0) {
            player.sendMessage(Text.translatable("message.fate_ubw.trace_on.full").formatted(Formatting.GRAY), true);
            return false;
        }
        ServerWorld world = player.getServerWorld();
        long expiry = world.getTime() + LIFETIME;
        ItemStack memory = memory(player);
        if (memory.isEmpty()) {
            // Sin nada analizado: Kanshō en la mano y Bakuya en la otra (o en el inventario)
            inventory.setStack(slot, project(new ItemStack(FateUBW.KANSHOU), expiry));
            ItemStack bakuya = project(new ItemStack(FateUBW.BAKUYA), expiry);
            if (player.getOffHandStack().isEmpty()) player.setStackInHand(net.minecraft.util.Hand.OFF_HAND, bakuya);
            else inventory.insertStack(bakuya);
        } else {
            inventory.setStack(slot, project(memory, expiry));
        }
        if (slot != inventory.selectedSlot) {
            inventory.selectedSlot = slot;
            player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(slot));
        }

        Vec3d hand = player.getEyePos().add(player.getRotationVec(1.0F).multiply(0.8)).add(0.0, -0.4, 0.0);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 25, 0.3, 0.4, 0.3, 0.15);
        world.spawnParticles(ParticleTypes.ENCHANT, hand.x, hand.y, hand.z, 30, 0.4, 0.5, 0.4, 0.5);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0F, 1.4F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.5F, 0.8F);
        player.sendMessage(Text.literal("Trace On").formatted(Formatting.AQUA, Formatting.BOLD), true);
        com.nuwuman.fateubw.Voices.say(world, player, "trace_on");
        return true;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TraceOn::tick);
        // Una proyección que sale del inventario: si la has tirado tú, vuela y estalla; si no, se desvanece
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof ItemEntity item) || !isProjection(item.getStack())) return;
            if (item.getOwner() instanceof PlayerEntity player && item.age == 0) {
                item.setVelocity(player.getRotationVec(1.0F).multiply(1.6));
                item.setPickupDelayInfinite();
                THROWN.add(item);
            } else {
                fade(world, item.getPos());
                item.discard();
            }
        });
    }

    private static void fade(ServerWorld world, Vec3d pos) {
        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.3, pos.z, 8, 0.15, 0.2, 0.15, 0.03);
    }

    private static void tick(MinecraftServer server) {
        // Broken Phantasm: al chocar con un bloque o con alguien, o tras 3 s en el aire
        THROWN.removeIf(item -> {
            if (item.isRemoved()) return true;
            boolean hit = item.isOnGround() || item.horizontalCollision || item.age > 60
                    || !item.getWorld().getOtherEntities(item, item.getBoundingBox().expand(0.3),
                    e -> e instanceof LivingEntity && e.isAlive() && e != item.getOwner()).isEmpty();
            if (!hit) {
                if (item.getWorld() instanceof ServerWorld world) {
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, item.getX(), item.getY(), item.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
                }
                return false;
            }
            if (item.getWorld() instanceof ServerWorld world) {
                boolean griefing = FateUBW.breaksBlocks(world, item.getPos());
                // A nombre de quien la lanzó: respeta el PvP, fatePlayerDamagePercent y le cuenta las bajas
                world.createExplosion(item, world.getDamageSources().explosion(item, item.getOwner()), null,
                        item.getX(), item.getY(), item.getZ(), griefing ? 3.5F : 3.0F,
                        false, griefing ? World.ExplosionSourceType.TNT : World.ExplosionSourceType.NONE);
                world.spawnParticles(ParticleTypes.FLASH, item.getX(), item.getY(), item.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            }
            item.discard();
            return true;
        });

        if (server.getTicks() % 10 != 0) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            long now = player.getWorld().getTime();
            boolean faded = false;
            for (int i = 0; i < player.getInventory().size(); i++) {
                if (expired(player.getInventory().getStack(i), now)) {
                    player.getInventory().setStack(i, ItemStack.EMPTY);
                    faded = true;
                }
            }
            if (expired(player.currentScreenHandler.getCursorStack(), now)) {
                player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
                faded = true;
            }
            if (faded) {
                fade(player.getServerWorld(), player.getPos().add(0.0, 1.0, 0.0));
                player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 1.0F, 1.2F);
            }
        }
    }

    private static boolean expired(ItemStack stack, long now) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.contains(TAG) && data.copyNbt().getLong(TAG) <= now;
    }
}
