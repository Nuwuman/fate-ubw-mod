package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.npc.HostileServantEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Un servant enemigo con forma de jugador, su piel y su ropa de GeckoLib. Berserker es más grande. */
public class HostileServantRenderer extends BipedEntityRenderer<HostileServantEntity, PlayerEntityModel<HostileServantEntity>> {
    public HostileServantRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5F);
        addFeature(new ArmorFeatureRenderer<>(this, new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
    }

    @Override
    protected void scale(HostileServantEntity entity, MatrixStack matrices, float amount) {
        if (entity.kind() == HostileServantEntity.Kind.BERSERKER) matrices.scale(1.3F, 1.3F, 1.3F);
    }

    @Override
    public Identifier getTexture(HostileServantEntity entity) {
        return FateUBW.id("textures/entity/servant/" + entity.kind().id() + ".png");
    }
}
