package com.example.modbus;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

/**
 * Plain-socket demo client: exercises reads, writes, an exception case,
 * and continued reads after the exception on the same connection.
 */
public final class DemoClient implements AutoCloseable {

    private final Socket socket;
    private final OutputStream out;
    private final DataInputStream in;
    private int transaction = 0;

    private DemoClient(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = socket.getOutputStream();
        in = new DataInputStream(socket.getInputStream());
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    /** Sends MBAP + PDU and returns the response PDU (function code included). */
    byte[] request(int unitId, int... pduInts) throws IOException {
        byte[] pdu = new byte[pduInts.length];
        for (int i = 0; i < pdu.length; i++) pdu[i] = (byte) pduInts[i];
        int tx = ++transaction;
        byte[] frame = new byte[7 + pdu.length];
        frame[0] = (byte) (tx >> 8);
        frame[1] = (byte) tx;
        frame[4] = (byte) ((1 + pdu.length) >> 8);
        frame[5] = (byte) (1 + pdu.length);
        frame[6] = (byte) unitId;
        System.arraycopy(pdu, 0, frame, 7, pdu.length);
        out.write(frame);
        out.flush();

        int respTx = in.readUnsignedShort();
        in.readUnsignedShort(); // protocol id
        int length = in.readUnsignedShort();
        int respUnit = in.readUnsignedByte();
        byte[] respPdu = in.readNBytes(length - 1);
        if (respPdu.length < length - 1) throw new EOFException("truncated response");
        System.out.printf("tx=%d unit=%d -> respTx=%d respUnit=%d pdu=%s%n",
                tx, unitId, respTx, respUnit, hex(respPdu));
        return respPdu;
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X ", x));
        return sb.toString().trim();
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 15020;
        try (DemoClient client = new DemoClient(host, port)) {
            System.out.println("-- read 8 coils of unit 1 from address 0 (FC1)");
            client.request(1, 0x01, 0, 0, 0, 8);
            System.out.println("-- read 4 holding registers of unit 1 from address 0 (FC3)");
            client.request(1, 0x03, 0, 0, 0, 4);
            System.out.println("-- write single coil 2 = ON on unit 1 (FC5)");
            client.request(1, 0x05, 0, 2, (byte) 0xFF, 0x00);
            System.out.println("-- write single register 1 = 4660 on unit 1 (FC6)");
            client.request(1, 0x06, 0, 1, 0x12, 0x34);
            System.out.println("-- write 10 coils from address 0 on unit 2 (FC15)");
            client.request(2, 0x0F, 0, 0, 0, 10, 2, (byte) 0xCD, 0x01);
            System.out.println("-- write 2 registers from address 2 on unit 2 (FC16)");
            client.request(2, 0x10, 0, 2, 0, 2, 4, 0x00, 0x0A, 0x01, 0x02);
            System.out.println("-- read back unit 2 registers 0..3 (FC3)");
            client.request(2, 0x03, 0, 0, 0, 4);
            System.out.println("-- exception demo: unknown function 0x2B (expect 01)");
            client.request(1, 0x2B, 1, 1);
            System.out.println("-- exception demo: unconfigured address (expect 02)");
            client.request(1, 0x03, 0x10, 0, 0, 1);
            System.out.println("-- exception demo: illegal quantity (expect 03)");
            client.request(1, 0x01, 0, 0, 0, 0);
            System.out.println("-- exception demo: unknown unit 9 (expect 0B)");
            client.request(9, 0x03, 0, 0, 0, 1);
            System.out.println("-- connection still usable: read unit 1 coils again");
            byte[] pdu = client.request(1, 0x01, 0, 0, 0, 8);
            System.out.println("coil bits byte0=" + String.format("%02X", pdu[2]));
        }
        System.out.println("demo finished");
    }
}
