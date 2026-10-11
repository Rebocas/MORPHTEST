package com.morphmod.client;

import com.morphmod.config.MorphConfig;
import com.morphmod.morph.MorphKind;
import com.morphmod.morph.MorphManager;
import com.morphmod.morph.MorphRegistry;
import com.morphmod.morph.MorphState;
import com.morphmod.network.ModNetwork;
import com.morphmod.network.MorphActionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Console do Controle de Morph: abas (mobs / entidades / blocos), busca, tamanho e reset. */
public class ConsoleScreen extends Screen {
    private enum Tab {
        MOBS(MorphKind.MOB, "gui.morphmod.tab.mobs"),
        ENTITIES(MorphKind.ENTITY, "gui.morphmod.tab.entities"),
        BLOCKS(MorphKind.BLOCK, "gui.morphmod.tab.blocks");

        final MorphKind kind;
        final String key;

        Tab(MorphKind kind, String key) {
            this.kind = kind;
            this.key = key;
        }
    }

    private record Entry(ResourceLocation id, Component name, ItemStack icon, String search) {}

    private static final int CELL = 22;
    private static final Map<String, Item> SPECIAL_ICONS = Map.of(
            "minecraft:iron_golem", Items.IRON_BLOCK,
            "minecraft:snow_golem", Items.CARVED_PUMPKIN,
            "minecraft:wither", Items.NETHER_STAR,
            "minecraft:ender_dragon", Items.DRAGON_HEAD,
            "minecraft:giant", Items.ZOMBIE_HEAD);

    private final Map<Tab, List<Entry>> all = new EnumMap<>(Tab.class);
    private List<Entry> view = List.of();
    private Tab tab = Tab.MOBS;
    private EditBox search;
    private ScaleSlider slider;
    private ResourceLocation selectedId;
    private Component selectedName = Component.empty();
    private int scrollRow;
    private long lastClickTime;
    private int tickCounter;
    private int sliderDirtyTick = -1;
    private float pendingScale = 1f;

    private int x0, y0, pw, ph, gx, gy, gw, gh, cols, rows;

    public ConsoleScreen() {
        super(Component.translatable("gui.morphmod.title"));
    }

    // ------------------------------------------------------------------ montagem

    @Override
    protected void init() {
        pw = Math.min(width - 16, 360);
        ph = Math.min(height - 28, 250);
        x0 = (width - pw) / 2;
        y0 = Math.max(16, (height - ph) / 2 + 6);
        gx = x0 + 8;
        gy = y0 + 54;
        gw = pw - 16 - 6;
        gh = ph - 54 - 62;
        cols = Math.max(1, gw / CELL);
        rows = Math.max(1, gh / CELL);

        int tw = (pw - 16 - 8) / 3;
        int i = 0;
        for (Tab t : Tab.values()) {
            addRenderableWidget(Button.builder(Component.translatable(t.key), b -> setTab(t))
                    .bounds(x0 + 8 + i * (tw + 4), y0 + 8, tw, 20).build());
            i++;
        }

        String old = search == null ? "" : search.getValue();
        search = new EditBox(font, x0 + 8, y0 + 32, pw - 16, 16, Component.translatable("gui.morphmod.search"));
        search.setHint(Component.translatable("gui.morphmod.search"));
        search.setResponder(s -> refilter());
        addRenderableWidget(search);
        search.setValue(old);

        float cur = MorphManager.get(minecraft.player).scale();
        pendingScale = cur;
        slider = new ScaleSlider(x0 + 8, y0 + ph - 30, 150, 20, cur);
        addRenderableWidget(slider);

        int bx = x0 + 8 + 154;
        int bw = (pw - 16 - 154 - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.morphmod.transform"), b -> doMorph())
                .bounds(bx, y0 + ph - 30, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.morphmod.reset"), b -> doReset())
                .bounds(bx + bw + 4, y0 + ph - 30, bw, 20).build());

        if (all.isEmpty()) buildLists();
        refilter();
    }

    private void buildLists() {
        for (Tab t : Tab.values()) {
            List<Entry> list = new ArrayList<>();
            for (ResourceLocation id : MorphRegistry.list(t.kind)) {
                Component name = nameOf(t.kind, id);
                list.add(new Entry(id, name, icon(t.kind, id),
                        (name.getString() + " " + id).toLowerCase(Locale.ROOT)));
            }
            all.put(t, list);
        }
    }

    private static Component nameOf(MorphKind kind, ResourceLocation id) {
        if (kind == MorphKind.BLOCK) return BuiltInRegistries.BLOCK.get(id).getName();
        return BuiltInRegistries.ENTITY_TYPE.get(id).getDescription();
    }

    private static ItemStack icon(MorphKind kind, ResourceLocation id) {
        if (kind == MorphKind.BLOCK) {
            Block b = BuiltInRegistries.BLOCK.get(id);
            return new ItemStack(b.asItem());
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        SpawnEggItem egg = SpawnEggItem.byId(type);
        if (egg != null) return new ItemStack(egg);
        Item special = SPECIAL_ICONS.get(id.toString());
        if (special != null) return new ItemStack(special);
        Item same = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        if (same != Items.AIR) return new ItemStack(same);
        return new ItemStack(Items.NAME_TAG);
    }

    private void setTab(Tab t) {
        tab = t;
        scrollRow = 0;
        if (search != null && !search.getValue().isEmpty()) search.setValue("");
        refilter();
    }

    private void refilter() {
        String q = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
        List<Entry> src = all.getOrDefault(tab, List.of());
        view = q.isEmpty() ? src : src.stream().filter(en -> en.search().contains(q)).toList();
        scrollRow = 0;
    }

    // ------------------------------------------------------------------ acoes

    private void doMorph() {
        if (selectedId == null) return;
        ModNetwork.CHANNEL.sendToServer(MorphActionPacket.morph(tab.kind, selectedId));
        onClose();
    }

    private void doReset() {
        ModNetwork.CHANNEL.sendToServer(MorphActionPacket.reset());
        onClose();
    }

    private void flushScale() {
        if (sliderDirtyTick >= 0) {
            ModNetwork.CHANNEL.sendToServer(MorphActionPacket.scale(pendingScale));
            sliderDirtyTick = -1;
        }
    }

    @Override
    public void tick() {
        tickCounter++;
        if (sliderDirtyTick >= 0 && tickCounter - sliderDirtyTick >= 4) flushScale();
    }

    @Override
    public void removed() {
        flushScale();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && mx >= gx && mx < gx + cols * CELL && my >= gy && my < gy + rows * CELL) {
            int c = (int) ((mx - gx) / CELL);
            int r = (int) ((my - gy) / CELL);
            int idx = scrollRow * cols + r * cols + c;
            if (idx >= 0 && idx < view.size()) {
                Entry en = view.get(idx);
                long now = Util.getMillis();
                boolean dbl = en.id().equals(selectedId) && now - lastClickTime < 300;
                selectedId = en.id();
                selectedName = en.name();
                lastClickTime = now;
                if (dbl) doMorph();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int totalRows = (view.size() + cols - 1) / cols;
        int max = Math.max(0, totalRows - rows);
        scrollRow = Math.max(0, Math.min(max, scrollRow - (int) Math.signum(delta)));
        return true;
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x0, y0, x0 + pw, y0 + ph, 0xD0101820);
        g.renderOutline(x0, y0, pw, ph, 0xFF3DDC97);
        g.drawCenteredString(font, title, width / 2, y0 - 12, 0xFF3DDC97);
        g.fill(gx - 1, gy - 1, gx + cols * CELL + 1, gy + rows * CELL + 1, 0x55000000);

        Entry hover = null;
        int start = scrollRow * cols;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = start + r * cols + c;
                if (idx >= view.size()) break;
                Entry en = view.get(idx);
                int cx = gx + c * CELL;
                int cy = gy + r * CELL;
                boolean over = mx >= cx && mx < cx + CELL && my >= cy && my < cy + CELL;
                boolean sel = en.id().equals(selectedId);
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, sel ? 0xAA3DDC97 : over ? 0x66FFFFFF : 0x33FFFFFF);
                g.renderItem(en.icon(), cx + 3, cy + 3);
                if (over) hover = en;
            }
        }

