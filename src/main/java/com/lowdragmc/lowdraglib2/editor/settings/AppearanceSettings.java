package com.lowdragmc.lowdraglib2.editor.settings;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSearch;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.SearchComponentConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SDFRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.gui.ui.style.StyleOrigin;
import com.lowdragmc.lowdraglib2.gui.ui.style.StyleSlot;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.lowdragmc.lowdraglib2.utils.search.IResultHandler;
import com.mojang.serialization.Codec;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public class AppearanceSettings implements Settings {
    public static final ResourceLocation ID = LDLib2.id("appearance");
    public static final Codec<AppearanceSettings> CODEC = PersistedParser.createCodec(AppearanceSettings::new);
    private static final int LIGHT_RUNTIME_FIX_SPECIFICITY = 120101;
    private static final int LIGHT_RUNTIME_FIX_SOURCE_ORDER = 120101;

    @Configurable
    @ConfigSearch(searchConfiguratorMethod = "searchStyles")
    @Getter @Setter
    private ResourceLocation stylesheet = StylesheetManager.ORE_MERGED;
    @Persisted(key = "windowSize")
    @Getter @Setter
    private int screenScale = -1;
    @Configurable(name = "settings.ldlib2.appearance.font.global")
    @ConfigNumber(range = {0, 48}, wheel = 1)
    @Getter @Setter
    private float globalFontSize = 0;
    @Configurable(name = "settings.ldlib2.appearance.font.text")
    @ConfigNumber(range = {0, 48}, wheel = 1)
    @Getter @Setter
    private float textFontSize = 0;
    @Configurable(name = "settings.ldlib2.appearance.font.button")
    @ConfigNumber(range = {0, 48}, wheel = 1)
    @Getter @Setter
    private float buttonFontSize = 0;
    @Configurable(name = "settings.ldlib2.appearance.font.input")
    @ConfigNumber(range = {0, 48}, wheel = 1)
    @Getter @Setter
    private float inputFontSize = 0;
    @Configurable(name = "settings.ldlib2.appearance.font.text_area")
    @ConfigNumber(range = {0, 48}, wheel = 1)
    @Getter @Setter
    private float textAreaFontSize = 0;
    @Configurable(name = "settings.ldlib2.appearance.font.progress")
    @ConfigNumber(range = {0, 48}, wheel = 1)
    @Getter @Setter
    private float progressFontSize = 0;

    // runtime
    @Nullable
    private Stylesheet currentStylesheet;
    @Nullable
    private Stylesheet currentFontStylesheet;
    private String currentFontStylesheetRaw = "";
    @Nullable
    private WeakReference<ModularUI> appliedModularUI;
    private final UIEventListener onMuiChangedListener = this::onModularUIChanged;

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public String getPath() {
        return "Appearance";
    }

    @Override
    public void onApply(Editor editor) {
        if (!editor.hasEventListener(UIEvents.MUI_CHANGED, onMuiChangedListener)) {
            editor.addEventListener(UIEvents.MUI_CHANGED, onMuiChangedListener);
        }
        applyStylesheet(editor);
        // screenScale
        var minecraft = Minecraft.getInstance();
        var guiScale = minecraft.options.guiScale();
        var maxScale =  minecraft.getWindow().calculateScale(0, minecraft.isEnforceUnicode());
        if (screenScale > maxScale) {
            screenScale = maxScale;
        }
        if (guiScale.get() != screenScale) {
            guiScale.set(screenScale);
            Minecraft.getInstance().resizeDisplay();
        }
    }

    private void onModularUIChanged(UIEvent event) {
        if (event.currentElement instanceof Editor editor) {
            applyStylesheet(editor);
        }
    }

    private void applyStylesheet(Editor editor) {
        var stylesheet = StylesheetManager.INSTANCE.getStylesheet(this.stylesheet);
        if (stylesheet == null) {
            return;
        }
        var fontStylesheetRaw = buildFontStylesheetRaw();

        var mui = editor.getModularUI();
        if (mui == null) {
            appliedModularUI = null;
            currentStylesheet = stylesheet;
            currentFontStylesheet = createFontStylesheet(fontStylesheetRaw);
            currentFontStylesheetRaw = fontStylesheetRaw;
            return;
        }

        var previousMui = appliedModularUI == null ? null : appliedModularUI.get();
        var sameMui = previousMui == mui;
        var sameStylesheet = currentStylesheet == stylesheet;
        var sameFontStylesheet = Objects.equals(currentFontStylesheetRaw, fontStylesheetRaw);
        if (sameMui && sameStylesheet && sameFontStylesheet) {
            applyRuntimeThemeFixes(mui);
            return;
        }

        if (previousMui != null && !sameMui) {
            if (currentStylesheet != null) {
                previousMui.getStyleEngine().removeStylesheet(currentStylesheet);
            }
            if (currentFontStylesheet != null) {
                previousMui.getStyleEngine().removeStylesheet(currentFontStylesheet);
            }
        } else if (previousMui != null) {
            if (!sameStylesheet && currentStylesheet != null) {
                previousMui.getStyleEngine().removeStylesheet(currentStylesheet);
            }
            if (!sameFontStylesheet && currentFontStylesheet != null) {
                previousMui.getStyleEngine().removeStylesheet(currentFontStylesheet);
            }
        }
        if (!sameMui || !sameStylesheet) {
            mui.getStyleEngine().addStylesheet(stylesheet);
        }
        if (!sameMui || !sameFontStylesheet) {
            currentFontStylesheet = createFontStylesheet(fontStylesheetRaw);
            if (currentFontStylesheet != null) {
                mui.getStyleEngine().addStylesheet(currentFontStylesheet);
            }
        }
        appliedModularUI = new WeakReference<>(mui);
        currentStylesheet = stylesheet;
        currentFontStylesheetRaw = fontStylesheetRaw;
        applyRuntimeThemeFixes(mui);
    }

    private void applyRuntimeThemeFixes(ModularUI mui) {
        if (isLightStylesheet()) {
            removeKnownGlobalThemeStylesheets(mui);
            for (var element : mui.getAllElements()) {
                applyLightElementFixes(element);
            }
            return;
        }
        for (var element : mui.getAllElements()) {
            clearLightElementFixes(element);
        }
    }

    private boolean isLightStylesheet() {
        return StylesheetManager.LIGHT.equals(stylesheet) || StylesheetManager.LIGHT_MERGED.equals(stylesheet);
    }

    private void removeKnownGlobalThemeStylesheets(ModularUI mui) {
        var styleEngine = mui.getStyleEngine();
        for (var location : List.of(
                StylesheetManager.GDP,
                StylesheetManager.GDP_MERGED,
                StylesheetManager.MC,
                StylesheetManager.MC_MERGED,
                StylesheetManager.MODERN,
                StylesheetManager.MODERN_MERGED,
                StylesheetManager.ORE,
                StylesheetManager.ORE_MERGED
        )) {
            var sheet = StylesheetManager.INSTANCE.getStylesheet(location);
            if (sheet != null) {
                styleEngine.removeStylesheet(sheet);
            }
        }
    }

    private void applyLightElementFixes(UIElement element) {
        if (element instanceof Tab tab) {
            setLightTexture(tab, PropertyRegistry.BASE_BACKGROUND, lightRect(0xc8f8fafc, 3, 1, 0xffa8b4c0));
            setLightTexture(tab, PropertyRegistry.HOVER_BACKGROUND, lightRect(0xe0ffffff, 3, 1, 0xff9eacbb));
            setLightTexture(tab, PropertyRegistry.PRESSED_BACKGROUND, lightRect(0xd8d9e8f6, 3, 1, 0xff6f9fd8));
        }
        if (element instanceof Button button && button.hasClass("__white_icon__")) {
            setLightTexture(button, PropertyRegistry.BASE_BACKGROUND, lightRect(0xd0f8fafc, 3, 1, 0xffa8b4c0));
            setLightTexture(button, PropertyRegistry.HOVER_BACKGROUND, lightRect(0xe8ffffff, 3, 1, 0xff8098b2));
            setLightTexture(button, PropertyRegistry.PRESSED_BACKGROUND, lightRect(0xd0e5edf6, 3, 1, 0xff8098b2));
        }
    }

    private static void setLightTexture(UIElement element, com.lowdragmc.lowdraglib2.gui.ui.style.Property<IGuiTexture> property, IGuiTexture texture) {
        element.getStyleBag().replaceOrPutCandidate(property, StyleSlot.of(
                property,
                StyleOrigin.IMPORTANT,
                LIGHT_RUNTIME_FIX_SPECIFICITY,
                LIGHT_RUNTIME_FIX_SOURCE_ORDER,
                texture
        ));
    }

    private void clearLightElementFixes(UIElement element) {
        if (element instanceof Tab || element instanceof Button button && button.hasClass("__white_icon__")) {
            element.getStyleBag().removeCandidates(PropertyRegistry.BASE_BACKGROUND, AppearanceSettings::isLightRuntimeFix);
            element.getStyleBag().removeCandidates(PropertyRegistry.HOVER_BACKGROUND, AppearanceSettings::isLightRuntimeFix);
            element.getStyleBag().removeCandidates(PropertyRegistry.PRESSED_BACKGROUND, AppearanceSettings::isLightRuntimeFix);
        }
    }

    private static boolean isLightRuntimeFix(com.lowdragmc.lowdraglib2.gui.ui.style.StyleSlot<?> slot) {
        return slot.origin() == StyleOrigin.IMPORTANT &&
                slot.specificity() == LIGHT_RUNTIME_FIX_SPECIFICITY &&
                slot.sourceOrder() == LIGHT_RUNTIME_FIX_SOURCE_ORDER;
    }

    private static SDFRectTexture lightRect(int color, float radius, float stroke, int borderColor) {
        return SDFRectTexture.of(color).setRadius(radius).setStroke(stroke).setBorderColor(borderColor);
    }

    private String buildFontStylesheetRaw() {
        var builder = new StringBuilder();
        appendFontSizeRule(builder, "text, label, text-field:host, text-area", globalFontSize);
        appendFontSizeRule(builder, "text, label", textFontSize);
        appendFontSizeRule(builder, "button:host .__button_text__, tab:host .__button_text__", buttonFontSize);
        appendFontSizeRule(builder, "text-field:host, .__tag-field_text-field__, .__search-component_text-field__", inputFontSize);
        appendFontSizeRule(builder, "text-area, code-editor", textAreaFontSize);
        appendFontSizeRule(builder, "progress-bar:host label, .__progress-bar_label__", progressFontSize);
        return builder.toString();
    }

    private void appendFontSizeRule(StringBuilder builder, String selector, float fontSize) {
        if (fontSize <= 0) return;
        builder.append(selector)
                .append(" { font-size: ")
                .append(fontSize)
                .append("; }\n");
    }

    @Nullable
    private Stylesheet createFontStylesheet(String raw) {
        if (raw.isBlank()) {
            return null;
        }
        var stylesheet = Stylesheet.parse(raw);
        stylesheet.setName("appearance-font-overrides");
        return stylesheet;
    }

    private SearchComponentConfigurator.ISearchConfigurator<ResourceLocation> searchStyles() {
        return new SearchComponentConfigurator.ISearchConfigurator<>() {
            @Override
            @Nonnull
            public ResourceLocation defaultValue() {
                return StylesheetManager.ORE_MERGED;
            }

            @Override
            public void search(String word, IResultHandler<ResourceLocation> searchHandler) {
                var lowerWord = word.toLowerCase();
                if (stylesheet != null && lowerWord.equals(stylesheet.toString().toLowerCase())) {
                    lowerWord = "";
                }
                var candidates = new LinkedHashSet<ResourceLocation>(List.of(
                        StylesheetManager.GDP_MERGED,
                        StylesheetManager.MC_MERGED,
                        StylesheetManager.MODERN_MERGED,
                        StylesheetManager.ORE_MERGED,
                        StylesheetManager.LIGHT_MERGED
                ));
                for (var key : StylesheetManager.INSTANCE.getAllPackStylesheets()) {
                    if (key.getPath().endsWith(".lss")) {
                        key = key.withPath(key.getPath().substring(0, key.getPath().length() - ".lss".length()));
                    }
                    candidates.add(key);
                }
                for (var key : candidates) {
                    if (Thread.currentThread().isInterrupted()) return;
                    if (key.toString().toLowerCase().contains(lowerWord)) {
                        searchHandler.acceptResult(key);
                    }
                }
            }

            @Override
            @Nonnull
            public String resultText(@NotNull ResourceLocation value) {
                return value.toString();
            }

            @Override
            public UIElementProvider<ResourceLocation> candidateUIProvider() {
                return UIElementProvider.text(res -> Component.literal(res.toString()));
            }
        };
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        Settings.super.buildConfigurator(father);
        // scale
        var minecraft = Minecraft.getInstance();
        var guiScale = minecraft.options.guiScale();
        var maxScale =  minecraft.getWindow().calculateScale(0, minecraft.isEnforceUnicode());
        var scales = new ArrayList<Integer>(maxScale + 1);
        for (int i = 0; i <= maxScale; i++) {
            scales.add(i);
        }
        father.addConfiguratorAt(new SelectorConfigurator<>("ldlib.gui.editor.menu.view.window_size", () -> screenScale, scale -> {
            guiScale.set(scale);
            setScreenScale(scale);
        }, -1, true, scales, scale -> scale == 0 ? "options.guiScale.auto" : scale + ""), 1);
    }
}
