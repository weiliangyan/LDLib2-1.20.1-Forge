package com.lowdragmc.lowdraglib2.client;

import com.lowdragmc.lowdraglib2.LDLib2Registries;
import com.lowdragmc.lowdraglib2.client.shader.LDLibShaders;
import com.lowdragmc.lowdraglib2.client.shader.management.ShaderManager;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.editor.UIEditor;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * @author KilaBash
 * @date 2023/2/9
 * @implNote ClientCommands
 */
@OnlyIn(Dist.CLIENT)
public class ClientCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> createLiteral(String command) {
        return Commands.literal(command);
    }

    public static List<LiteralArgumentBuilder<CommandSourceStack>> createClientCommands() {
        var commands = new ArrayList<LiteralArgumentBuilder<CommandSourceStack>>();
        commands.add(createLiteral("ldlib2_client").then(createLiteral("reload_shader")
                .executes(context -> {
                    LDLibShaders.reload();
                    ShaderManager.getInstance().reload();
                    return 1;
                }))
                .then(createLiteral("ui_editor")
                        .executes(context -> {
                            if (!openUIEditor()) {
                                context.getSource().sendFailure(Component.literal("LDLib2 UI editor can only be opened in-game"));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.literal("Opening LDLib2 UI editor"), false);
                            return 1;
                        })));
        if (LDLib2Registries.SCREEN_TESTS != null && !LDLib2Registries.SCREEN_TESTS.values().isEmpty()) {
            commands.add(createScreenTestCommands());
        }
        return commands;
    }

    public static boolean openUIEditor() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        var ui = new ModularUI(UI.of(EditorWindow.open(UIEditor.WINDOW_ID, UIEditor::new)))
                .shouldCloseOnEsc(false)
                .shouldCloseOnKeyInventory(false);
        minecraft.setScreen(new ModularUIScreen(ui, Component.literal("LDLib2 UI Editor")));
        return true;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createScreenTestCommands() {
        var builder = Commands.literal("ldlib2_screen_test");
        if (LDLib2Registries.SCREEN_TESTS == null) {
            return builder;
        }
        for (var uiTest : LDLib2Registries.SCREEN_TESTS) {
            builder = builder.then(createLiteral(uiTest.annotation().name())
                    .executes(context -> {
                        var test = uiTest.value().get();
                        var minecraft = Minecraft.getInstance();
                        var entityPlayer = minecraft.player;
                        if (entityPlayer == null) return 0;
                        var ui = test.createUI(entityPlayer);
                        minecraft.setScreen(new ModularUIScreen(ui, Component.empty()));
                        return 1;
                    }));
        }
        return builder;
    }
}
