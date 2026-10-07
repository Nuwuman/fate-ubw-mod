package com.nuwuman.fateubw;

import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * Armadura de servant con modelo 3D de GeckoLib.
 * Lee geo/item/armor/{model}.geo.json, textures/item/armor/{model}.png y reproduce en bucle "animation.{model}.idle".
 */
public class ServantArmorItem extends ArmorItem implements GeoItem {
    private final String model;
    private final RawAnimation idle;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ServantArmorItem(RegistryEntry<ArmorMaterial> material, ArmorItem.Type type, Item.Settings settings, String model) {
        super(material, type, settings.maxDamage(type.getMaxDamage(37)));
        this.model = model;
        this.idle = RawAnimation.begin().thenLoop("animation." + model + ".idle");
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoArmorRenderer<ServantArmorItem> renderer;

            @Override
            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(@Nullable T entity, ItemStack stack,
                                                                                    @Nullable EquipmentSlot slot, @Nullable BipedEntityModel<T> original) {
                if (renderer == null) renderer = new GeoArmorRenderer<>(new DefaultedItemGeoModel<>(FateUBW.id("armor/" + model)));
                return renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, 10, state -> state.setAndContinue(idle)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** ¿Lleva puestos peto, grebas y botas de este conjunto? */
    public static boolean wearsSet(PlayerEntity player, Item chest, Item legs, Item feet) {
        return player.getEquippedStack(EquipmentSlot.CHEST).isOf(chest)
                && player.getEquippedStack(EquipmentSlot.LEGS).isOf(legs)
                && player.getEquippedStack(EquipmentSlot.FEET).isOf(feet);
    }
}
