package com.example.modbus.config;

public final class ServerConfig {
    private String host = "127.0.0.1";
    private int port = 11502;
    private int maxConnections = 16;
    private int maxFrameLength = 260;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public int getMaxConnections() { return maxConnections; }
    public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }

    public int getMaxFrameLength() { return maxFrameLength; }
    public void setMaxFrameLength(int maxFrameLength) { this.maxFrameLength = maxFrameLength; }
}
