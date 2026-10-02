package com.example.modbus.demo;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class DemoClient {
    private static int nextTransactionId = 1;

    private DemoClient() {}

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 11502;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            socket.setTcpNoDelay(true);
            OutputStream output = socket.getOutputStream();
            DataInputStream input = new DataInputStream(socket.getInputStream());

            System.out.println("connected to " + host + ":" + port);

            byte[] readCoils = frame(1, new byte[]{0x01, 0x00, 0x00, 0x00, 0x08});
            byte[] readRegisters = frame(1, new byte[]{0x03, 0x00, 0x00, 0x00, 0x04});
            byte[] writeCoil = frame(1, new byte[]{0x05, 0x00, 0x01, (byte) 0xFF, 0x00});
            byte[] writeRegisters = frame(1, new byte[]{
                    0x10, 0x00, 0x01, 0x00, 0x02, 0x04,
                    0x03, (byte) 0xE8, 0x07, (byte) 0xD0
            });
            byte[] unknownUnit = frame(9, new byte[]{0x03, 0x00, 0x00, 0x00, 0x01});
            byte[] unknownFunction = frame(1, new byte[]{0x08, 0x00, 0x00, 0x00, 0x01});
            byte[] missingAddress = frame(1, new byte[]{0x03, 0x60, 0x00, 0x00, 0x01});
            byte[] illegalQuantity = frame(1, new byte[]{0x03, 0x00, 0x00, 0x00, 0x00});
            byte[] continueRead = frame(2, new byte[]{0x03, 0x03, (byte) 0xE8, 0x00, 0x03});

            System.out.println("-- read 8 coils from unit 1 @0 --");
            exchange(output, input, readCoils);
            System.out.println("-- read 4 registers from unit 1 @0 --");
            exchange(output, input, readRegisters);
            System.out.println("-- force unit 1 coil @1 ON --");
            exchange(output, input, writeCoil);
            System.out.println("-- read coils after write --");
            exchange(output, input, readCoils);
            System.out.println("-- write unit 1 registers @1 = 1000,2000 --");
            exchange(output, input, writeRegisters);
            System.out.println("-- read registers after write --");
            exchange(output, input, readRegisters);
            System.out.println("-- unknown unit 9, expect exception 0B --");
            exchange(output, input, unknownUnit);
            System.out.println("-- unknown function 8, expect exception 01 --");
            exchange(output, input, unknownFunction);
            System.out.println("-- unconfigured register 24576, expect exception 02 --");
            exchange(output, input, missingAddress);
            System.out.println("-- illegal quantity 0, expect exception 03 --");
            exchange(output, input, illegalQuantity);
            System.out.println("-- after errors: read 3 registers from unit 2 @1000 --");
            exchange(output, input, continueRead);
        }
    }

    private static void exchange(OutputStream output, DataInputStream input, byte[] request) throws Exception {
        output.write(request);
        output.flush();

        int transactionId = input.readUnsignedShort();
        int protocolId = input.readUnsignedShort();
        int length = input.readUnsignedShort();
        byte[] rest = input.readNBytes(length);
        byte[] full = new byte[6 + length];
        full[0] = (byte) (transactionId >> 8);
        full[1] = (byte) transactionId;
        full[2] = (byte) (protocolId >> 8);
        full[3] = (byte) protocolId;
        full[4] = (byte) (length >> 8);
        full[5] = (byte) length;
        System.arraycopy(rest, 0, full, 6, length);
        System.out.println(hex(full));
    }

    private static byte[] frame(int unitId, byte[] pdu) {
        int transactionId = nextTransactionId++;
        byte[] frame = new byte[7 + pdu.length];
        frame[0] = (byte) (transactionId >> 8);
        frame[1] = (byte) transactionId;
        frame[2] = 0;
        frame[3] = 0;
        frame[4] = (byte) ((pdu.length + 1) >> 8);
        frame[5] = (byte) (pdu.length + 1);
        frame[6] = (byte) unitId;
        System.arraycopy(pdu, 0, frame, 7, pdu.length);
        return frame;
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte value : bytes) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(String.format("%02X", value));
        }
        return builder.toString();
    }
}
