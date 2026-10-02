# Modbus TCP 模拟后端

供上位机联调的 Modbus TCP 从站模拟器。Java 17 + Netty 4.1.118，状态仅存内存，重启后按 JSON 配置恢复初值。

## 构建与测试

```bash
mvn test          # 运行全部单元测试（配置校验 / 分帧 / 功能码）
mvn package       # 构建 target/modbus-device-simulator-1.0-SNAPSHOT.jar
```

## 启动服务

```bash
mvn compile exec:java -Dexec.mainClass=com.example.modbus.Main -Dexec.args="config.json"
# 或
java -cp "target/classes:$(cat target/cp.txt)" com.example.modbus.Main config.json
```

监听 `127.0.0.1:<port>`（端口须为 1024–65535 高端口，在配置中指定）。

## 演示客户端

服务启动后另开终端：

```bash
java -cp "target/classes:$(cat target/cp.txt)" com.example.modbus.DemoClient 127.0.0.1 15020
```

依次演示 FC1/3/5/6/15/16 读写、四类异常（01/02/03/0B），并在异常后继续正常读取。

## 配置格式（config.json，含双设备示例）

```json
{
  "port": 15020,
  "maxConnections": 16,
  "units": [
    { "unitId": 1,
      "coils":            { "start": 0, "values": [true, false, ...] },
      "holdingRegisters": { "start": 0, "values": [100, 200, ...] } },
    { "unitId": 2, "coils": {...}, "holdingRegisters": {...} }
  ]
}
```

- 地址从 0 计；线圈初值为布尔，寄存器初值 0–65535。
- 启动时拒绝：重复 unitId、越界区间（start+count > 65536）、非法初值、低端口。

## 协议范围（Modbus V1.1b3 over TCP）

- MBAP 分帧：按长度字段拆分半包/粘包；协议标识非 0 或长度非法（<2 或 >254）直接关闭连接，不预分配大缓冲；响应保留事务 ID 与 Unit ID。
- 功能码：1 读线圈、3 读保持寄存器、5 写单线圈（仅接受 FF00/0000）、6 写单寄存器、15 写多线圈、16 写多寄存器。
- 数量限制：FC1 ≤2000，FC3 ≤125，FC15 ≤1968，FC16 ≤123；线圈按最低位打包、末字节未用位清零；寄存器大端。
- 异常码：01 未知功能，02 未配置地址，03 非法数量/值/字节数，0B 未知 Unit。
- 批量写先整体校验再原子提交，失败不改变任何地址；同 Unit 各连接共享状态，并发读不会看到批量写的中间结果；不同 Unit 互不影响。
- 连接数受 `maxConnections` 限制，每连接接收缓冲 4KB；断连与服务关闭时释放全部网络资源。

## 代码结构

| 文件 | 职责 |
|---|---|
| `config/SimulatorConfig.java` | JSON 加载与校验 |
| `device/DeviceMemory.java` / `DeviceRegistry.java` | 单设备内存态 / Unit 注册表 |
| `codec/ModbusFrameDecoder.java` | MBAP 分帧（半包/粘包/非法帧） |
| `codec/ModbusResponseEncoder.java` | 响应编码 |
| `server/ModbusRequestHandler.java` | 功能码分发与异常 |
| `server/ConnectionLimitHandler.java` | 连接数限制 |
| `server/ModbusServer.java` | Netty 引导与资源释放 |
| `Main.java` / `DemoClient.java` | 入口 / TCP 演示客户端 |
