package io.papermc.paper.network;

import io.netty.channel.Channel;

/**
 * Compile-only stand-in for the class of the same name in the Paper server jar (not in paper-api): signatures only,
 * so the plugin builds without a server jar. Never packaged; at runtime the server's own class is used.
 */
@FunctionalInterface
public interface ChannelInitializeListener {

    void afterInitChannel(Channel channel);
}
