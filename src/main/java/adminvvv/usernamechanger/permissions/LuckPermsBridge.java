package adminvvv.usernamechanger.permissions;

import adminvvv.usernamechanger.Usernamechanger;
import java.util.UUID;
import net.luckperms.api.LuckPermsProvider;

/** Loaded only when the optional LuckPerms mod is installed. */
public final class LuckPermsBridge {
    private static boolean warned;

    private LuckPermsBridge() {}

    public static Boolean check(UUID id, String node) {
        try {
            var loadedUser = LuckPermsProvider.get().getUserManager().getUser(id);
            if (loadedUser == null) return false;
            return switch (loadedUser.getCachedData().getPermissionData().checkPermission(node)) {
                case TRUE -> true;
                case FALSE -> false;
                case UNDEFINED -> null;
            };
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                Usernamechanger.LOGGER.error("LuckPerms permission check failed; denying access", e);
            }
            return false;
        }
    }

}
