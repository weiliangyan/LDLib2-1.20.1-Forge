package com.lowdragmc.lowdraglib2.gui.ui.elements;

import com.google.common.util.concurrent.Runnables;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.gui.texture.DynamicTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.style.Property;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.gui.util.ITreeNode;
import com.lowdragmc.lowdraglib2.integration.kjs.KJSBindings;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.MethodsReturnNonnullByDefault;
import org.appliedenergistics.yoga.*;
import org.joml.Vector2f;

import javax.annotation.Nonnull;
import org.jetbrains.annotations.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@Accessors(chain = true)
@KJSBindings
public class Menu<K, T> extends UIElement {
    @Configurable(name = "MenuStyle")
    public class MenuStyle extends Style {
        private static final Property<?>[] PROPERTIES = new Property[] {
                PropertyRegistry.NODE_BACKGROUND,
                PropertyRegistry.LEAF_BACKGROUND,
                PropertyRegistry.NODE_HOVER_BACKGROUND,
                PropertyRegistry.LEAF_HOVER_BACKGROUND,
                PropertyRegistry.ARROW,
        };

        public MenuStyle() {
            super(Menu.this);
        }

        @Override
        protected Property<?>[] getProperties() {
            return PROPERTIES;
        }

        public IGuiTexture nodeTexture() {
            return getValueSave(PropertyRegistry.NODE_BACKGROUND);
        }

        public MenuStyle nodeTexture(IGuiTexture texture) {
            set(PropertyRegistry.NODE_BACKGROUND, texture);
            return this;
        }

        public IGuiTexture leafTexture() {
            return getValueSave(PropertyRegistry.LEAF_BACKGROUND);
        }

        public MenuStyle leafTexture(IGuiTexture texture) {
            set(PropertyRegistry.LEAF_BACKGROUND, texture);
            return this;
        }

        public IGuiTexture nodeHoverTexture() {
            return getValueSave(PropertyRegistry.NODE_HOVER_BACKGROUND);
        }

        public MenuStyle nodeHoverTexture(IGuiTexture texture) {
            set(PropertyRegistry.NODE_HOVER_BACKGROUND, texture);
            return this;
        }

        public IGuiTexture leafHoverTexture() {
            return getValueSave(PropertyRegistry.LEAF_HOVER_BACKGROUND);
        }

        public MenuStyle leafHoverTexture(IGuiTexture texture) {
            set(PropertyRegistry.LEAF_HOVER_BACKGROUND, texture);
            return this;
        }

        public IGuiTexture arrowIcon() {
            return getValueSave(PropertyRegistry.ARROW);
        }

        public MenuStyle arrowIcon(IGuiTexture texture) {
            set(PropertyRegistry.ARROW, texture);
            return this;
        }
    }
    public final ITreeNode<K, T> root;
    @Getter
    private final MenuStyle menuStyle = new MenuStyle();
    @Nonnull
    protected UIElementProvider<K> uiProvider;
    @Setter @Nullable
    protected Consumer<ITreeNode<K, T>> onNodeClicked;
    @Setter
    protected boolean autoClose = true;
    @Getter
    protected final Map<ITreeNode<K, T>, UIElement> nodeUIs = new LinkedHashMap<>();
    @Setter
    protected Function<ITreeNode<K, T>, IGuiTexture> textureProvider = node -> DynamicTexture.of(() -> node.isLeaf() ? menuStyle.leafTexture() : menuStyle.nodeTexture());
    @Setter
    protected Function<ITreeNode<K, T>, IGuiTexture> hoverTextureProvider = node -> DynamicTexture.of(() -> node.isLeaf() ? menuStyle.leafHoverTexture() : menuStyle.nodeHoverTexture());
    @Setter
    protected Runnable onClose = Runnables.doNothing();
    // runtime
    @Nullable
    protected Menu<K, T> parentMenu;
    @Nullable
    protected ITreeNode<K, T> openedNode;
    @Nullable
    protected Menu<K, T> opened;
    protected boolean rootPopup;

    public Menu(ITreeNode<K, T> root) {
        this(root, (key) -> new TextElement().setText(key.toString()));
    }

    public Menu(ITreeNode<K, T> root, UIElementProvider<K> uiProvider) {
        this.root = root;
        this.uiProvider = uiProvider;

        getLayout().paddingAll(2);
        getLayout().gapAll(2);
        getLayout().positionType(TaffyPosition.ABSOLUTE);
        getLayout().minWidth(120);
        getStyle().backgroundTexture(Sprites.RECT_SOLID);
        getStyle().zIndex(100);
        setOverflowVisible(true);
        setFocusable(true);
        addEventListener(UIEvents.BLUR, this::onBlur, true);

        initMenu();
        internalSetup();
    }

    @Override
    public String name() {
        return "menu";
    }

    protected void onBlur(UIEvent event) {
        if (event.relatedTarget != null && isInMenuTree(event.relatedTarget)) { // focus on children or opened submenus
            return;
        }

        if (event.target == this) { // lose focus
            if (isSelfOrOpenedMenuHover() && event.relatedTarget == null) {
                focus();
            } else {
                if (parentMenu != null && parentMenu.isSelfOrOpenedMenuHover()) {
                    focus();
                } else if(autoClose) {
                    close();
                }
            }
        } else { // child lose focus
            if (event.relatedTarget == null && isSelfOrOpenedMenuHover()) {
                focus();
            } else {
                if(autoClose) {
                    close();
                }
            }
        }
    }

