package com.lycoris.loconautics.client;

import com.lycoris.loconautics.client.boilerbogey.BoilerBogeyRenderer;
import com.lycoris.loconautics.client.boilerbogey.BoilerBogeyVisual;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class BoilerBogeyClient {

    private BoilerBogeyClient() {
    }

    public static BogeyStyle.SizeRenderer smallRenderer() {
        return new BogeyStyle.SizeRenderer(new BoilerBogeyRenderer(), BoilerBogeyVisual::new);
    }
}
