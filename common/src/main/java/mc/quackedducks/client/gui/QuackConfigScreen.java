package mc.quackedducks.client.gui;

import mc.quackedducks.config.QuackConfig;
import mc.quackedducks.network.QuackNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class QuackConfigScreen extends Screen {
    public QuackConfigScreen() {
        super(Component.literal("QuackedMod Configuration"));
    }

    @Override
    protected void init() {
        var config = QuackConfig.get().genericDucks;
        int centerX = this.width / 2;
        int startY = 50;
        int spacing = 26;

        // --- Duck Settings ---

        // Width Slider (0.1 - 5.0)
        this.addRenderableWidget(new QuackSlider(centerX - 100, startY, 200, 20,
                Component.literal("Duck Width: "), config.duckWidth, 0.1f, 5.0f, (val) -> config.duckWidth = val));

        startY += spacing;

        // Height Slider (0.1 - 5.0)
        this.addRenderableWidget(new QuackSlider(centerX - 100, startY, 200, 20,
                Component.literal("Duck Height: "), config.duckHeight, 0.1f, 5.0f, (val) -> config.duckHeight = val));

        startY += spacing;

        // Speed Slider (0.05 - 1.0)
        this.addRenderableWidget(new QuackSlider(centerX - 100, startY, 200, 20,
                Component.literal("Movement Speed: "), (float) config.movementSpeed, 0.05f, 1.0f,
                (val) -> config.movementSpeed = val));

        startY += spacing;

        // Sound Interval Slider (20 - 1200)
        this.addRenderableWidget(new QuackSlider(centerX - 100, startY, 200, 20,
                Component.literal("Quack Sound Freq (Ticks): "), (float) config.ambientSoundInterval, 20.0f, 1200.0f,
                (val) -> config.ambientSoundInterval = val.intValue()));

        // --- Footer Buttons ---

        this.addRenderableWidget(Button.builder(Component.literal("Reset to Defaults"), (button) -> {
            QuackConfig.get().genericDucks = new QuackConfig.GenericDucks();
            this.rebuildWidgets();
        }).bounds(centerX - 100, this.height - 55, 200, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (button) -> {
            QuackConfig.get().validate();
            mc.quackedducks.QuackMod.sendConfigUpdate(new QuackNetwork.UpdateConfigPayload(
                    config.duckWidth, config.duckHeight, config.movementSpeed, config.ambientSoundInterval));
            this.onClose();
        }).bounds(centerX - 100, this.height - 30, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw dark gradient background for premium feel
        this.renderTransparentBackground(guiGraphics);

        // Title
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        // Category Label
        guiGraphics.drawCenteredString(this.font, Component.literal("Entity Parameters"), this.width / 2, 38, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Custom premium-style slider for QuackedMod.
     */
    private static class QuackSlider extends AbstractSliderButton {
        private final Component prefix;
        private final float min;
        private final float max;
        private final java.util.function.Consumer<Float> setter;

        public QuackSlider(int x, int y, int width, int height, Component prefix, float current, float min, float max,
                java.util.function.Consumer<Float> setter) {
            super(x, y, width, height, prefix, (double) Mth.clamp((current - min) / (max - min), 0.0f, 1.0f));
            this.prefix = prefix;
            this.min = min;
            this.max = max;
            this.setter = setter;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            float value = min + (float) (this.value * (max - min));
            // Rounding for cleaner UI
            if (max > 10) {
                this.setMessage(prefix.copy().append(String.format("%.0f", value)));
            } else {
                this.setMessage(prefix.copy().append(String.format("%.2f", value)));
            }
        }

        @Override
        protected void applyValue() {
            float val = min + (float) (this.value * (max - min));
            setter.accept(val);
        }
    }
}
