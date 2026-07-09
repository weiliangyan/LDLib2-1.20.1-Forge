package com.lowdragmc.lowdraglib2.integration.kjs;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.style.values.TextureValue;
import com.lowdragmc.lowdraglib2.math.Position;
import com.lowdragmc.lowdraglib2.math.Size;
import com.lowdragmc.lowdraglib2.utils.ReflectionUtils;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import com.lowdragmc.lowdraglib2.integration.kjs.ui.UIEvents;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ClassFilter;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.util.wrap.TypeWrappers;
import org.joml.Vector3f;

/**
 * @author KilaBash
 * @date 2023/3/26
 * @implNote GregTechKubeJSPlugin
 */
public class LDLibKubeJSPlugin extends KubeJSPlugin {

    @Override
    public void registerEvents() {
        UIEvents.INSTANCE.register();
    }

    @Override
    public void registerClasses(ScriptType type, ClassFilter filter) {
        filter.allow("com.lowdragmc.lowdraglib2");
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        // LDLib2 Auto Bindings
        ReflectionUtils.findAnnotationClasses(KJSBindings.class, data -> {
            var isClientOnly = (boolean) data.getOrDefault("clientOnly", false);
            if (isClientOnly && !LDLib2.isClient()) return false;
            var modId = data.getOrDefault("modId", "").toString();
            if (modId.isEmpty()) return true;
            return LDLib2.isModLoaded(modId);
        }, clazz -> {
            var annotation = clazz.getAnnotation(KJSBindings.class);
            var bindingName = annotation.value();
            if (bindingName.isEmpty()) bindingName = clazz.getSimpleName();
            event.add(bindingName, clazz);
        }, () -> {});

        ReflectionUtils.findAnnotationStaticField(KJSBindings.class, data -> {
            var modId = data.getOrDefault("modId", "").toString();
            if (modId.isEmpty()) return true;
            return LDLib2.isModLoaded(modId);
        }, (field, o) -> {
            var annotation = field.getAnnotation(KJSBindings.class);
            var bindingName = annotation.value();
            if (bindingName.isEmpty()) bindingName = field.getName();

            event.add(bindingName, o);
        }, () -> {});

        // math
        event.add("Vector3f", Vector3f.class);
        event.add("GuiSize", Size.class);
        event.add("GuiPos", Position.class);
    }

    @Override
    public void registerTypeWrappers(ScriptType type, TypeWrappers registry) {
        super.registerTypeWrappers(type, registry);
        registry.register(IResourcePath.class, (Context cx, Object obj) -> {
            if (obj instanceof IResourcePath path) {
                return path;
            }
            return obj == null ? null : IResourcePath.parse(obj.toString());
        });
        registry.register(IGuiTexture.class, (Context cx, Object obj) -> {
            if (obj instanceof IGuiTexture texture) {
                return texture;
            }
            IGuiTexture result = obj == null ? null : TextureValue.parseTexture(obj.toString());
            return result == null ? IGuiTexture.EMPTY : result;
        });
    }
}
