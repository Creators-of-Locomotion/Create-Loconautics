package com.lycoris.loconautics.mixin.client.gui;

import com.lycoris.loconautics.client.scrollmenu.ScrollMenuHandler;
import com.lycoris.loconautics.content.grab.GrabClientHandler;
import com.lycoris.loconautics.content.variantpicker.ScrollPickerHandler;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiSelectedItemNameMixin {

    @Shadow
    private int toolHighlightTimer;

    @Shadow
    private ItemStack lastToolHighlight;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;I)V", at = @At("TAIL"))
    private void loconautics$drawPickerKey(GuiGraphics graphics, int yShift, CallbackInfo ci) {
        if (this.toolHighlightTimer <= 0 || this.lastToolHighlight.isEmpty()) return;
        Component hint = ScrollPickerHandler.pickerHint(this.lastToolHighlight);
        if (hint == null) return;
        int alpha = Math.min(255, (int) (this.toolHighlightTimer * 256.0F / 10.0F));
        if (alpha <= 0) return;
        loconautics$drawHint(graphics, yShift, loconautics$keyTag(hint), alpha);
    }

    @Inject(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;I)V", at = @At("TAIL"))
    private void loconautics$drawGrabRotateKey(GuiGraphics graphics, int yShift, CallbackInfo ci) {
        if (!GrabClientHandler.canRotate()) return;
        Component key = GrabClientHandler.ROTATE_MODE.getTranslatedKeyMessage().copy()
                .withStyle(GrabClientHandler.ROTATE_MODE.isDown() ? ChatFormatting.WHITE : ChatFormatting.GRAY);
        Component tag = Component.translatable("gui.loconautics.grab.rotate_hint", key)
                .withStyle(ChatFormatting.DARK_GRAY);
        loconautics$drawHint(graphics, yShift, tag, 255);
    }

    private static Component loconautics$keyTag(Component key) {
        return Component.literal("[").append(key).append("]").withColor(0xFFD700);
    }

    private void loconautics$drawHint(GuiGraphics graphics, int yShift, Component tag, int alpha) {
        Font font = this.minecraft.font;
        int y = graphics.guiHeight() - Math.max(yShift, 59) - font.lineHeight - 1;
        if (!this.minecraft.gameMode.canHurtPlayer()) y += 14;
        int w = font.width(tag);
        graphics.drawStringWithBackdrop(font, tag, (graphics.guiWidth() - w) / 2, y, w,
                FastColor.ARGB32.color(alpha, 0xFFFFFF));
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void loconautics$keepItemName(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ScrollMenuHandler.isOpen()) {
            this.toolHighlightTimer = 40;
        }
    }
}
