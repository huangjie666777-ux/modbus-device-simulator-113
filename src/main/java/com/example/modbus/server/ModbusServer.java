package com.example.modbus.server;

import com.example.modbus.codec.ModbusFrameDecoder;
import com.example.modbus.codec.ModbusResponseEncoder;
import com.example.modbus.config.SimulatorConfig;
import com.example.modbus.device.DeviceRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/** Netty TCP server bound to 127.0.0.1 on the configured high port. */
public final class ModbusServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ModbusServer.class);

    private final SimulatorConfig config;
    private final DeviceRegistry registry;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public ModbusServer(SimulatorConfig config) {
        this.config = config;
        this.registry = new DeviceRegistry(config);
    }

    public DeviceRegistry registry() {
        return registry;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        ConnectionLimitHandler limiter = new ConnectionLimitHandler(config.maxConnections);
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 16)
                .childOption(ChannelOption.SO_RCVBUF, 4096) // per-connection buffer cap
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(limiter)
                                .addLast(new ModbusFrameDecoder())
                                .addLast(new ModbusResponseEncoder())
                                .addLast(new ModbusRequestHandler(registry));
                    }
                });
        channel = bootstrap.bind(new InetSocketAddress("127.0.0.1", config.port)).sync().channel();
        log.info("Modbus simulator listening on 127.0.0.1:{} ({} units, max {} connections)",
                config.port, config.units.size(), config.maxConnections);
    }

    @Override
    public void close() {
        if (channel != null) channel.close().awaitUninterruptibly();
        if (workerGroup != null) workerGroup.shutdownGracefully().awaitUninterruptibly();
        if (bossGroup != null) bossGroup.shutdownGracefully().awaitUninterruptibly();
        log.info("Modbus simulator stopped, network resources released");
    }
}
