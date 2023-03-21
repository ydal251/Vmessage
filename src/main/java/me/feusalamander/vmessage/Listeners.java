package me.feusalamander.vmessage;

import com.google.common.annotations.Beta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.context.Context;
import net.luckperms.api.context.ContextManager;
import net.luckperms.api.model.user.User;

import java.net.ProtocolException;
import java.security.SignatureException;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.CompletionException;
@SuppressWarnings({"UnstableApiUsage", "deprecation"})
public final class Listeners {
    public static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();
    public static final MiniMessage mm = MiniMessage.miniMessage();
    private LuckPerms luckPermsAPI;
    private final Configuration configuration;
    private final ProxyServer proxyServer;

    Listeners(final ProxyServer proxyServer, final Configuration configuration) {
        if (proxyServer.getPluginManager().getPlugin("luckperms").isPresent()) {
            this.luckPermsAPI = LuckPermsProvider.get();
        }
        this.configuration = configuration;
        this.proxyServer = proxyServer;
    }

    @Subscribe
    private void onMessage(final PlayerChatEvent e) {
        if (!configuration.isMessageEnabled()) {
            return;
        }
        if(configuration.isAllEnabled()){
            e.setResult(PlayerChatEvent.ChatResult.denied());
        }
        message(e.getPlayer(), e.getMessage());
    }

    @Subscribe
    private void onLeave(final DisconnectEvent e) {
        if (!configuration.isLeaveEnabled()) {
            return;
        }
        final Player p = e.getPlayer();
        final Optional<ServerConnection> server = p.getCurrentServer();
        if (server.isEmpty()) {
            return;
        }
        String message = configuration.getLeaveFormat()
                .replace("#player#", p.getUsername())
                .replace("#oldserver#", server.get().getServerInfo().getName());
        if (luckPermsAPI != null) {
            message = luckperms(message, p);
        }
        if (configuration.isMinimessageEnabled()) {
            proxyServer.sendMessage(mm.deserialize(message.replace("§", "")));
        } else {
            proxyServer.sendMessage(SERIALIZER.deserialize(message));
        }

    }

    @Subscribe
    private void onChange(final ServerPostConnectEvent e) {
        if (!configuration.isChangeEnabled() && !configuration.isJoinEnabled()) {
            return;
        }
        final RegisteredServer pre = e.getPreviousServer();
        final Player p = e.getPlayer();
        final Optional<ServerConnection> serverConnection = e.getPlayer().getCurrentServer();
        if (pre != null&&serverConnection.isPresent()) {
            if (!configuration.isChangeEnabled()) {
                return;
            }
            final ServerConnection actual = serverConnection.get();
            String message = configuration.getChangeFormat()
                    .replace("#player#", p.getUsername())
                    .replace("#oldserver#", pre.getServerInfo().getName())
                    .replace("#server#", actual.getServerInfo().getName());
            if (luckPermsAPI != null) {
                message = luckperms(message, p);
            }
            if (configuration.isMinimessageEnabled()) {
                proxyServer.sendMessage(mm.deserialize(message.replace("§", "")));
            } else {
                proxyServer.sendMessage(SERIALIZER.deserialize(message));
            }
        } else if (serverConnection.isPresent()){
            if (!configuration.isJoinEnabled()) {
                return;
            }
            String message = configuration.getJoinFormat()
                    .replace("#player#", p.getUsername())
                    .replace("#server#", serverConnection.get().getServerInfo().getName());
            if (luckPermsAPI != null) {
                message = luckperms(message, p);
            }
            if (configuration.isMinimessageEnabled()) {
                proxyServer.sendMessage(mm.deserialize(message.replace("§", "")));
            } else {
                proxyServer.sendMessage(SERIALIZER.deserialize(message));
            }
        }
    }

    private String luckperms(String message, final Player p) {
        final CachedMetaData data = luckPermsAPI.getPlayerAdapter(Player.class).getMetaData(p);
        final String prefix = data.getPrefix();
        final String suffix = data.getSuffix();
        if (message.contains("#prefix#")&&prefix != null) {
            message = message.replace("#prefix#", prefix);
        }
        if (message.contains("#suffix#")&&suffix != null) {
            message = message.replace("#suffix#", suffix);
        }
        message = message.replace("#prefix#", "").replace("#suffix#", "");
        return message;
    }

    public void message(final Player p, final String m) {
        final boolean permission = p.hasPermission("vmessage.minimessage");
        String message = configuration.getMessageFormat()
                .replace("#player#", p.getUsername())
                .replace("#server#", p.getCurrentServer().orElseThrow().getServerInfo().getName());
        if (luckPermsAPI != null) {
            message = luckperms(message, p);
        }
        if(permission)message = message.replace("#message#", m);
        Component finalMessage;
        if (configuration.isMinimessageEnabled()) {
            finalMessage = mm.deserialize(message.replace("§", ""));
        } else {
            finalMessage = SERIALIZER.deserialize(message);
        }
        if(!permission)finalMessage = finalMessage.replaceText("#message#", Component.text(m));
        if(configuration.isAllEnabled()){
            proxyServer.sendMessage(finalMessage);
        }else {
            final Component FMessage = finalMessage;
            proxyServer.getAllServers().forEach(server -> {
                if (!Objects.equals(p.getCurrentServer().map(ServerConnection::getServerInfo).orElse(null), server.getServerInfo())) {
                    server.sendMessage(FMessage);
                }
            });
        }

    }
}
