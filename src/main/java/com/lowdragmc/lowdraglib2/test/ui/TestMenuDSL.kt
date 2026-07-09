package com.lowdragmc.lowdraglib2.test.ui

import com.lowdragmc.lowdraglib2.LDLib2
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot
import com.lowdragmc.lowdraglib2.gui.sync.rpc.rpcEvent
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.element
import com.lowdragmc.lowdraglib2.gui.ui.elements.*
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import com.lowdragmc.lowdraglib2.gui.ui.inventorySlots
import com.lowdragmc.lowdraglib2.gui.ui.layout.pct
import com.lowdragmc.lowdraglib2.gui.ui.layout.px
import com.lowdragmc.lowdraglib2.gui.ui.row
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister
import lombok.NoArgsConstructor
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.material.Fluids
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.templates.FluidTank
import net.minecraftforge.items.ItemStackHandler


@LDLRegister(name = "dsl_sync", registry = "ldlib2:menu_test")
@NoArgsConstructor
class TestMenuDSL : IMenuTest {
    private var bool = true
    private var string = "hello"
    private var number = 0.5f

    override fun createUI(player: Player): ModularUI {
        return try {
            createSyncUI(player)
        } catch (throwable: Throwable) {
            LDLib2.LOGGER.error("Failed to create LDLib2 dsl_sync test UI on {}", if (player.level().isClientSide) "client" else "server", throwable)
            createFallbackUI(player, throwable)
        }
    }

    private fun createSyncUI(player: Player): ModularUI {
        val itemHandler = ItemStackHandler(2)
        val fluidTank = FluidTank(2000)
        val root = element({
            cls = { +"panel_bg" }
        }) {
            label({ text("Data Between Screen and Menu") })
            row({
                layout = { gap { all(2.px) } }
            }) {
                itemSlot({ bind(itemHandler, 0) })
                itemSlot({ bind(ItemHandlerSlot(itemHandler, 1).setCanTake { false }) })
                fluidSlot({ bind(fluidTank, 0) })
            }
            element({
                layout = { gap { all(2.px) } }
            }) {
                switch { bind(::bool) }
                textField { bind(::string) }
                scrollerHorizontal({ layout = { width(100.pct) } }) { bind(::number) }
                label {
                    bindS2C({
                        Component.literal("s->c only: ")
                        .append(Component.literal(bool.toString()).withStyle(ChatFormatting.AQUA)).append(" ")
                        .append(Component.literal(string).withStyle(ChatFormatting.RED)).append(" ")
                        .append(Component.literal("%.2f".format(number)).withStyle(ChatFormatting.YELLOW))
                    })
                }
                button {
                    serverEvents {
                        UIEvents.MOUSE_DOWN += {
                            if (fluidTank.fluid.fluid === Fluids.WATER) {
                                fluidTank.fluid = FluidStack(Fluids.LAVA, 1000)
                            } else {
                                fluidTank.fluid = FluidStack(Fluids.WATER, 1000)
                            }
                        }
                    }
                    val rpcEvent = element.rpcEvent { clickValue: String -> string = clickValue }
                    events {
                        UIEvents.MOUSE_DOWN += {
                            rpcEvent.send("rpc")
                        }
                    }
                }
                inventorySlots()
            }
        }
        return ModularUI(UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MODERN)), player)
    }

    private fun createFallbackUI(player: Player, throwable: Throwable): ModularUI {
        val root = com.lowdragmc.lowdraglib2.gui.ui.UIElement()
        root.addChildren(
            Label().setText("dsl_sync failed to create UI"),
            Label().setText(throwable.javaClass.name),
            Label().setText(throwable.message ?: "no message")
        ).addClass("panel_bg")
        return ModularUI(UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MODERN)), player)
    }

}
