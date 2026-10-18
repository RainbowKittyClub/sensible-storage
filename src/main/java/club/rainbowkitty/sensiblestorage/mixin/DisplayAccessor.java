package club.rainbowkitty.sensiblestorage.mixin;

import com.mojang.math.Transformation;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;

/** Reads what a display entity draws with, all of it private to {@link Display}. */
@Mixin(Display.class)
public interface DisplayAccessor {
    /** The display's transformation, from its synched data. */
    @Invoker("createTransformation")
    static Transformation sensiblestorage$transformation(SynchedEntityData entityData) {
        throw new AssertionError();
    }

    /** Ticks its transformation eases over. */
    @Invoker("getTransformationInterpolationDuration")
    int sensiblestorage$interpolationDuration();

    /** Its brightness override, if any. */
    @Invoker("getBrightnessOverride")
    @Nullable Brightness sensiblestorage$brightness();

    /** How it faces the camera. */
    @Invoker("getBillboardConstraints")
    Display.BillboardConstraints sensiblestorage$billboard();

    /** Its view range, as a factor of the entity distance. */
    @Invoker("getViewRange")
    float sensiblestorage$viewRange();
}
