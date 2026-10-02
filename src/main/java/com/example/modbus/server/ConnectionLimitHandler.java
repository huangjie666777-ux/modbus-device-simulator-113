package com.example.modbus.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.concurrent.atomic.AtomicInteger;

/** Rejects connections beyond the configured limit and tracks releases. */
public final class ConnectionLimitHandler extends ChannelInboundHandlerAdapter {

    private final AtomicInteger active = new AtomicInteger();
    private final int maxConnections;

    public ConnectionLimitHandler(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (active.incrementAndGet() > maxConnections) {
            active.decrementAndGet();
            ctx.close();
            return;
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        active.decrementAndGet();
        super.channelInactive(ctx);
    }

    public int activeConnections() {
        return active.get();
    }
}
