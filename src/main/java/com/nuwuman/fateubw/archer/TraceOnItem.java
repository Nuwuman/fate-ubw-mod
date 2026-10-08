package com.nuwuman.fateubw.archer;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.lancer.GaeBolgItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Trace On: la proyección de EMIYA. Agachado + click derecho analiza el arma de quien miras (o la de tu otra mano);
 * click derecho proyecta una copia en tu mano y Trace On pasa al inventario. Sin nada analizado proyecta a Kanshō y Bakuya.
 * Las proyecciones se desvanecen al minuto, no se pueden guardar fuera del inventario y, si las tiras, estallan.
 */
public class TraceOnItem extends Item {
    private static final String TAG = "fate_ubw_projection"; // tick del mundo en que se desvanece
    private static final int LIFETIME = 20 * 60;
    private static final int COOLDOWN = 20;
    private static final double ANALYZE_RANGE = 24.0;
    // Proyecciones lanzadas, a la espera de chocar para estallar
    private static final List<ItemEntity> THROWN = new ArrayList<>();

    public TraceOnItem(Item.Settings settings) {
        super(settings);
    }

    public static boolean isProjection(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.contains(TAG);
    }

    private static ItemStack memory(ItemStack traceOn) {
        ContainerComponent memory = traceOn.get(DataComponentTypes.CONTAINER);
        return memory == null ? ItemStack.EMPTY : memory.copyFirstStack();
    }

    private static ItemStack project(ItemStack original, long expiry) {
        ItemStack copy = original.copyWithCount(1);
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, copy, nbt -> nbt.putLong(TAG, expiry));
        copy.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        copy.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.translatable("item.fate_ubw.projection")
                .styled(s -> s.withItalic(false).withColor(Formatting.AQUA)))));
        return copy;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!(world instanceof ServerWorld server)) return TypedActionResult.success(stack, true);
        if (user.isSneaking()) return analyze(server, user, hand, stack);

        int free = user.getInventory().getEmptySlot();
        if (free < 0) {
            user.sendMessage(Text.translatable("message.fate_ubw.trace_on.full").formatted(Formatting.GRAY), true);
            return TypedActionResult.fail(stack);
        }
        long expiry = world.getTime() + LIFETIME;
        ItemStack memory = memory(stack);
        ItemStack projected;
        if (memory.isEmpty()) {
            // Sin nada analizado: Kanshō en esta mano y Bakuya en la otra (o en el inventario)
            projected = project(new ItemStack(FateUBW.KANSHOU), expiry);
            ItemStack bakuya = project(new ItemStack(FateUBW.BAKUYA), expiry);
            Hand other = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
            user.getInventory().setStack(free, stack);
            if (user.getStackInHand(other).isEmpty()) user.setStackInHand(other, bakuya);
            else user.getInventory().insertStack(bakuya);
        } else {
            projected = project(memory, expiry);
            user.getInventory().setStack(free, stack);
        }

        user.getItemCooldownManager().set(this, COOLDOWN);
        Vec3d hand3 = user.getEyePos().add(user.getRotationVec(1.0F).multiply(0.8)).add(0.0, -0.4, 0.0);
        server.spawnParticles(ParticleTypes.ELECTRIC_SPARK, hand3.x, hand3.y, hand3.z, 25, 0.3, 0.4, 0.3, 0.15);
        server.spawnParticles(ParticleTypes.ENCHANT, hand3.x, hand3.y, hand3.z, 30, 0.4, 0.5, 0.4, 0.5);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0F, 1.4F);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.5F, 0.8F);
        user.sendMessage(Text.literal("Trace On").formatted(Formatting.AQUA, Formatting.BOLD), true);
        // El valor devuelto ocupa la mano que tenía Trace On
        return TypedActionResult.success(projected);
    }

    // Análisis estructural: el arma de quien miras o, si no lleva ninguna, la de tu otra mano
    private TypedActionResult<ItemStack> analyze(ServerWorld world, PlayerEntity user, Hand hand, ItemStack stack) {
        LivingEntity target = GaeBolgItem.findTarget(world, user, ANALYZE_RANGE, 0.97);
        ItemStack source = target != null ? target.getMainHandStack() : ItemStack.EMPTY;
        if (!source.isDamageable()) source = user.getStackInHand(hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND);
        if (!source.isDamageable()) {
            user.sendMessage(Text.translatable("message.fate_ubw.trace_on.nothing").formatted(Formatting.GRAY), true);
            return TypedActionResult.fail(stack);
        }
        stack.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(source.copyWithCount(1))));
        user.sendMessage(Text.translatable("message.fate_ubw.trace_on.analyzed", source.getName()).formatted(Formatting.AQUA), true);
        if (target != null && target.getMainHandStack() == source) {
            // Una línea de chispas hasta el arma analizada
            Vec3d from = user.getEyePos(), to = target.getBoundingBox().getCenter();
            for (int i = 0; i <= 12; i++) {
                Vec3d p = from.lerp(to, i / 12.0);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8F, 1.6F);
        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        ItemStack memory = memory(stack);
        tooltip.add(Text.translatable("item.fate_ubw.trace_on.tooltip.memory",
                memory.isEmpty() ? Text.translatable("item.fate_ubw.trace_on.tooltip.pair") : memory.getName()).formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("item.fate_ubw.trace_on.tooltip.project").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.fate_ubw.trace_on.tooltip.analyze").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.fate_ubw.trace_on.tooltip.throw").formatted(Formatting.RED));
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TraceOnItem::tick);
        // Una proyección que sale del inventario: si la has lanzado tú, vuela y estalla; si no, se desvanece
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
                boolean griefing = FateUBW.breaksBlocks(world);
                world.createExplosion(item, item.getX(), item.getY(), item.getZ(), griefing ? 3.5F : 3.0F,
                        griefing ? World.ExplosionSourceType.TNT : World.ExplosionSourceType.NONE);
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
