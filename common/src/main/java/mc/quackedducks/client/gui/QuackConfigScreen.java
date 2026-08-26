package mc.quackedducks.client.gui;

import mc.quackedducks.QuackMod;
import mc.quackedducks.config.QuackConfig;
import mc.quackedducks.network.QuackNetwork;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * In-game configuration screen opened via {@code /quack config}.
 *
 * <p>Renders sliders for duck width, height, movement speed, and ambient sound
 * frequency. On "Done", validates the config locally and fires an
 * {@link mc.quackedducks.network.QuackNetwork.UpdateConfigPayload} to the server.
 * Changes take effect on all clients after the server re-broadcasts a
 * {@link mc.quackedducks.network.QuackNetwork.SyncConfigPayload}.
 *
 * <p>Sliders edit {@link #draft}, a private copy of the live config, never the
 * {@link QuackConfig} singleton. Only "Done" publishes it, so closing with ESC
 * discards the edits instead of leaving the client's ducks the wrong size until
 * the next sync.
 */
public class QuackConfigScreen extends Screen {
    /** Unsaved working copy. The singleton is only written by the sync handler. */
    private QuackConfig.GenericDucks draft;

    public QuackConfigScreen() {
        super(Component.literal("QuackedMod Configuration"));
    }

    @Override
    protected void init() {
        // Only snapshot on first open - rebuildWidgets() re-enters init() and must keep
        // whatever the player has already dragged.
        if (draft == null) {
            draft = new QuackConfig.GenericDucks(QuackConfig.get().genericDucks);
        }

        int centerX = this.width / 2;
        int startY = 50;
        int spacing = 26;

        // --- Duck Settings ---

        // Width Slider (0.1 - 5.0)
        this.addRenderableWidget(new QuackSlider(centerX - 100, startY, 200, 20,
                Component.literal("Duck Width: "), draft.duckWidth, 0.1f, 5.0f, (val) -> draft.duckWidth = val));

        startY += spacing;

        // Height Slider (0.1 - 5.0)
        this.addRenderableWidget(new QuackSlider(centerX - 100, startY, 200, 20,
                Component.literal("Duck Height: "), draft.duckHeight, 0.1f, 5.0f, (val) -> draft.duckHeight = val));

        startY += spacing;

        // Speed Slider (0.05 - 1.0)
        this.addRenderableWidget(new QuackSlider(centerX - 100, startY, 200, 20,
                Component.literal("Movement Speed: "), (float) draft.movementSpeed, 0.05f, 1.0f,
                (val) -> draft.movementSpeed = val));

        startY += spacing;

        // Sound Interval Slider (20 - 1200)
        this.addRenderableWidget(new QuackSlider(centerX - 100, startY, 200, 20,
                Component.literal("Quack Sound Freq (Ticks): "), (float) draft.ambientSoundInterval, 20.0f, 1200.0f,
                (val) -> draft.ambientSoundInterval = val.intValue()));

        startY += spacing;

        // Migration Cooldown Slider (20 - 12000 ticks; 20=~1s for testing, 12000=10min)
        this.addRenderableWidget(new QuackSlider(centerX - 100, startY, 200, 20,
                Component.literal("Migration Cooldown (Ticks): "), (float) draft.migrationCooldownTicks, 20.0f, 12000.0f,
                (val) -> draft.migrationCooldownTicks = val.intValue()));

        startY += spacing;

        // Dab Chance Slider (1 - 20; 1=always, 5=20% default, 20=5% rare)
        this.addRenderableWidget(new QuackSlider(centerX - 100, startY, 200, 20,
                Component.literal("Dab Chance (1=always, 20=rare): "), (float) draft.dabChance, 1.0f, 20.0f,
                (val) -> draft.dabChance = val.intValue()));

        // --- Footer Buttons ---

        this.addRenderableWidget(Button.builder(Component.literal("Reset to Defaults"), (button) -> {
            draft = new QuackConfig.GenericDucks();
            this.rebuildWidgets();
        }).bounds(centerX - 100, this.height - 55, 200, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (button) -> {
            QuackMod.sendConfigUpdate(QuackNetwork.from(draft, QuackNetwork.UpdateConfigPayload::new));
            this.onClose();
        }).bounds(centerX - 100, this.height - 30, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        // Draw dark gradient background for premium feel
        this.extractTransparentBackground(extractor);

        // Title
        extractor.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        // Category Label
        extractor.centeredText(this.font, Component.literal("Entity Parameters"), this.width / 2, 38, 0xAAAAAA);

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    /** Returns {@code false} so the game keeps running while the GUI is open. */
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
