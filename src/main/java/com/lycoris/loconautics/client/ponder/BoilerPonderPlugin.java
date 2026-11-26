package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.dashboard.DashboardBlocks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.List;

public class BoilerPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return LoconauticsConstants.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(fireboxIds())
                .addStoryBoard("boiler/barrel", FireboxScenes::fire)
                .addStoryBoard("boiler/firebox_row", FireboxScenes::row)
                .addStoryBoard("boiler/stoker", FireboxScenes::stoker)
                .addStoryBoard("boiler/boiler", BoilerSetupScenes::assembly)
                .addStoryBoard("boiler/barrel", BoilerScenes::ratio)
                .addStoryBoard("boiler/barrel", BoilerScenes::level)
                .addStoryBoard("boiler/barrel", BoilerScenes::failure);

        helper.forComponents(bodyIds())
                .addStoryBoard("boiler/boiler", BoilerSetupScenes::assembly)
                .addStoryBoard("boiler/water", BoilerScenes::water)
                .addStoryBoard("boiler/barrel", BoilerScenes::ratio)
                .addStoryBoard("boiler/barrel", BoilerScenes::level)
                .addStoryBoard("boiler/barrel", BoilerScenes::smokestack)
                .addStoryBoard("boiler/barrel", BoilerScenes::takeoff);

        helper.forComponents(BoilerBlocks.STEAM_TAKEOFF.getId())
                .addStoryBoard("boiler/barrel", BoilerScenes::takeoff)
                .addStoryBoard("boiler/barrel", BoilerScenes::pressure);

        helper.forComponents(BoilerBlocks.SMART_SAFETY_VALVE.getId(),
                        BoilerBlocks.VALVE_ITEM.getId())
                .addStoryBoard("boiler/barrel", BoilerScenes::pressure)
                .addStoryBoard("boiler/barrel", BoilerScenes::failure);

        helper.forComponents(ResourceLocation.fromNamespaceAndPath("create", "steam_whistle"))
                .addStoryBoard("boiler/barrel", BoilerScenes::whistle);

        helper.forComponents(BoilerBlocks.BOILER_BOGEY.getId())
                .addStoryBoard("boiler/bogey_wheels", BoilerBogeyScenes::wheels)
                .addStoryBoard("boiler/bogey_control", BoilerBogeyScenes::control)
                .addStoryBoard("boiler/bogey_boiler", BoilerBogeyScenes::boiler);

        helper.forComponents(BoilerBlocks.LOCOMOTIVE_GAUGE.getId())
                .addStoryBoard("boiler/barrel", BoilerScenes::gauge)
                .addStoryBoard("panel/panel", PanelScenes::parts);

        helper.forComponents(BoilerBlocks.GAUGE_BUTTON_ITEM.getId(), BoilerBlocks.GAUGE_LEVER_ITEM.getId(),
                        BoilerBlocks.GAUGE_SWITCH_ITEM.getId())
                .addStoryBoard("panel/panel", PanelScenes::parts)
                .addStoryBoard("panel/panel", PanelScenes::controls)
                .addStoryBoard("panel/panel", PanelScenes::gui)
                .addStoryBoard("panel/panel", PanelScenes::look)
                .addStoryBoard("panel/panel", PanelScenes::glass)
                .addStoryBoard("panel/panel_targets", PanelScenes::targets);

        helper.forComponents(DashboardBlocks.DASHBOARDS.values().stream().map(DeferredHolder::getId).toList())
                .addStoryBoard("dashboard/dashboard", DashboardScenes::desk);
    }

    private static List<ResourceLocation> fireboxIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        ids.add(BoilerBlocks.FIREBOX_STOCKER.getId());
        BoilerBlocks.DYED_FIREBOXES.values().forEach(byColor -> byColor.values().forEach(block -> ids.add(block.getId())));
        BoilerBlocks.EXTRA_FIREBOXES.values().forEach(byKind -> byKind.values().forEach(block -> ids.add(block.getId())));
        return ids;
    }

    private static List<ResourceLocation> bodyIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        ids.add(com.lycoris.loconautics.registry.LoconauticsRegistries.SMOKESTACK.getId());
        BoilerBlocks.DYED_BODIES.values().forEach(byColor -> byColor.values().forEach(block -> ids.add(block.getId())));
        BoilerBlocks.EXTRA_BODIES.values().forEach(byKind -> byKind.values().forEach(block -> ids.add(block.getId())));
        return ids;
    }
}
