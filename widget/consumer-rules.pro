# Reflective id host action for widget clicks (RestrictedRemoteCompose.kt#idHostAction).
-keep class androidx.compose.remote.creation.compose.action.HostAction {
    <init>(int, androidx.compose.remote.creation.compose.state.RemoteString, androidx.compose.remote.creation.compose.state.RemoteString);
}
