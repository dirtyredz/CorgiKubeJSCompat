package corgitaco.corgilib.core;

import com.mojang.serialization.Codec;
import corgitaco.corgilib.entity.condition.Condition;
import net.minecraft.core.Registry;
import java.util.function.Supplier;

/** Compile-only API from the pinned CorgiLib jar; never a runtime implementation. */
public class CorgiLibRegistry {
    public static final Supplier<Registry<Codec<? extends Condition>>> CONDITION = null;
}
