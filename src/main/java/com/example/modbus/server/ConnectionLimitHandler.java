package com.example.modbus.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.concurrent.atomic.AtomicInteger;

public final class ConnectionLimitHandler extends ChannelInboundHandlerAdapter {
    private final AtomicInteger activeConnections;
    private final int maxConnections;
    private boolean accepted;

    public ConnectionLimitHandler(AtomicInteger activeConnections, int maxConnections) {
        this.activeConnections = activeConnections;
        this.maxConnections = maxConnections;
    }

    @Override
    public void channelActive(ChannelHandlerContext context) throws Exception {
        int current = activeConnections.incrementAndGet();
        if (current > maxConnections) {
            activeConnections.decrementAndGet();
            context.close();
            return;
        }
        accepted = true;
        super.channelActive(context);
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        if (accepted) {
            activeConnections.decrementAndGet();
            accepted = false;
        }
        super.channelInactive(context);
    }
}
