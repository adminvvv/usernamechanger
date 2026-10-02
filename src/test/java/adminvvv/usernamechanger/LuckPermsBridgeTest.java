package adminvvv.usernamechanger;

import adminvvv.usernamechanger.permissions.LuckPermsBridge;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.util.Tristate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LuckPermsBridgeTest {
    @Test void usesPublishedApiAndPreservesTristateDecisions() throws Exception {
        var answer = new AtomicReference<>(Tristate.TRUE);
        var requestedNode = new AtomicReference<String>();
        var permissions = (CachedPermissionData) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CachedPermissionData.class}, (proxy, method, args) -> {
                    assertEquals("checkPermission", method.getName());
                    requestedNode.set((String) args[0]);
                    return answer.get();
                });
        var cache = returns(CachedDataManager.class, "getPermissionData", permissions);
        var user = returns(User.class, "getCachedData", cache);
        var manager = returns(UserManager.class, "getUser", user);
        var api = returns(LuckPerms.class, "getUserManager", manager);
        var register = LuckPermsProvider.class.getDeclaredMethod("register", LuckPerms.class);
        var unregister = LuckPermsProvider.class.getDeclaredMethod("unregister");
        register.setAccessible(true);
        unregister.setAccessible(true);
        register.invoke(null, api);
        try {
            UUID id = UUID.randomUUID();
            assertEquals(true, LuckPermsBridge.check(id, "usernamechanger.change"));
            assertEquals("usernamechanger.change", requestedNode.get());
            answer.set(Tristate.FALSE);
            assertEquals(false, LuckPermsBridge.check(id, "usernamechanger.change"));
            answer.set(Tristate.UNDEFINED);
            assertNull(LuckPermsBridge.check(id, "usernamechanger.change"));
        } finally {
            unregister.invoke(null);
        }
    }

    private static <T> T returns(Class<T> type, String expectedMethod, Object result) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            assertEquals(expectedMethod, method.getName());
            return result;
        }));
    }
}
