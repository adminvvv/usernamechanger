package adminvvv.usernamechanger;

import com.mojang.authlib.GameProfile;
import adminvvv.usernamechanger.mixin.ChunkMapAccessor;
import adminvvv.usernamechanger.mixin.TrackedEntityAccessor;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.arguments.GameProfileArgument;
import com.mojang.brigadier.StringReader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.permissions.LevelBasedPermissionSet;

public class NicknameGameTests {
    public static UUID worldOwner;

    @GameTest(maxTicks = 100)
    public void nicknameLifecycle(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var service = Usernamechanger.service();
        var profile = new GameProfile(UUID.fromString("a33a278b-37bf-49b4-9eca-5eb4216b2311"), "NickTestAlice");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(server, helper.getLevel(), profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        var observerProfile = new GameProfile(UUID.fromString("635c5247-06e7-4dcb-a312-a3934fa8e17d"), "NickTestBob");
        var observerCookie = CommonListenerCookie.createInitial(observerProfile, false);
        var observer = new ServerPlayer(server, helper.getLevel(), observerProfile, observerCookie.clientInformation());
        var observerConnection = new Connection(PacketFlow.SERVERBOUND);
        var observerChannel = new EmbeddedChannel(observerConnection);
        server.getPlayerList().placeNewPlayer(observerConnection, observer, observerCookie);
        var tracked = (TrackedEntityAccessor) ((ChunkMapAccessor) player.level().getChunkSource().chunkMap)
                .usernamechanger$entities().get(player.getId());
        // Make this embedded connection a tracking viewer without waiting for client chunk acknowledgements.
        tracked.usernamechanger$viewers().add(observer.connection);
        var console = server.createCommandSourceStack();
        var commands = server.getCommands().getDispatcher();
        try {
            commands.execute("usernamereset NickTestAlice", console);
            var unprivileged = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.ALL);
            check(helper, !service.allowed(unprivileged, "change"), "Non-operators must be denied");
            check(helper, service.allowed(player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER), "change"),
                    "OP level 2 must be allowed");
            check(helper, service.change(unprivileged, "NickTestAlice", "Unauthorized") == 0, "Direct command must also deny access");
            for (String command : List.of("usernamechange", "usernamereset", "usernamechanger")) {
                check(helper, !commands.getRoot().getChild(command).canUse(unprivileged), "Command visible to non-operator: " + command);
            }
            var identity = new net.minecraft.server.players.NameAndId(profile);
            server.getPlayerList().op(identity, java.util.Optional.of(LevelBasedPermissionSet.MODERATOR), java.util.Optional.empty());
            try {
                for (String command : List.of("usernamechange", "usernamereset", "usernamechanger")) {
                    check(helper, commands.getRoot().getChild(command).canUse(unprivileged), "Command hidden from level-1 operator: " + command);
                }
                check(helper, commands.execute("usernamechange NickTestAlice OperatorNick", unprivileged) == 1, "Operator could not execute command");
            } finally {
                server.getPlayerList().deop(identity);
            }
            worldOwner = profile.id();
            try {
                check(helper, !server.getWorldData().isAllowCommands(), "Owner test must disable cheats");
                check(helper, !server.getPlayerList().isOp(identity), "Owner test must run without OP or cheats");
                for (String command : List.of("usernamechange", "usernamereset", "usernamechanger")) {
                    check(helper, commands.getRoot().getChild(command).canUse(unprivileged), "Command hidden from world owner: " + command);
                }
                check(helper, commands.execute("usernamechange NickTestAlice OwnerNick", unprivileged) == 1, "World owner could not execute command");
                check(helper, !service.allowed(observer.createCommandSourceStack().withPermission(LevelBasedPermissionSet.ALL), "change"),
                        "World ownership must not grant access to a guest");
            } finally {
                worldOwner = null;
            }
            check(helper, commands.execute("usernamechange NickTestAlice Comet", console) == 1, "Set command failed");
            check(helper, player.getGameProfile().equals(profile), "Account profile changed");
            check(helper, player.getScoreboardName().equals("NickTestAlice"), "Scoreboard identity changed");
            check(helper, player.getDisplayName().getString().equals("Comet"), "Chat display name incorrect");
            check(helper, player.getTabListDisplayName() == null, "Keep vanilla's live team formatting for tab names");
            check(helper, server.getPlayerList().getPlayerByName("cOmEt") == player, "Alias target failed");
            check(helper, server.getPlayerList().getPlayerByName("NickTestAlice") == player, "Account target failed");
            var selector = new EntitySelectorParser(new StringReader("Comet"), true).parse();
            check(helper, selector.findSinglePlayer(console) == player, "Vanilla entity argument rejected alias");
            var profileTarget = GameProfileArgument.gameProfile().parse(new StringReader("Comet")).getNames(console).iterator().next();
            check(helper, profileTarget.id().equals(profile.id()), "Profile commands resolved alias to the wrong account");
            check(helper, console.getOnlinePlayerNames().contains("Comet"), "Autocomplete missing nickname");
            check(helper, !console.getOnlinePlayerNames().contains("NickTestAlice"), "Autocomplete leaked renamed account");
            var suggestions = commands.getCompletionSuggestions(commands.parse("usernamechange ", console)).join()
                    .getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).toList();
            check(helper, suggestions.contains("Comet") && !suggestions.contains("NickTestAlice"), "Command suggestions must use current names");
            boolean removed = false;
            boolean spawned = false;
            Object outbound;
            while ((outbound = observerChannel.readOutbound()) != null) {
                if (outbound instanceof ClientboundBundlePacket bundle) {
                    for (var subPacket : bundle.subPackets()) {
                        if (subPacket instanceof ClientboundRemoveEntitiesPacket) removed = true;
                        if (subPacket instanceof ClientboundAddEntityPacket add) spawned |= add.getId() == player.getId();
                    }
                }
            }
            check(helper, removed && spawned, "Tracking viewer did not receive a nametag refresh");
            var packet = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player));
            var entry = packet.entries().getFirst();
            check(helper, entry.profile().name().equals("Comet"), "Nametag profile was not renamed");
            check(helper, entry.profile().id().equals(profile.id()), "Client UUID changed");
            check(helper, entry.profile().properties().equals(profile.properties()), "Skin properties changed");
            var team = server.getScoreboard().addPlayerTeam("nickname_test");
            server.getScoreboard().addPlayerToTeam("NickTestAlice", team);
            var teamPacket = ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true);
            check(helper, teamPacket.getPlayers().contains("Comet"), "Client team membership missing nickname");
            commands.execute("usernamechange Comet Meteor", console);
            check(helper, player.getDisplayName().getString().equals("Meteor"), "Renaming alias failed");
            check(helper, server.getPlayerList().getPlayerByName("Comet") == null, "Old alias still resolves");
            server.getPlayerList().remove(player);
            var rejoined = new ServerPlayer(server, helper.getLevel(), profile, cookie.clientInformation());
            var secondConnection = new Connection(PacketFlow.SERVERBOUND);
            var secondChannel = new EmbeddedChannel(secondConnection);
            server.getPlayerList().placeNewPlayer(secondConnection, rejoined, cookie);
            try {
                check(helper, rejoined.getDisplayName().getString().equals("Meteor"), "Nickname lost on reconnect");
                check(helper, commands.execute("usernamereset Meteor", console) == 1, "Reset failed");
                check(helper, rejoined.getDisplayName().getString().equals("NickTestAlice"), "Reset display incorrect");
                check(helper, service.nickname(profile.id()) == null, "Reset did not remove stored nickname");
            } finally {
                server.getPlayerList().remove(rejoined);
                secondChannel.finishAndReleaseAll();
                server.getScoreboard().removePlayerTeam(team);
            }
        } finally {
            if (server.getPlayerList().getPlayer(profile.id()) == player) server.getPlayerList().remove(player);
            server.getPlayerList().remove(observer);
            observerChannel.finishAndReleaseAll();
            channel.finishAndReleaseAll();
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void bedrockNamesAndOfflineSuggestions(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var service = Usernamechanger.service();
        var profile = new GameProfile(UUID.fromString("00000000-0000-0000-0009-000000000123"), ".Bedrock Player123");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(server, helper.getLevel(), profile, cookie.clientInformation());
        var channel = new EmbeddedChannel(new Connection(PacketFlow.SERVERBOUND));
        var connection = channel.pipeline().get(Connection.class);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        var console = server.createCommandSourceStack();
        var commands = server.getCommands().getDispatcher();
        try {
            commands.execute("usernamereset " + profile.id(), console);
            var suggestions = commands.getCompletionSuggestions(commands.parse("usernamechange ", console)).join()
                    .getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).toList();
            check(helper, suggestions.contains("\".Bedrock Player123\""), "Bedrock names must be quoted in suggestions");
            check(helper, commands.execute("usernamechange \".Bedrock Player123\" BedrockNick", console) == 1, "Quoted Bedrock target failed");
            check(helper, player.getGameProfile().equals(profile), "Bedrock account identity changed");
            check(helper, server.getPlayerList().getPlayerByName("BedrockNick") == player, "Bedrock nickname target failed");
            var persisted = new adminvvv.usernamechanger.storage.NicknameStore(server.getWorldPath(
                    net.minecraft.world.level.storage.LevelResource.ROOT).resolve("usernamechanger.json"));
            check(helper, "BedrockNick".equals(persisted.nickname(profile.id())), "Bedrock nickname failed to reload");
            server.getPlayerList().remove(player);
            check(helper, !service.knownNames().contains("BedrockNick") && !service.knownNames().contains(profile.name()),
                    "Offline renamed player must not appear in suggestions");
            check(helper, commands.execute("usernamereset BedrockNick", console) == 1, "Hidden offline nickname must still resolve");
            check(helper, service.knownNames().contains(profile.name()), "Offline unnamed account must appear");
            check(helper, commands.execute("usernamechange \".Bedrock Player123\" BedrockNick", console) == 1,
                    "Offline Bedrock account could not be renamed");
            commands.execute("usernamereset " + profile.id(), console);
        } finally {
            if (server.getPlayerList().getPlayer(profile.id()) == player) server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, Component.literal(message));
    }

    @GameTest(maxTicks = 100)
    public void reloadImportsCachedOfflinePlayers(GameTestHelper helper) throws Exception {
        var cache = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("usercache.json");
        byte[] original = java.nio.file.Files.exists(cache) ? java.nio.file.Files.readAllBytes(cache) : null;
        UUID id = UUID.randomUUID();
        String name = "Cache" + id.toString().substring(0, 8);
        var server = helper.getLevel().getServer();
        var console = server.createCommandSourceStack();
        var commands = server.getCommands().getDispatcher();
        try {
            java.nio.file.Files.writeString(cache, "[{\"uuid\":\"" + id + "\",\"name\":\"" + name + "\"}]");
            check(helper, commands.execute("usernamechanger reload", console) == 1, "Cache reload failed");
            check(helper, Usernamechanger.service().knownNames().contains(name), "Cached offline player missing from suggestions");
            check(helper, commands.execute("usernamechange " + name + " CacheNickname", console) == 1,
                    "Imported offline player could not be renamed");
            commands.execute("usernamechanger reload", console);
            check(helper, "CacheNickname".equals(Usernamechanger.service().nickname(id)), "Reload overwrote imported player's nickname");
            check(helper, !Usernamechanger.service().knownNames().contains(name), "Renamed offline account still suggested");
            commands.execute("usernamereset " + id, console);
        } finally {
            if (original == null) java.nio.file.Files.deleteIfExists(cache);
            else java.nio.file.Files.write(cache, original);
        }
        helper.succeed();
    }
}