        int totalRows = (view.size() + cols - 1) / cols;
        if (totalRows > rows) {
            int bx = gx + cols * CELL + 2;
            int trackH = rows * CELL;
            int barH = Math.max(10, trackH * rows / totalRows);
            int barY = gy + (trackH - barH) * scrollRow / Math.max(1, totalRows - rows);
            g.fill(bx, gy, bx + 3, gy + trackH, 0x44FFFFFF);
            g.fill(bx, barY, bx + 3, barY + barH, 0xFF3DDC97);
        }

        MorphState cur = MorphManager.get(minecraft.player);
        Component curName = Component.translatable("gui.morphmod.original");
        if (cur.isMorphed()) curName = nameOf(cur.kind(), cur.id());
        g.drawString(font, Component.translatable("gui.morphmod.current", curName), x0 + 8, y0 + ph - 58, 0xFFAAAAAA);
        Component sel = selectedId == null
                ? Component.translatable("gui.morphmod.none_selected")
                : Component.translatable("gui.morphmod.selected", selectedName);
        g.drawString(font, sel, x0 + 8, y0 + ph - 46, 0xFFFFFFFF);

        super.render(g, mx, my, pt);

        if (hover != null) {
            g.renderComponentTooltip(font, List.of(hover.name(),
                    Component.literal(hover.id().toString()).withStyle(ChatFormatting.DARK_GRAY)), mx, my);
        }
    }

    // ------------------------------------------------------------------ slider de tamanho (escala logaritmica)

    private static float minScale() {
        return MorphConfig.MIN_SCALE.get().floatValue();
    }

    private static float maxScale() {
        return MorphConfig.MAX_SCALE.get().floatValue();
    }

    private static double toValue(float scale) {
        double min = minScale();
        double max = maxScale();
        double v = Math.log(Math.max(min, Math.min(max, scale)) / min) / Math.log(max / min);
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static float toScale(double v) {
        float s = (float) (minScale() * Math.pow(maxScale() / minScale(), v));
        return Math.abs(s - 1.0f) < 0.04f ? 1.0f : s; // "imã" em 1.00x
    }

    private class ScaleSlider extends AbstractSliderButton {
        ScaleSlider(int x, int y, int w, int h, float scale) {
            super(x, y, w, h, Component.empty(), toValue(scale));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("gui.morphmod.size",
                    String.format(Locale.ROOT, "%.2fx", toScale(value))));
        }

        @Override
        protected void applyValue() {
            pendingScale = toScale(value);
            sliderDirtyTick = tickCounter;
        }
    }
}
