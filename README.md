# Modbus TCP 设备模拟后端

基于 Java 17、Netty 4.1.118.Final 和 Jackson 的内存态 Modbus TCP 模拟器，供上位机联调使用。项目只提供 TCP 后端和命令行演示客户端，不包含前端。

## 构建与测试

```bash
mvn test
mvn package
```

依赖使用项目固定的本地仓库参数（见 `.mvn/maven.config`），无需额外安装 Netty。

## 启动服务

```bash
mvn exec:java
```

或指定配置文件：

```bash
mvn exec:java -Dexec.args=config/modbus-simulator.json
```

默认监听 `127.0.0.1:11502`。示例配置包含 Unit 1 和 Unit 2 两台设备。

## TCP 演示客户端

服务启动后，另开终端执行：

```bash
java -cp target/classes:$(cat target/cp.txt) com.example.modbus.demo.DemoClient 127.0.0.1 11502
```

若没有 `target/cp.txt`，先生成依赖 classpath：

```bash
mvn dependency:build-classpath -Dmdep.outputFile=target/cp.txt
```

演示内容包括：读线圈、读保持寄存器、单线圈写、批量寄存器写、未知 Unit、未知功能码、未配置地址、非法数量，以及异常后继续正常读取。

## JSON 配置

示例见 `config/modbus-simulator.json`：

```json
{
  "server": {
    "host": "127.0.0.1",
    "port": 11502,
    "maxConnections": 8,
    "maxFrameLength": 260
  },
  "devices": [
    {
      "unitId": 1,
      "coilStartAddress": 0,
      "coils": [true, false, true],
      "holdingStartAddress": 0,
      "holdingRegisters": [100, 200, 300]
    }
  ]
}
```

校验规则：

- `unitId` 范围为 0..247，且不能重复。
- `coilStartAddress` 和 `holdingStartAddress` 范围为 0..65535。
- 每个地址区间必须非空，且起始地址加数组长度不能超过 65536。
- 线圈值必须为布尔值；寄存器值必须为 0..65535 的整数。
- 端口必须为 1024..65535；连接数和帧长有配置上限。

状态只保存在 JVM 内存中；进程重启后按 JSON 初值恢复。同一 Unit 的所有连接共享同一份内存，不同 Unit 相互独立。

## 协议范围

遵循 Modbus over TCP / V1.1b3 的 MBAP + PDU 形式：

| 功能码 | 功能 | 数量范围 |
| --- | --- | --- |
| `0x01` | 读线圈 | 1..2000 |
| `0x03` | 读保持寄存器 | 1..125 |
| `0x05` | 写单个线圈 | 值仅允许 `FF00` / `0000` |
| `0x06` | 写单个保持寄存器 | 1 个 |
| `0x0F` | 写多个线圈 | 1..1968 |
| `0x10` | 写多个保持寄存器 | 1..123 |

实现细节：

- MBAP 事务 ID 和 Unit ID 在响应中原样保留，协议标识固定为 0。
- 按 MBAP length 拆分半包和粘包；length 非法、协议标识错误或超过配置帧长会关闭连接。
- 解码器只在读到 length 后按声明长度等待，不预分配大缓冲。
- 线圈从最低位开始打包，最后一个字节未使用位清零。
- 寄存器采用 16 位大端编码。
- 批量写先校验完整地址、数量和字节数，再在设备锁内整体提交；失败不改变任何地址。
- 读操作与写操作使用同一设备监视器，读请求不会观察到一次批量写的部分结果。
- 单个请求异常只返回异常响应，不断开连接，不影响后续请求。

异常码：

| 异常码 | 场景 |
| --- | --- |
| `01` | 未知或不支持的功能码 |
| `02` | 请求地址或数量跨越未配置区间 |
| `03` | 非法数量、字节数、PDU 长度或单线圈值 |
| `0B` | Unit ID 未在配置中找到 |

## 代码结构

- `config`：JSON 模型和启动配置校验。
- `device`：Unit 注册表、设备内存和读写同步。
- `protocol`：MBAP 分帧、功能码处理、异常和响应编码。
- `server`：Netty 启动、连接数限制和资源释放。
- `demo`：无第三方依赖的 TCP 演示客户端。
