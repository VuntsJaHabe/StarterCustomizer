package com.example.startercustomizer.client;

import com.cobblemon.mod.common.client.CobblemonResources;
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

abstract class StarterFrame extends Screen {
    static final int WIDTH = 239;
    static final int HEIGHT = 197;
    static final int RIGHT_PANE = 134;
    static final int RIGHT_WIDTH = 99;
    private static final ResourceLocation BASE = texture("base");
    private static final ResourceLocation BACKGROUND = texture("background");
    private static final ResourceLocation BACKGROUND_BALL = texture("background_poke_ball");
    private static final ResourceLocation PLATFORM = texture("platform_base");
    private static final ResourceLocation BUTTON = texture("choose_button");

    private ResourceLocation typePlatform;

    StarterFrame(Component title) {
        super(title);
    }

    final int left() {
        return (width - WIDTH) / 2;
    }

    final int top() {
        return (height - HEIGHT) / 2;
    }

    final void addPreview(RenderablePokemon pokemon) {
        ModelWidget preview = createPreview(pokemon);
        if (preview != null) {
            addRenderableWidget(preview);
        }
    }

    final ModelWidget createPreview(RenderablePokemon pokemon) {
        typePlatform = null;
        if (pokemon == null) {
            return null;
        }

        String typeId = pokemon.getForm().getPrimaryType().getShowdownId();
        ResourceLocation candidate = ResourceLocation.tryParse(
                "cobblemon:textures/gui/starterselection/starter_platform_base_" + typeId + ".png");
        if (candidate != null && minecraft.getResourceManager().getResource(candidate).isPresent()) {
            typePlatform = candidate;
        }
        return new ModelWidget(left() + 6, top() + 17, 118, 100,
                pokemon, 2.7f, 0f, -12, true, false, 15);
    }

    @Override
    public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left();
        int y = top();
        int frame = minecraft.level == null ? 0 : (int) (minecraft.level.getGameTime() / 3 % 16);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(BACKGROUND, x + 6, y + 17, 0f, 0f, 118, 100, 118, 100);
        graphics.enableScissor(x + 6, y + 17, x + 124, y + 117);
        graphics.blit(BACKGROUND_BALL, x + 17, y + 30, 0f, frame * 109f, 109, 109, 109, 1744);
        graphics.blit(PLATFORM, x + 14, y + 105, 0f, 0f, 113, 32, 113, 32);
        if (typePlatform != null) {
            graphics.blit(typePlatform, x + 14, y + 99, 0f, 0f, 113, 30, 113, 30);
        }
        graphics.disableScissor();
        graphics.blit(BASE, x, y, 0f, 0f, WIDTH, HEIGHT, WIDTH, HEIGHT);
        RenderSystem.disableBlend();
    }

    final void drawHeading(GuiGraphics graphics, Component title) {
        graphics.drawString(font, cobblemonFont(title), left() + 7, top() + 4, 0xFFFFFF);
    }

    final void drawPreviewName(GuiGraphics graphics, Component label, int relativeY) {
        Component text = cobblemonFont(label);
        graphics.enableScissor(left() + 8, top() + relativeY, left() + 122, top() + relativeY + 12);
        graphics.drawCenteredString(font, text, left() + 65, top() + relativeY, 0xFFFFFF);
        graphics.disableScissor();
    }

    static Component cobblemonFont(Component text) {
        return text.copy().withStyle(style -> style.withFont(CobblemonResources.INSTANCE.getDEFAULT_LARGE()));
    }

    static Button button(Component label, int x, int y, int width, Button.OnPress press) {
        return new Button(Button.builder(label, press).bounds(x, y, width, 14)) {
            @Override
            protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                graphics.blit(BUTTON, getX(), getY(), getWidth(), getHeight(),
                        0f, active && isHoveredOrFocused() ? 14f : 0f, 106, 14, 106, 28);
                RenderSystem.disableBlend();
                renderScrollingString(graphics, Minecraft.getInstance().font, cobblemonFont(getMessage()),
                        getX() + 3, getY(), getRight() - 3, getBottom(), active ? 0xFFFFFF : 0xA0A0A0);
            }
        };
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/starterselection/" + name + ".png");
    }

    @Override
    public final boolean isPauseScreen() {
        return false;
    }
}
