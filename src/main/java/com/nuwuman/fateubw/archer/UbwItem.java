package com.nuwuman.fateubw.archer;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.gilgamesh.BabylonPortalEntity;
import com.nuwuman.fateubw.gilgamesh.BabylonWeaponEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;

/**
 * Unlimited Blade Works. Fuera: mantén para desplegar el Reality Marble.
 * Dentro: click derecho hace surgir espadas del suelo que vuelan al enemigo; agachado lo deshace.
 */
public class UbwItem extends Item {
    public static final int CHANT_TICKS = 60;
    public static final int MARBLE_COOLDOWN = 20 * 120;
    private static final int BARRAGE_COOLDOWN = 30;
    private static final int BARRAGE_SWORDS = 10;
    private static final double CHANT_RADIUS = UnlimitedBladeWorks.RADIUS - 2;
    private static final DustParticleEffect EMBER = new DustParticleEffect(new Vector3f(1.0F, 0.45F, 0.1F), 1.5F);

    public UbwItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (UnlimitedBladeWorks.isInside(user)) {
            if (user.isSneaking()) {
                if (user instanceof ServerPlayerEntity player) UnlimitedBladeWorks.end(player);
            } else {
                if (world instanceof ServerWorld server) barrage(server, user);
                user.getItemCooldownManager().set(this, BARRAGE_COOLDOWN);
            }
            return TypedActionResult.success(stack, world.isClient());
        }
        if (user.getItemCooldownManager().isCoolingDown(FateUBW.UBW_COOLDOWN)) {
            if (!world.isClient) FateUBW.cooldownMessage(user, FateUBW.UBW_COOLDOWN, MARBLE_COOLDOWN, "unlimited_blade_works");
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    // Un anillo de fuego que se extiende por el suelo mientras se recita el aria
    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!(world instanceof ServerWorld server) || UnlimitedBladeWorks.isInside(user)) return;
        int charge = getMaxUseTime(stack, user) - remainingUseTicks;
        double radius = CHANT_RADIUS * Math.min(1.0, charge / (double) CHANT_TICKS);
        for (int i = 0; i < 64; i++) {
            double angle = i * Math.PI * 2 / 64 + charge * 0.05;
            server.spawnParticles(ParticleTypes.FLAME, user.getX() + Math.cos(angle) * radius, user.getY() + 0.1,
                    user.getZ() + Math.sin(angle) * radius, 1, 0.0, 0.05, 0.0, 0.01);
        }
        server.spawnParticles(EMBER, user.getX(), user.getBodyY(0.5), user.getZ(), 2, 0.6, 0.8, 0.6, 0.0);
        if (charge % 20 == 0 && charge > 0 && charge <= CHANT_TICKS) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0F, 0.6F + charge / 100.0F);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        int charge = getMaxUseTime(stack, user) - remainingUseTicks;
        if (charge < CHANT_TICKS || !(user instanceof ServerPlayerEntity player)) return;
        if (UnlimitedBladeWorks.open(player)) {
            player.getItemCooldownManager().set(FateUBW.UBW_COOLDOWN, MARBLE_COOLDOWN);
        }
    }

    // Espadas que surgen del suelo detrás del lanzador y salen disparadas hacia lo que mira
    private void barrage(ServerWorld world, PlayerEntity player) {
        Vec3d target = BabylonPortalEntity.aimPoint(world, player, 48.0);
        Vec3d forward = Vec3d.fromPolar(0.0F, player.getYaw());
        Vec3d right = forward.crossProduct(new Vec3d(0.0, 1.0, 0.0));
        ItemStack[] swords = {new ItemStack(FateUBW.KANSHOU), new ItemStack(FateUBW.BAKUYA), new ItemStack(FateUBW.SWORD_ARROW),
                new ItemStack(FateUBW.CALADBOLG), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.DIAMOND_SWORD)};
        for (int i = 0; i < BARRAGE_SWORDS; i++) {
            Vec3d from = player.getPos()
                    .add(right.multiply((i - (BARRAGE_SWORDS - 1) / 2.0) * 0.9))
                    .subtract(forward.multiply(1.5 + world.random.nextDouble()))
                    .add(0.0, 0.4 + world.random.nextDouble() * 0.6, 0.0);
            BabylonWeaponEntity sword = new BabylonWeaponEntity(world, player, swords[world.random.nextInt(swords.length)].copy());
            sword.setPosition(from);
            Vec3d dir = target.subtract(from).normalize();
            sword.setVelocity(dir.x, dir.y, dir.z, 2.6F, 1.5F);
            world.spawnEntity(sword);
            world.spawnParticles(ParticleTypes.CRIT, from.x, from.y, from.z, 4, 0.1, 0.1, 0.1, 0.1);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.5F, 0.6F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_TRIDENT_THROW.value(), SoundCategory.PLAYERS, 1.0F, 0.8F);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.unlimited_blade_works.tooltip.open").formatted(Formatting.RED));
        tooltip.add(Text.translatable("item.fate_ubw.unlimited_blade_works.tooltip.inside").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("item.fate_ubw.unlimited_blade_works.tooltip.end").formatted(Formatting.GRAY));
    }
}
