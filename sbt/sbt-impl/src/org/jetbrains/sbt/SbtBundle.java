package org.jetbrains.sbt;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class SbtBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.SbtBundle";

    private static final SbtBundle INSTANCE = new SbtBundle();

    private SbtBundle() {
        super(BUNDLE);
    }

    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
