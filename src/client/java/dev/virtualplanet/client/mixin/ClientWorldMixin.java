package dev.virtualplanet.client.mixin;

import dev.virtualplanet.client.LocalSky;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives every player their own celestial phase.
 *
 * <h2>Why the sky angle is overridden instead of injected into</h2>
 *
 * <p>In 1.21.1 the sky angle lives in the interface default method
 * {@code net.minecraft.world.LunarWorldView#getSkyAngle(float)}:
 * {@code return this.getDimension().getSkyAngle(this.getLunarTime());} - it ignores the tick delta and
 * has no concrete implementation in {@code ClientWorld} or {@code World} to inject into. Adding the
 * method to {@code ClientWorld} therefore overrides the default for <em>every</em> call site at once:
 *
 * <ul>
 *   <li>the four calls inside {@code ClientWorld} itself - sky brightness (the light map), sky colour,
 *       cloud colour and fog colour, which is why those follow the viewer automatically, and</li>
 *   <li>the call in {@code WorldRenderer#renderSky}, which rotates the sun, the moon and the stars.</li>
 * </ul>
 *
 * <p>Because the whole celestial frame is rotated by this one angle, the mod's sun can never disagree
 * with a second, vanilla sun: there is only one sun, drawn where the viewer's longitude puts it.
 *
 * <p>The same mechanism provides the local moon: {@code LunarWorldView#getMoonSize()} is a default method
 * that calls {@code getMoonPhase()}, so overriding the phase also resizes the moon for the viewer.
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    /** The viewer's local celestial phase (added method; see the class documentation). */
    public float getSkyAngle(float tickDelta) {
        return LocalSky.skyAngle((ClientWorld) (Object) this, tickDelta);
    }

    /** The viewer's local moon phase (added method; {@code getMoonSize()} follows automatically). */
    public int getMoonPhase() {
        return LocalSky.moonPhase((ClientWorld) (Object) this);
    }

    /**
     * Optional refinement: when {@code localSkyBrightness} is disabled the light map must keep following
     * vanilla's own time, so the local angle is not allowed to leak into it.
     */
    @Inject(method = "getSkyBrightness(F)F", at = @At("HEAD"), cancellable = true)
    private void virtualplanet$vanillaSkyBrightness(float tickDelta, CallbackInfoReturnable<Float> info) {
        if (LocalSky.usesVanillaBrightness()) {
            info.setReturnValue(LocalSky.vanillaSkyBrightness((ClientWorld) (Object) this, tickDelta));
        }
    }
}
