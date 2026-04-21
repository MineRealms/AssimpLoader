package cn.minerealms.iic.turrets.client.gui;

import cn.minerealms.iic.IntegratedIndustrialCraft;
import cn.minerealms.iic.turrets.common.block_entity.FlameThrowerTurretBlockEntity;
import cn.minerealms.iic.turrets.common.packet.MekanismTurretsPacketHandler;
import cn.minerealms.iic.turrets.common.packet.ModifyTurretTargetPacket;
import mekanism.api.math.FloatingLong;
import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.button.ToggleButton;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.common.MekanismLang;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.inventory.warning.WarningTracker;
import mekanism.common.util.text.EnergyDisplay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 火焰喷射器炮塔GUI
 */
public class FlameThrowerTurretScreen extends GuiMekanismTile<FlameThrowerTurretBlockEntity, MekanismTileContainer<FlameThrowerTurretBlockEntity>> {

    private static final ResourceLocation TARGET_HOSTILE_OFF = new ResourceLocation(IntegratedIndustrialCraft.MODID, "textures/gui/target_hostile_off.png");
    private static final ResourceLocation TARGET_HOSTILE_ON = new ResourceLocation(IntegratedIndustrialCraft.MODID, "textures/gui/target_hostile_on.png");
    private static final ResourceLocation TARGET_PASSIVE_OFF = new ResourceLocation(IntegratedIndustrialCraft.MODID, "textures/gui/target_passive_off.png");
    private static final ResourceLocation TARGET_PASSIVE_ON = new ResourceLocation(IntegratedIndustrialCraft.MODID, "textures/gui/target_passive_on.png");
    private static final ResourceLocation TARGET_PLAYER_OFF = new ResourceLocation(IntegratedIndustrialCraft.MODID, "textures/gui/target_players_off.png");
    private static final ResourceLocation TARGET_PLAYER_ON = new ResourceLocation(IntegratedIndustrialCraft.MODID, "textures/gui/target_players_on.png");
    private static final ResourceLocation TARGET_TRUSTED_OFF = new ResourceLocation(IntegratedIndustrialCraft.MODID, "textures/gui/target_trusted_off.png");
    private static final ResourceLocation TARGET_TRUSTED_ON = new ResourceLocation(IntegratedIndustrialCraft.MODID, "textures/gui/target_trusted_on.png");

    public FlameThrowerTurretScreen(MekanismTileContainer<FlameThrowerTurretBlockEntity> container, Inventory inv, Component title) {
        super(container, inv, title);
        dynamicSlots = true;
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiVerticalPowerBar(this, tile.getEnergyContainer(), 164, 15))
                .warning(WarningTracker.WarningType.NOT_ENOUGH_ENERGY, () -> {
                    MachineEnergyContainer<FlameThrowerTurretBlockEntity> energyContainer = tile.getEnergyContainer();
                    return energyContainer.getEnergyPerTick().greaterThan(energyContainer.getEnergy());
                });
        FloatingLong energyPerTick = tile.getEnergyContainer().getEnergyPerTick();
        addRenderableWidget(new GuiEnergyTab(this, () -> List.of(
                Component.translatable("gui.turret.energy_per_shot").append(EnergyDisplay.of(energyPerTick).getTextComponent()),
                MekanismLang.NEEDED.translate(EnergyDisplay.of(energyPerTick.subtract(tile.getEnergyContainer().getEnergy())))
        )));

        int i = 25;
        // 目标选择按钮 - 移除禁用逻辑，所有按钮都可点击
        addRenderableWidget(new ToggleButton(this, 40, 33, 20, 20, TARGET_HOSTILE_OFF, TARGET_HOSTILE_ON, tile::targetsHostile,
                () -> MekanismTurretsPacketHandler.sendToServer(new ModifyTurretTargetPacket(tile.getBlockPos(), (byte) 0, !tile.targetsHostile())),
                (element, guiGraphics, mouseX, mouseY) -> guiGraphics.renderComponentTooltip(font, List.of(Component.translatable("gui.turret.target_hostile")), mouseX, mouseY)
        ));
        addRenderableWidget(new ToggleButton(this, 40 + i, 33, 20, 20, TARGET_PASSIVE_OFF, TARGET_PASSIVE_ON, tile::targetsPassive,
                () -> MekanismTurretsPacketHandler.sendToServer(new ModifyTurretTargetPacket(tile.getBlockPos(), (byte) 1, !tile.targetsPassive())),
                (element, guiGraphics, mouseX, mouseY) -> guiGraphics.renderComponentTooltip(font, List.of(Component.translatable("gui.turret.target_passive")), mouseX, mouseY)
        ));
        addRenderableWidget(new ToggleButton(this, 40 + 2 * i, 33, 20, 20, TARGET_PLAYER_OFF, TARGET_PLAYER_ON, tile::targetsPlayers,
                () -> MekanismTurretsPacketHandler.sendToServer(new ModifyTurretTargetPacket(tile.getBlockPos(), (byte) 2, !tile.targetsPlayers())),
                (element, guiGraphics, mouseX, mouseY) -> guiGraphics.renderComponentTooltip(font, List.of(Component.translatable("gui.turret.target_players")), mouseX, mouseY)
        ));
        addRenderableWidget(new ToggleButton(this, 40 + 3 * i, 33, 20, 20, TARGET_TRUSTED_OFF, TARGET_TRUSTED_ON, tile::targetsTrusted,
                () -> MekanismTurretsPacketHandler.sendToServer(new ModifyTurretTargetPacket(tile.getBlockPos(), (byte) 3, !tile.targetsTrusted())),
                (element, guiGraphics, mouseX, mouseY) -> guiGraphics.renderComponentTooltip(font, List.of(Component.translatable("gui.turret.target_trusted")), mouseX, mouseY)
        ));

        // 攻击模式按钮（文本按钮）
        addRenderableWidget(new ModeToggleButton(this, 40, 60, 80, 18,
                () -> tile.isAreaMode() ? Component.translatable("gui.turret.mode_area") : Component.translatable("gui.turret.mode_precision"),
                () -> MekanismTurretsPacketHandler.sendToServer(new ModifyTurretTargetPacket(tile.getBlockPos(), (byte) 4, !tile.isAreaMode())),
                (element, guiGraphics, mouseX, mouseY) -> {
                    List<Component> tooltip = tile.isAreaMode() ?
                            List.of(Component.translatable("gui.turret.mode_area.desc")) :
                            List.of(Component.translatable("gui.turret.mode_precision.desc"));
                    guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
                }
        ));
    }

    @Override
    protected void drawForegroundText(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderTitleText(guiGraphics);
        drawString(guiGraphics, playerInventoryTitle, inventoryLabelX, inventoryLabelY, titleTextColor());
        super.drawForegroundText(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        renderBackground(guiGraphics);
    }
}
