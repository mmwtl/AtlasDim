package com.mmwtl.atlasdim;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Протокол gcp/1 демона ghudbridgelited (подмножество). Перенесено на Java из
 * Gcp.kt из ANHUD (https://github.com/g992/ANHUD);
 * в HELLO добавлена позиция окна (x, y) — поля по смещениям 4/8, сверено с
 * разбором HELLO в бинарнике (main+0x14a4…0x1560).
 *
 * Сообщение: 24-байтный little-endian заголовок (magic, type, flags, seq,
 * length, crc32(payload), crc32(header[0..19])) + payload.
 */
final class Gcp {
    static final int MAGIC = 0x31504347;
    static final int HEADER_SIZE = 24;

    static final int T_HELLO = 0x0001;
    static final int T_HELLO_ACK = 0x0002;
    static final int T_PING = 0x0003;
    static final int T_BYE = 0x0005;
    static final int T_ERROR = 0x0006;
    static final int T_FRAME = 0x0010;

    static final int FRAME_LAST = 0x0001;
    static final int ENC_RAW = 0;
    static final int ENC_RLE32 = 1;
    static final int MODE_FRAME = 1;
    static final int TRANSPARENCY_SOURCE_OVER = 3;

    static final int ERR_BUSY = 11;

    private static final int NAME_MAX_BYTES = 64;
    private static final int CLIENT_ID_MAX_BYTES = 23;

    private Gcp() {
    }

    static byte[] message(int type, int flags, int seq, byte[] payload) {
        ByteBuffer b = le(HEADER_SIZE + payload.length);
        b.putInt(MAGIC).putShort((short) type).putShort((short) flags)
                .putInt(seq).putInt(payload.length).putInt(crc(payload, 0, payload.length));
        b.putInt(crc(b.array(), 0, 20));
        b.put(payload);
        return b.array();
    }

    /** Разобранный заголовок; IllegalArgumentException, если это не gcp/1. */
    static final class Header {
        final int type;
        final int length;

        Header(byte[] h) {
            ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
            if (h.length < HEADER_SIZE || b.getInt(0) != MAGIC || b.getInt(20) != crc(h, 0, 20)) {
                throw new IllegalArgumentException("not a gcp/1 header");
            }
            type = b.getShort(4) & 0xffff;
            length = b.getInt(12);
        }
    }

    /** HELLO: окно w×h в точке (x, y) дисплея, прозрачность source-over. */
    static byte[] hello(String name, int x, int y, int w, int h, int fps) {
        byte[] nameBytes = utf8Prefix(name, NAME_MAX_BYTES);
        ByteBuffer b = le(56 + 2 + nameBytes.length);
        b.putShort((short) 1)                 // версия протокола
                .putShort((short) MODE_FRAME)
                .putInt(x).putInt(y)
                .putInt(w).putInt(h)
                .putInt(0)                    // z — демон игнорирует, задаётся --z
                .putShort((short) TRANSPARENCY_SOURCE_OVER)
                .putShort((short) fps)
                .putInt(0);
        b.put(utf8Prefix(name, CLIENT_ID_MAX_BYTES));
        b.position(56);
        b.putShort((short) nameBytes.length).put(nameBytes);
        return b.array();
    }

    /** FRAME на всё окно: RGBA w×h, RLE32 если короче сырого. */
    static byte[] frame(byte[] rgba, int w, int h) {
        byte[] rle = rle32(rgba);
        boolean useRle = rle.length < rgba.length;
        byte[] data = useRle ? rle : rgba;
        ByteBuffer b = le(24 + data.length);
        b.putShort((short) (useRle ? ENC_RLE32 : ENC_RAW)).putShort((short) 0)
                .putInt(0).putInt(0).putInt(w).putInt(h).putInt(rgba.length)
                .put(data);
        return b.array();
    }

    /** PackBits по 32-битным пикселям: 0x00..0x7F — n+1 литералов, 0x80..0xFF — повтор (n&0x7F)+1. */
    static byte[] rle32(byte[] px) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(px.length / 4);
        int n = px.length / 4;
        int i = 0;
        while (i < n) {
            int run = 1;
            while (i + run < n && run < 128 && samePixel(px, i, i + run)) {
                run++;
            }
            if (run >= 2) {
                out.write(0x80 | (run - 1));
                out.write(px, i * 4, 4);
                i += run;
                continue;
            }
            int start = i;
            int count = 0;
            while (i < n && count < 128 && !(i + 1 < n && samePixel(px, i, i + 1))) {
                count++;
                i++;
            }
            out.write(count - 1);
            out.write(px, start * 4, count * 4);
        }
        return out.toByteArray();
    }

    static int errorCode(byte[] payload) {
        return payload.length >= 4
                ? ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).getInt(0) : -1;
    }

    /** ERROR: u32 code, u32 errno, char[56] сообщение. */
    static String describeError(byte[] payload) {
        ByteBuffer b = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        int code = payload.length >= 4 ? b.getInt(0) : -1;
        int end = 8;
        while (end < payload.length && payload[end] != 0) {
            end++;
        }
        String message = payload.length > 8
                ? new String(payload, 8, end - 8, StandardCharsets.UTF_8) : "";
        return message + " (код " + code + ")";
    }

    static int crc(byte[] b, int off, int len) {
        CRC32 c = new CRC32();
        c.update(b, off, len);
        return (int) c.getValue();
    }

    private static byte[] utf8Prefix(String s, int max) {
        byte[] all = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
        if (all.length <= max) {
            return all;
        }
        int n = max;
        while (n > 0 && (all[n] & 0xC0) == 0x80) {
            n--;
        }
        byte[] out = new byte[n];
        System.arraycopy(all, 0, out, 0, n);
        return out;
    }

    private static ByteBuffer le(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static boolean samePixel(byte[] px, int a, int b) {
        int oa = a * 4;
        int ob = b * 4;
        return px[oa] == px[ob] && px[oa + 1] == px[ob + 1]
                && px[oa + 2] == px[ob + 2] && px[oa + 3] == px[ob + 3];
    }
}
