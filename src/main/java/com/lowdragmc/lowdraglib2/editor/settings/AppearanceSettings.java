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
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextArea;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
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
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.io.FileReader;
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
    private static final int FONT_RUNTIME_FIX_SPECIFICITY = 120102;
    private static final int FONT_RUNTIME_FIX_SOURCE_ORDER = 120102;
    private static ResourceLocation activeStylesheet = StylesheetManager.ORE_MERGED;
    private static FontSettings activeFontSettings = FontSettings.DEFAULT;
    @Nullable
    private static AppearanceSettings activeSettings;
    private static boolean activeStylesheetLoaded;

    public AppearanceSettings() {
        activeSettings = this;
    }

    @Configurable
    @ConfigSearch(searchConfiguratorMethod = "searchStyles")
    @Getter @Setter
    private ResourceLocation stylesheet = StylesheetManager.ORE_MERGED;

    /**
     * Returns the stylesheet most recently applied by the appearance settings.
     * External modular UIs can use this to match the editor's active theme.
     */
    public static ResourceLocation getActiveStylesheet() {
        var settings = activeSettings;
        if (settings != null) {
            return settings.stylesheet;
        }
        loadActiveStylesheetFromFile();
        return activeStylesheet;
    }

    public static boolean isLightThemeActive() {
        var stylesheet = getActiveStylesheet();
        return StylesheetManager.LIGHT.equals(stylesheet) || StylesheetManager.LIGHT_MERGED.equals(stylesheet);
    }

    /**
     * Applies the configured font sizes to an external Modular UI.
     * This uses an important runtime style so settings also affect elements
     * that declare their own compact font size in code.
     */
    public static void applyActiveFontSettings(ModularUI modularUI) {
        var fontSettings = getActiveFontSettings();
        for (var element : modularUI.getAllElements()) {
            element.getStyleBag().removeCandidates(PropertyRegistry.FONT_SIZE, AppearanceSettings::isFontRuntimeFix);
            var fontSize = fontSizeFor(element, fontSettings);
            if (fontSize > 0) {
                setFontSize(element, fontSize);
            }
        }
    }

    private static FontSettings getActiveFontSettings() {
        var settings = activeSettings;
        if (settings != null) {
            return new FontSettings(
                    settings.globalFontSize,
                    settings.textFontSize,
                    settings.buttonFontSize,
                    settings.inputFontSize,
                    settings.textAreaFontSize,
                    settings.progressFontSize
            );
        }
        loadActiveStylesheetFromFile();
        return activeFontSettings;
    }

    private static synchronized void loadActiveStylesheetFromFile() {
        if (activeStylesheetLoaded) return;
        activeStylesheetLoaded = true;
        var settingsFile = FMLLoader.getGamePath().resolve("config").resolve(LDLib2.MOD_ID).resolve("editor.json").toFile();
        if (!settingsFile.exists()) return;
        try (var reader = new FileReader(settingsFile)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            var appearance = root.getAsJsonObject(ID.toString());
            if (appearance == null) return;
            if (appearance.has("stylesheet")) {
                var stylesheet = ResourceLocation.tryParse(appearance.get("stylesheet").getAsString());
                if (stylesheet != null) {
                    activeStylesheet = stylesheet;
                }
            }
            activeFontSettings = FontSettings.from(appearance);
        } catch (Exception e) {
            LDLib2.LOGGER.warn("Failed to read the active appearance stylesheet", e);
        }
    }
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
        activeSettings = this;
        activeStylesheet = stylesheet;
        activeStylesheetLoaded = true;
        activeFontSettings = new FontSettings(globalFontSize, textFontSize, buttonFontSize, inputFontSize, textAreaFontSize, progressFontSize);
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
        applyActiveFontSettings(mui);
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
            setLightTexture(tab, PropertyRegistry.BASE_BACKGROUND, lightRect(0xc8f8fafc, 0, 1, 0xffa8b4c0));
            setLightTexture(tab, PropertyRegistry.HOVER_BACKGROUND, lightRect(0xe0ffffff, 0, 1, 0xff9eacbb));
            setLightTexture(tab, PropertyRegistry.PRESSED_BACKGROUND, lightRect(0xd8d9e8f6, 0, 1, 0xff6f9fd8));
        }
        if (element instanceof Button button && button.hasClass("__white_icon__")) {
            setLightTexture(button, PropertyRegistry.BASE_BACKGROUND, lightRect(0xd0f8fafc, 0, 1, 0xffa8b4c0));
            setLightTexture(button, PropertyRegistry.HOVER_BACKGROUND, lightRect(0xe8ffffff, 0, 1, 0xff8098b2));
            setLightTexture(button, PropertyRegistry.PRESSED_BACKGROUND, lightRect(0xd0e5edf6, 0, 1, 0xff8098b2));
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
        return buildFontStylesheetRaw(new FontSettings(globalFontSize, textFontSize, buttonFontSize, inputFontSize, textAreaFontSize, progressFontSize));
    }

    private static String buildFontStylesheetRaw(FontSettings settings) {
        var builder = new StringBuilder();
        appendFontSizeRule(builder, "text, label, text-field:host, text-area", settings.global());
        appendFontSizeRule(builder, "text, label", settings.text());
        appendFontSizeRule(builder, "button:host .__button_text__, tab:host .__button_text__", settings.button());
        appendFontSizeRule(builder, "text-field:host, .__tag-field_text-field__, .__search-component_text-field__", settings.input());
        appendFontSizeRule(builder, "text-area, code-editor", settings.textArea());
        appendFontSizeRule(builder, "progress-bar:host label, .__progress-bar_label__", settings.progress());
        return builder.toString();
    }

    private static void appendFontSizeRule(StringBuilder builder, String selector, float fontSize) {
        if (fontSize <= 0) return;
        builder.append(selector)
                .append(" { font-size: ")
                .append(fontSize)
                .append("; }\n");
    }

    private static float fontSizeFor(UIElement element, FontSettings settings) {
        float result = settings.global();
        if (element instanceof TextField) {
            return settings.inputOr(result);
        }
        if (element instanceof TextArea) {
            return settings.textAreaOr(result);
        }
        if (element instanceof TextElement) {
            if (element.hasClass("__button_text__")) {
                return settings.buttonOr(result);
            }
            if (element.hasClass("__progress-bar_label__")) {
                return settings.progressOr(result);
            }
            return settings.textOr(result);
        }
        return 0;
    }

    private static void setFontSize(UIElement element, float fontSize) {
        element.getStyleBag().replaceOrPutCandidate(PropertyRegistry.FONT_SIZE, StyleSlot.of(
                PropertyRegistry.FONT_SIZE,
                StyleOrigin.IMPORTANT,
                FONT_RUNTIME_FIX_SPECIFICITY,
                FONT_RUNTIME_FIX_SOURCE_ORDER,
                fontSize
        ));
    }

    private static boolean isFontRuntimeFix(StyleSlot<?> slot) {
        return slot.origin() == StyleOrigin.IMPORTANT &&
                slot.specificity() == FONT_RUNTIME_FIX_SPECIFICITY &&
                slot.sourceOrder() == FONT_RUNTIME_FIX_SOURCE_ORDER;
    }

    private record FontSettings(float global, float text, float button, float input, float textArea, float progress) {
        private static final FontSettings DEFAULT = new FontSettings(0, 0, 0, 0, 0, 0);

        private static FontSettings from(com.google.gson.JsonObject json) {
            return new FontSettings(
                    number(json, "globalFontSize"),
                    number(json, "textFontSize"),
                    number(json, "buttonFontSize"),
                    number(json, "inputFontSize"),
                    number(json, "textAreaFontSize"),
                    number(json, "progressFontSize")
            );
        }

        private static float number(com.google.gson.JsonObject json, String key) {
            return json.has(key) && json.get(key).isJsonPrimitive() && json.get(key).getAsJsonPrimitive().isNumber()
                    ? json.get(key).getAsFloat()
                    : 0;
        }

        private float textOr(float fallback) {
            return text > 0 ? text : fallback;
        }

        private float buttonOr(float fallback) {
            return button > 0 ? button : fallback;
        }

        private float inputOr(float fallback) {
            return input > 0 ? input : fallback;
        }

        private float textAreaOr(float fallback) {
            return textArea > 0 ? textArea : fallback;
        }

        private float progressOr(float fallback) {
            return progress > 0 ? progress : fallback;
        }
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
