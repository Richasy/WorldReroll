package dev.richasy.worldreroll;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Preferences apply to the next reroll; no existing world settings are changed here. */
public final class RerollSettingsScreen extends Screen {
    private static boolean lastVillagePreference;
    private final Screen parent;
    private final boolean structures;
    private boolean village;
    private int left, top, panelWidth, cardWidth;

    public RerollSettingsScreen(Screen parent) {
        super(Component.translatable("worldreroll.settings.title"));
        this.parent = parent;
        var server = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
        structures = server != null && server.getWorldGenSettings().options().generateStructures();
        village = structures && lastVillagePreference;
    }

    @Override protected void init() {
        panelWidth = Math.min(380, width - 24);
        left = (width - panelWidth) / 2;
        top = Math.max(6, (height - 218) / 2);
        int content = left + 16, contentWidth = panelWidth - 32;
        cardWidth = (contentWidth - 12) / 2;
        addRenderableOnly(new StringWidget(content, top + 10, contentWidth, 20, title, font));
        text(content, top + 36, contentWidth, "worldreroll.settings.description", 0xFFB5C3D0, 2);
        addRenderableWidget(Button.builder(Component.translatable("worldreroll.settings.random"), ignored -> select(false))
                .bounds(content + 6, top + 72, cardWidth - 12, 20).build());
        var villageButton = addRenderableWidget(Button.builder(Component.translatable("worldreroll.settings.village"), ignored -> select(true))
                .bounds(content + cardWidth + 18, top + 72, cardWidth - 12, 20).build());
        villageButton.active = structures;
        text(content + 8, top + 102, cardWidth - 16, "worldreroll.settings.random_hint", 0xFFB5C3D0, 3);
        text(content + cardWidth + 20, top + 102, cardWidth - 16,
                structures ? "worldreroll.settings.village_hint" : "worldreroll.settings.structures_off",
                structures ? 0xFFB5C3D0 : 0xFFE1BB78, 3);
        text(content + 8, top + 155, contentWidth - 16, "worldreroll.settings.warning", 0xFFE1BB78, 2);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(content, top + 187, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldreroll.settings.continue"), ignored -> confirm())
                .bounds(content + contentWidth - 148, top + 187, 148, 20).build());
    }

    private void text(int x, int y, int maxWidth, String key, int color, int rows) {
        var widget = new MultiLineTextWidget(x, y, Component.translatable(key).withStyle(style -> style.withColor(color & 0xFFFFFF)), font)
                .setMaxWidth(maxWidth).setMaxRows(rows);
        addRenderableOnly(widget);
    }

    private void select(boolean nearVillage) {
        village = nearVillage;
        lastVillagePreference = village;
    }

    private void confirm() {
        if (!RerollController.available(minecraft)) return;
        String name = minecraft.getSingleplayerServer().getWorldData().getLevelName();
        minecraft.gui.setScreen(new ConfirmScreen(accepted -> {
            if (accepted) RerollController.start(minecraft, village);
            else minecraft.gui.setScreen(this);
        }, Component.translatable("worldreroll.confirm.title"),
                Component.translatable(village ? "worldreroll.confirm.village_body" : "worldreroll.confirm.body", name),
                Component.translatable("worldreroll.button"), Component.translatable("gui.cancel")));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(left, top, left + panelWidth, top + 218, 0xF0141C26);
        graphics.outline(left, top, panelWidth, 218, 0xFF4B5B6B);
        graphics.fill(left + 16, top + 61, left + panelWidth - 16, top + 62, 0xFF354454);
        card(graphics, left + 16, !village);
        card(graphics, left + 28 + cardWidth, village);
        graphics.fill(left + 16, top + 149, left + panelWidth - 16, top + 178, 0xFF302B22);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void card(GuiGraphicsExtractor graphics, int x, boolean selected) {
        graphics.fill(x, top + 67, x + cardWidth, top + 141, selected ? 0xFF213A35 : 0xFF1B2632);
        graphics.outline(x, top + 67, cardWidth, 74, selected ? 0xFF7AC7AA : 0xFF354454);
        if (selected) graphics.fill(x, top + 67, x + 3, top + 141, 0xFF7AC7AA);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
