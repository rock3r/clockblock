package dev.sebastiano.clockblocker.opus.widget.rc;

import androidx.annotation.NonNull;
import androidx.compose.remote.creation.compose.capture.RemoteComposeCreationState;
import androidx.compose.remote.creation.compose.state.BaseRemoteStateCacheKey;
import androidx.compose.remote.creation.compose.state.RemoteColor;
import kotlin.jvm.functions.Function1;

/**
 * Themed colours for {@code RestrictedRemoteCompose.kt}'s {@code themedColor}. In Java because alpha20 keeps what it
 * needs Kotlin-{@code internal}: {@code RemoteColor}'s writer-callback constructor and the cache key types. Kotlin
 * refuses to call them; Java sees plain public bytecode. Drop this once compose-remote has a public themed colour.
 */
@SuppressWarnings("RestrictedApiAndroidX")
final class ThemedRemoteColors {
    private ThemedRemoteColors() {
    }

    /** No system colour index: the player keeps the light / dark fallback values. */
    private static final short NO_SYSTEM_COLOR = -1;

    /**
     * A colour whose id the player resolves to {@code light} or {@code dark} by its theme (a {@code ColorTheme}
     * operation in colour group {@code group}). One operation per pair and document: the creation state caches the
     * id by key.
     */
    @NonNull
    static RemoteColor create(@NonNull String group, int light, int dark) {
        Function1<RemoteComposeCreationState, Integer> write = state ->
                (int) state.getDocument().addThemedColor(group, NO_SYSTEM_COLOR, NO_SYSTEM_COLOR, light, dark);
        return new RemoteColor(new Key(group, light, dark), write);
    }

    private static final class Key extends BaseRemoteStateCacheKey {
        private final String group;
        private final int light;
        private final int dark;

        Key(String group, int light, int dark) {
            this.group = group;
            this.light = light;
            this.dark = dark;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return key.light == light && key.dark == dark && key.group.equals(group);
        }

        @Override
        protected int hashCodeImpl() {
            return (group.hashCode() * 31 + light) * 31 + dark;
        }

        @NonNull
        @Override
        public String toDebugString() {
            return "ThemedColor(" + group + ", light=" + Integer.toHexString(light) + ", dark=" + Integer.toHexString(dark) + ")";
        }
    }
}