    @Override
    protected void onLayoutChanged() {
        super.onLayoutChanged();
        var mui = getModularUI();
        if (mui != null) {
            // if outside the screen, move it back to the screen
            var screenWidth = mui.getScreenWidth();
            var screenHeight = mui.getScreenHeight();
            var x = getPositionX();
            var y = getPositionY();
            var width = getSizeWidth();
            var height = getSizeHeight();
            // check head out of screen
            if (y < 0) {
                layout(layout -> layout.top(getLayoutY() - y));
            } else if (y + height > screenHeight) {
                layout(layout -> layout.top(getLayoutY() + screenHeight - (y + height)));
            }
            if (x < 0) {
                layout(layout -> layout.left(getLayoutX() - x));
            } else if (x + width > screenWidth) {
                if (x > width && parentMenu != null && !rootPopup) {
                    // move to the left first
                    layout(layout -> layout.left(0 - width));
                } else {
                    layout(layout -> layout.left(getLayoutX() + screenWidth - (x + width)));
                }
            }
        }
    }

    @Override
    protected void onAdded() {
        var mui = getModularUI();
        if (mui != null && !rootPopup) {
            mui.requestFocus(this);
        }
    }

    protected boolean isInMenuTree(@Nullable UIElement element) {
        if (element == null) {
            return false;
        }
        if (this == element || this.isAncestorOf(element)) {
            return true;
        }
        return opened != null && opened.isInMenuTree(element);
    }

    protected boolean isSelfOrOpenedMenuHover() {
        var mui = getModularUI();
        return mui != null && isInMenuTree(mui.getLastHoveredElement());
    }

    public Menu<K, T> setUiProvider(UIElementProvider<K> uiProvider) {
        this.uiProvider = uiProvider;
        clearAllChildren();
        initMenu();
        return this;
    }

    public Menu<K, T> menuStyle(Consumer<MenuStyle> menuStyle) {
        menuStyle.accept(this.menuStyle);
        return this;
    }

    public void close(){
        if (opened != null) {
            opened.close();
            opened = null;
            openedNode = null;
        }
        if (this.getParent() != null) {
            this.getParent().removeChild(this);
        }
        onClose.run();
    }

    protected void initMenu() {
        if (!root.isLeaf()) {
            for (var child : root.getChildren()) {
                var container = new UIElement().layout(layout -> {
                    layout.flexDirection(FlexDirection.ROW);
                    layout.alignItems(AlignItems.CENTER);
                }).setOverflowVisible(true)
                        .style(style -> style.backgroundTexture(textureProvider.apply(child)))
                        .addChild(new UIElement().layout(layout -> {
                            layout.flex(1);
                        }).addChild(uiProvider.apply(child.getKey())))
                        .addEventListener(UIEvents.MOUSE_DOWN, e -> {
                            if (e.button == 0) {
                                if (child.isLeaf()) {
                                    if (onNodeClicked != null) {
                                        onNodeClicked.accept(child);
                                    }
                                    if (autoClose) {
                                        close();
                                    }
                                } else {
                                    openChildMenu(e.currentElement, child);
                                    e.stopPropagation();
                                }
                            }
                        }).addEventListener(UIEvents.MOUSE_ENTER, e -> {
                            e.currentElement.style(style -> style.backgroundTexture(hoverTextureProvider.apply(child)));
                            if (!child.isLeaf()) { // open a new menu
                                openChildMenu(e.currentElement, child);
                            } else {
                                if (opened != null) {
                                    opened.close();
                                    openedNode = null;
                                    focus();
                                }
                            }
                        }, true)
                        .addEventListener(UIEvents.MOUSE_LEAVE, e -> {
                            e.currentElement.style(style -> style.backgroundTexture(textureProvider.apply(child)));
                        }, true);
                if (child.isLeaf()) {
                    container.addClass("__menu_leaf-node__");
                } else {
                    container.addClass("__menu_branch-node__");
                    container.addChild(new UIElement().layout(layout -> {
                        layout.width(8);
                        layout.height(8);
                        layout.marginHorizontal(2);
                    }).style(style -> style.backgroundTexture(DynamicTexture.of(menuStyle::arrowIcon))));
                }
                nodeUIs.put(child, container);
                addChild(container);
            }
        }
    }

    protected void openChildMenu(UIElement anchor, ITreeNode<K, T> child) {
        if (opened != null) {
            if (openedNode == child) return;
            opened.close();
        }
        openedNode = child;
        opened = new Menu<>(child, uiProvider);
        opened.parentMenu = this;
        opened.rootPopup = true;
        opened.setAutoClose(autoClose);
        opened.getMenuStyle().copyFrom(menuStyle);
        opened.setTextureProvider(textureProvider);
        opened.setHoverTextureProvider(hoverTextureProvider);
        opened.getStyle().copyFrom(this.getStyle());
        opened.getStyle().zIndex(this.getStyle().zIndex() + 100);
        var mui = getModularUI();
        if (mui != null) {
            var root = mui.ui.rootElement;
            var worldPos = anchor.localToWorld(new Vector2f(
                    anchor.getPositionX() + anchor.getSizeWidth(),
                    anchor.getPositionY()
            ));
            var pos = root.worldToLocalLayoutOffset(worldPos);
            opened.getLayout().left(pos.x);
            opened.getLayout().top(pos.y);
        } else {
            opened.rootPopup = false;
            opened.getLayout().alignSelf(AlignItems.FLEX_START);
            opened.getLayout().left(anchor.getSizeWidth());
        }
        opened.setOnNodeClicked(node -> {
            if (onNodeClicked != null) {
                onNodeClicked.accept(node);
            }
            if (autoClose){
                close();
            }
        });
        if (mui != null) {
            mui.ui.rootElement.addChild(opened);
        } else {
            anchor.addChild(opened);
        }
    }

}
