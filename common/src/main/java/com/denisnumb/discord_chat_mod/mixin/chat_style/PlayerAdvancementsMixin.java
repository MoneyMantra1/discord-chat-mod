package com.denisnumb.discord_chat_mod.mixin.chat_style;

import com.denisnumb.discord_chat_mod.MinecraftEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Shadow
    public abstract AdvancementProgress getOrStartProgress(AdvancementHolder advancementHolder);

    @Unique
    private boolean discord_chat_mod$wasDoneBeforeAward;

    @Inject(
            method = "award(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z",
            at = @At("HEAD")
    )
    private void discord_chat_mod$capturePreAwardState(
            AdvancementHolder advancementHolder,
            String criterionName,
            CallbackInfoReturnable<Boolean> cir
    ) {
        this.discord_chat_mod$wasDoneBeforeAward = this.getOrStartProgress(advancementHolder).isDone();
    }

    @Inject(
            method = "award(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z",
            at = @At("RETURN")
    )
    private void discord_chat_mod$handleCompletedAdvancement(
            AdvancementHolder advancementHolder,
            String criterionName,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }

        AdvancementProgress progress = this.getOrStartProgress(advancementHolder);
        if (!this.discord_chat_mod$wasDoneBeforeAward && progress.isDone()) {
            MinecraftEvents.handleAdvancementMade(this.player, advancementHolder);
        }
    }
}
