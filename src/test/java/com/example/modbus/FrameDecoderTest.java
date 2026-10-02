package com.example.modbus;

import com.example.modbus.codec.ModbusFrameDecoder;
import com.example.modbus.codec.ModbusRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FrameDecoderTest {

    private static byte[] frame(int tx, int unit, int... pduInts) {
        byte[] pdu = new byte[pduInts.length];
        for (int i = 0; i < pdu.length; i++) pdu[i] = (byte) pduInts[i];
        byte[] f = new byte[7 + pdu.length];
        f[0] = (byte) (tx >> 8);
        f[1] = (byte) tx;
        f[4] = (byte) ((1 + pdu.length) >> 8);
        f[5] = (byte) (1 + pdu.length);
        f[6] = (byte) unit;
        System.arraycopy(pdu, 0, f, 7, pdu.length);
        return f;
    }

    @Test
    void halfPacketIsReassembled() {
        EmbeddedChannel ch = new EmbeddedChannel(new ModbusFrameDecoder());
        byte[] f = frame(1, 1, 0x03, 0, 0, 0, 4);
        ch.writeInbound(Unpooled.wrappedBuffer(f, 0, 5));
        assertNull(ch.readInbound());
        ch.writeInbound(Unpooled.wrappedBuffer(f, 5, f.length - 5));
        ModbusRequest req = ch.readInbound();
        assertNotNull(req);
        assertEquals(1, req.transactionId());
        assertEquals(3, req.functionCode());
        assertNull(ch.readInbound());
    }

    @Test
    void stickyPacketsAreSplit() {
        EmbeddedChannel ch = new EmbeddedChannel(new ModbusFrameDecoder());
        byte[] fa = frame(1, 1, 0x01, 0, 0, 0, 8);
        byte[] fb = frame(2, 2, 0x03, 0, 0, 0, 2);
        byte[] two = new byte[fa.length + fb.length];
        System.arraycopy(fa, 0, two, 0, fa.length);
        System.arraycopy(fb, 0, two, fa.length, fb.length);
        ch.writeInbound(Unpooled.wrappedBuffer(two));
        ModbusRequest a = ch.readInbound();
        ModbusRequest b = ch.readInbound();
        assertEquals(1, a.transactionId());
        assertEquals(2, b.transactionId());
        assertEquals(2, b.unitId());
        assertNull(ch.readInbound());
    }

    @Test
    void illegalLengthClosesConnection() {
        EmbeddedChannel ch = new EmbeddedChannel(new ModbusFrameDecoder());
        ByteBuf bad = Unpooled.buffer();
        bad.writeShort(1).writeShort(0).writeShort(60000).writeByte(1); // absurd length
        ch.writeInbound(bad);
        assertFalse(ch.isActive());
        assertNull(ch.readInbound());
    }

    @Test
    void nonZeroProtocolIdClosesConnection() {
        EmbeddedChannel ch = new EmbeddedChannel(new ModbusFrameDecoder());
        ByteBuf bad = Unpooled.buffer();
        bad.writeShort(1).writeShort(1).writeShort(2).writeByte(1).writeByte(0x03);
        ch.writeInbound(bad);
        assertFalse(ch.isActive());
    }
}
