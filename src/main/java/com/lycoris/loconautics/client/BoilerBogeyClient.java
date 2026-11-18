package com.lycoris.loconautics.client;

import com.simibubi.create.content.trains.bogey.BogeyStyle;
import com.simibubi.create.content.trains.bogey.StandardBogeyRenderer;
import com.simibubi.create.content.trains.bogey.StandardBogeyVisual;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class BoilerBogeyClient {

    private BoilerBogeyClient() {
    }

    // stand in, the steam bogey draws like creates small bogey until it has its own model
    public static BogeyStyle.SizeRenderer smallRenderer() {
        return new BogeyStyle.SizeRenderer(new StandardBogeyRenderer.Small(), StandardBogeyVisual.Small::new);
    }
}
