package maxigregrze.cobblesafari.rotomphone;

import maxigregrze.cobblesafari.network.RotomPhoneConfigSyncPayload;

import java.util.ArrayList;
import java.util.List;

public class RotomPhoneClientCache {

    private static List<RotomPhoneConfigSyncPayload.AppData> cachedApps = new ArrayList<>();
    private static List<RotomPhoneConfigSyncPayload.SkinData> cachedSkins = new ArrayList<>();

    /**
     * Per-session GUI flag for the currently open phone: whether the selected skin's custom
     * wallpaper (screen background + theme accent) is shown. Set from {@code OpenRotomPhonePayload}
     * on open; read at the two render choke points ({@code getTintColor}, backdrop renderer).
     * Only one phone is open at a time, so a static holder is sufficient.
     */
    private static boolean currentWallpaperEnabled = true;

    private RotomPhoneClientCache() {}

    public static void applySyncData(RotomPhoneConfigSyncPayload payload) {
        cachedApps = new ArrayList<>(payload.apps());
        cachedSkins = new ArrayList<>(payload.skins());
    }

    public static List<RotomPhoneConfigSyncPayload.AppData> getCachedApps() {
        return cachedApps;
    }

    public static List<RotomPhoneConfigSyncPayload.SkinData> getCachedSkins() {
        return cachedSkins;
    }

    public static void setCurrentWallpaperEnabled(boolean enabled) {
        currentWallpaperEnabled = enabled;
    }

    public static boolean isCurrentWallpaperEnabled() {
        return currentWallpaperEnabled;
    }
}
