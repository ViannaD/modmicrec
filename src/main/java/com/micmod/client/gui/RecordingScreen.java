package com.micmod.client.gui;

import com.micmod.client.audio.AudioPlayback;
import com.micmod.client.audio.AudioRecorder;
import com.micmod.client.audio.RecordingStorage;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;

import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.IOException;

/**
 * Tela que abre ao clicar com o microfone (botão direito) numa entidade.
 * Layout em duas colunas: lista de categorias (com indicador de gravação)
 * à esquerda, preview 3D da entidade + controles de gravação à direita.
 */
public class RecordingScreen extends Screen {

    private static final String[] CATEGORIES = {"ambient", "hurt", "death"};
    private static final String[] CATEGORY_LABELS = {"Ambient", "Hurt", "Death"};

    private static final int COLOR_PANEL_BG = 0xFFC6C6C6;
    private static final int COLOR_PANEL_BORDER = 0xFF373737;
    private static final int COLOR_ROW_IDLE = 0xFF8B8B8B;
    private static final int COLOR_ROW_HOVER = 0xFFA0A0A0;
    private static final int COLOR_ROW_SELECTED = 0xFF6B6B6B;
    private static final int COLOR_DOT_RECORDED = 0xFF55CC55;
    private static final int COLOR_DOT_EMPTY = 0xFF5A5A5A;
    private static final int COLOR_PREVIEW_BG = 0xFF0A0A0A;
    private static final int COLOR_RECORD = 0xFFCC4B4B;
    private static final int COLOR_RECORD_ACTIVE = 0xFFEE2222;
    private static final int COLOR_PLAY = 0xFF4CAF50;
    private static final int COLOR_DELETE = 0xFFCC4B4B;

    private final LivingEntity entity;
    private String selectedCategory = "ambient";

    private final AudioRecorder recorder = new AudioRecorder();
    private byte[] pendingRecording = null;
    private Clip previewClip = null;
    private boolean muted = false;
    private boolean applyToAllOfType = false;

    private ButtonWidget saveButton;

    private int panelX, panelY, panelW, panelH;
    private final int[] rowBounds = new int[CATEGORIES.length];
    private int listX, listW, rowH;
    private int previewX, previewY, previewW, previewH;
    private int[] networkIcon;
    private int[] recordIcon;
    private int[] playIcon;
    private int[] deleteIcon;
    private int[] muteIcon;

    public RecordingScreen(LivingEntity entity) {
        super(Text.literal("Mic Voicenator"));
        this.entity = entity;
    }

    @Override
    protected void init() {
        panelW = 250;
        panelH = 200;
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        listX = panelX + 10;
        listW = 130;
        rowH = 22;
        int listY = panelY + 32;
        for (int i = 0; i < CATEGORIES.length; i++) {
            rowBounds[i] = listY + i * (rowH + 2);
        }

        previewX = panelX + 150;
        previewW = panelW - 160;
        previewY = panelY + 30;
        previewH = 78;

        int iconY = previewY + previewH + 22;
        int iconSize = 18;
        recordIcon = new int[]{previewX, iconY, iconSize};
        playIcon = new int[]{previewX + iconSize + 8, iconY, iconSize};
        deleteIcon = new int[]{previewX + (iconSize + 8) * 2, iconY, iconSize};
        muteIcon = new int[]{panelX + panelW - 24, iconY, iconSize};
        networkIcon = new int[]{previewX + previewW - 16, previewY + 2, 14};

        saveButton = ButtonWidget.builder(Text.literal("Salvar"), b -> saveRecording())
                .dimensions(panelX + 10, panelY + panelH - 24, panelW - 20, 18)
                .build();
        addDrawableChild(saveButton);
    }

    private void selectCategory(String cat) {
        if (recorder.isRecording()) return;
        stopPreview();
        pendingRecording = null;
        selectedCategory = cat;
    }

