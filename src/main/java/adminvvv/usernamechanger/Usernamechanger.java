package adminvvv.usernamechanger;

import adminvvv.usernamechanger.config.ModConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Usernamechanger implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("usernamechanger");
    private static volatile NicknameService service;

    public static NicknameService service() {
        return service;
    }

    @Override
    public void onInitialize() {
        var configPath = FabricLoader.getInstance().getConfigDir().resolve("usernamechanger.json");
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> NicknameCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                service = new NicknameService(server, configPath, ModConfig.load(configPath));
            } catch (Exception e) {
                throw new IllegalStateException("Cannot load Username Changer; existing files have been preserved", e);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> service = null);
    }
}
