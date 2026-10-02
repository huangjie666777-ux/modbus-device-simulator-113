package com.example.modbus.server;

import com.example.modbus.config.ServerConfig;
import com.example.modbus.config.SimulatorConfig;
import com.example.modbus.device.DeviceRegistry;
import com.example.modbus.protocol.ModbusFrameDecoder;
import com.example.modbus.protocol.ModbusRequestHandler;
import com.example.modbus.protocol.ModbusResponseEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

public final class ModbusTcpServer implements AutoCloseable {
    private final SimulatorConfig config;
    private final DeviceRegistry registry;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public ModbusTcpServer(SimulatorConfig config) {
        this.config = config;
        this.registry = new DeviceRegistry(config);
    }

    public void start() throws InterruptedException {
        ServerConfig serverConfig = config.getServer();
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        AtomicInteger activeConnections = new AtomicInteger();

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline()
                                .addLast(new ConnectionLimitHandler(activeConnections, serverConfig.getMaxConnections()))
                                .addLast(new ModbusFrameDecoder(serverConfig.getMaxFrameLength()))
                                .addLast(new ModbusResponseEncoder())
                                .addLast(new ModbusRequestHandler(registry));
                    }
                });

        serverChannel = bootstrap.bind(new InetSocketAddress(serverConfig.getHost(), serverConfig.getPort()))
                .sync().channel();
    }

    public InetSocketAddress boundAddress() {
        return (InetSocketAddress) serverChannel.localAddress();
    }

    @Override
    public void close() throws InterruptedException {
        try {
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        } finally {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully().sync();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully().sync();
            }
        }
    }
}