    private void toggleRecord() {
        if (recorder.isRecording()) {
            pendingRecording = recorder.stop();
        } else {
            stopPreview();
            try {
                recorder.start();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void playPending() {
        byte[] data = pendingRecording;
        if (data == null && RecordingStorage.exists(entity.getUuid(), selectedCategory)) {
            try {
                data = RecordingStorage.load(entity.getUuid(), selectedCategory);
            } catch (IOException ignored) {
            }
        }
        if (data != null) {
            stopPreview();
            previewClip = AudioPlayback.playPreview(data, () -> previewClip = null);
            applyMuteToPreview();
        }
    }

    private void deleteRecording() {
        RecordingStorage.delete(entity.getUuid(), selectedCategory);
        pendingRecording = null;
    }

    private void saveRecording() {
        if (pendingRecording != null) {
            try {
                RecordingStorage.save(entity.getUuid(), selectedCategory, pendingRecording);
                pendingRecording = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopPreview() {
        if (previewClip != null) {
            previewClip.close();
            previewClip = null;
        }
    }

    private void toggleMute() {
        muted = !muted;
        applyMuteToPreview();
    }

    private void applyMuteToPreview() {
        if (previewClip != null && previewClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gain = (FloatControl) previewClip.getControl(FloatControl.Type.MASTER_GAIN);
            gain.setValue(muted ? gain.getMinimum() : 0f);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        context.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, COLOR_PANEL_BORDER);
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_PANEL_BG);

        context.drawText(this.textRenderer, Text.literal("Drift's Mob Voicenator"), panelX + 10, panelY + 8, 0x404040, false);

        renderCategoryList(context, mouseX, mouseY);
        renderPreviewPanel(context, mouseX, mouseY, delta);
        renderTransportControls(context, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderCategoryList(DrawContext context, int mouseX, int mouseY) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            String cat = CATEGORIES[i];
            int y = rowBounds[i];
            boolean hovered = isInside(listX, y, listW, rowH, mouseX, mouseY);
            boolean selected = cat.equals(selectedCategory);
            int bg = selected ? COLOR_ROW_SELECTED : hovered ? COLOR_ROW_HOVER : COLOR_ROW_IDLE;
            context.fill(listX, y, listX + listW, y + rowH, bg);
            context.drawBorder(listX, y, listW, rowH, COLOR_PANEL_BORDER);
            context.drawText(this.textRenderer, Text.literal(CATEGORY_LABELS[i]), listX + 8, y + (rowH - 8) / 2, 0xFFFFFF, false);

            int dotSize = 6;
            int dotX = listX + listW - dotSize - 6;
            int dotY = y + (rowH - dotSize) / 2;
            context.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, selected ? COLOR_DOT_RECORDED : COLOR_DOT_EMPTY);
        }
    }

    private void renderPreviewPanel(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawText(this.textRenderer, entity.getDisplayName(), previewX, panelY + 10, 0x202020, false);

        context.fill(previewX, previewY, previewX + previewW, previewY + previewH, COLOR_PREVIEW_BG);
        context.drawBorder(previewX, previewY, previewW, previewH, COLOR_PANEL_BORDER);

        int entX = previewX + previewW / 2;
        int entY = previewY + previewH - 10;
        int scale = (int) (previewH * 0.45);
        InventoryScreen.drawEntity(context, entX, entY, scale,
                (float) (entX - mouseX), (float) (entX - 50 - mouseY), entity);

        boolean netHover = isInside(networkIcon[0], networkIcon[1], networkIcon[2], networkIcon[2], mouseX, mouseY);
        fillCircle(context, networkIcon[0] + networkIcon[2] / 2, networkIcon[1] + networkIcon[2] / 2,
                networkIcon[2] / 2, applyToAllOfType ? 0xFF4C8BF5 : (netHover ? 0xFF666666 : 0xFF444444));

        int barX = previewX;
        int barY = previewY + previewH + 6;
        int barW = previewW;
        int barH = 8;
        context.fill(barX, barY, barX + barW, barY + barH, 0xFF2B2B2B);
        double elapsed = recorder.isRecording() ? recorder.getElapsedSeconds() : 0.0;
        double frac = Math.min(1.0, elapsed / AudioRecorder.MAX_SECONDS);
        int segments = 24;
        int filled = (int) Math.round(segments * frac);
        int segW = barW / segments;
        for (int i = 0; i < segments; i++) {
            int x = barX + i * segW;
            int color = i < filled ? 0xFF55CC55 : 0xFF454545;
            context.fill(x + 1, barY + 1, x + segW - 1, barY + barH - 1, color);
        }

        String timeText = String.format("%.1f / %.1fs", elapsed, (double) AudioRecorder.MAX_SECONDS);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(timeText), previewX + previewW / 2, barY + barH + 4, 0xAAAAAA);
    }

    private void renderTransportControls(DrawContext context, int mouseX, int mouseY) {
        boolean recording = recorder.isRecording();
        drawIconButton(context, recordIcon, recording ? COLOR_RECORD_ACTIVE : COLOR_RECORD, mouseX, mouseY,
                recording ? "\u25A0" : "\u25CF");
        drawIconButton(context, playIcon, COLOR_PLAY, mouseX, mouseY, "\u25B6");
        drawIconButton(context, deleteIcon, COLOR_DELETE, mouseX, mouseY, "\u2715");
        drawIconButton(context, muteIcon, muted ? 0xFF888888 : 0xFF6B9BD1, mouseX, mouseY, muted ? "\u00D7" : "\u266A");

        boolean hasSaved = RecordingStorage.exists(entity.getUuid(), selectedCategory);
        boolean hasPending = pendingRecording != null;
        String status = hasPending ? "\u00a7eGravação pronta (não salva)"
                : hasSaved ? "\u00a7aGravação salva" : "\u00a77Sem gravação";
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status), panelX + panelW / 2,
                muteIcon[1] + muteIcon[2] + 6, 0xFFFFFF);
    }

    private void drawIconButton(DrawContext context, int[] bounds, int color, int mouseX, int mouseY, String symbol) {
        int x = bounds[0], y = bounds[1], size = bounds[2];
        boolean hovered = isInside(x, y, size, size, mouseX, mouseY);
        int cx = x + size / 2;
        int cy = y + size / 2;
        fillCircle(context, cx, cy, size / 2, hovered ? lighten(color) : color);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(symbol), cx, cy - 4, 0xFFFFFF);
    }

    private static int lighten(int argbColor) {
        int a = (argbColor >> 24) & 0xFF;
        int r = Math.min(255, ((argbColor >> 16) & 0xFF) + 30);
        int g = Math.min(255, ((argbColor >> 8) & 0xFF) + 30);
        int b = Math.min(255, (argbColor & 0xFF) + 30);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void fillCircle(DrawContext context, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
            context.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static boolean isInside(int x, int y, int w, int h, int px, int py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < CATEGORIES.length; i++) {
                if (isInside(listX, rowBounds[i], listW, rowH, (int) mouseX, (int) mouseY)) {
                    selectCategory(CATEGORIES[i]);
                    return true;
                }
            }
            if (isInside(recordIcon[0], recordIcon[1], recordIcon[2], recordIcon[2], (int) mouseX, (int) mouseY)) {
                toggleRecord();
                return true;
            }
            if (isInside(playIcon[0], playIcon[1], playIcon[2], playIcon[2], (int) mouseX, (int) mouseY)) {
                playPending();
                return true;
            }
            if (isInside(deleteIcon[0], deleteIcon[1], deleteIcon[2], deleteIcon[2], (int) mouseX, (int) mouseY)) {
                deleteRecording();
                return true;
            }
            if (isInside(muteIcon[0], muteIcon[1], muteIcon[2], muteIcon[2], (int) mouseX, (int) mouseY)) {
                toggleMute();
                return true;
            }
            if (isInside(networkIcon[0], networkIcon[1], networkIcon[2], networkIcon[2], (int) mouseX, (int) mouseY)) {
                applyToAllOfType = !applyToAllOfType;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        super.tick();
        if (recorder.isRecording() && recorder.getElapsedSeconds() >= AudioRecorder.MAX_SECONDS) {
            pendingRecording = recorder.stop();
        }
    }

    @Override
    public void close() {
        if (recorder.isRecording()) {
            recorder.stop();
        }
        stopPreview();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
