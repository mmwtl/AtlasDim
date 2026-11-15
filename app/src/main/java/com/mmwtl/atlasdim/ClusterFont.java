package com.mmwtl.atlasdim;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Шрифт штатной приборки GeelyBCTF (температура за бортом, RESEARCH.md п. 10).
 * Шрифт закрытый: в APK и репозитории его нет, при применении композиции нужное
 * начертание один раз вырезается через telnet из resource.kzb на QNX (только чтение)
 * и хранится в файлах приложения. Кириллицы в нём нет — её Android берёт из системного.
 */
final class ClusterFont {
    private static final String TAG = "AtlasDim";
    static final int SYSTEM = 0;
    static final int REGULAR = 1;
    /** GeelyBCTF Switches — по толщине между Regular и Bold, ближе всех к температуре. */
    static final int MEDIUM = 2;
    static final int BOLD = 3;
    /** Свой шрифт пользователя ({@link UserFonts}), имя файла — в SceneConfig. */
    static final int USER = 4;
    private static final String KZB = "/apps/cluster/*/bin/hmi/*/ClusterHMI/resource.kzb";
    private static final long FETCH_TIMEOUT_MS = 120000;

    /** TTF внутри resource.kzb: смещение, размер и SHA-256 начертания на своей модели. */
    private static final class Face {
        final String file;
        final long offset;
        final int size;
        final String sha256;
        Typeface typeface;

        Face(String file, long offset, int size, String sha256) {
            this.file = file;
            this.offset = offset;
            this.size = size;
            this.sha256 = sha256;
        }

        /** dd читает блоками по 8, если смещение и размер кратны 8, иначе по 4. */
        int block() {
            return offset % 8 == 0 && size % 8 == 0 ? 8 : 4;
        }
    }

    /** Switches на обеих моделях один и тот же файл: общее имя, второй раз не скачивается. */
    private static final String SWITCHES = "GeelyBCTF-Switches.ttf";
    private static final String SWITCHES_SHA256 =
            "f73a22cf07912c7399749aa683fffd0da12d3e54a9e2840a5284de56496c2f1d";

    private static final Face[] FACES = {null,
            new Face("GeelyBCTF-Regular.ttf", 0x6918b20, 490032,
                    "50c7cbf722e910fa34486e27bec8e212f2e5ee29f9564148d22019522547f755"),
            new Face(SWITCHES, 0x6990558, 82312, SWITCHES_SHA256),
            new Face("GeelyBCTF-Bold.ttf", 0x68935f0, 463544,
                    "f7165f21b904d704971376560d364c11f0e8824242a2854ab34542f3fa9a27f8")};

    /**
     * Ситирей G426_J1: /apps/cluster/FX11_HEV/bin/hmi/GLY_FS11-A2J1/ClusterHMI/resource.kzb,
     * проверено на машине (PR #1, docs/cityray-kzb-fonts.md). Bold не кратен 8 — dd по 4.
     */
    private static final Face[] CITYRAY_FACES = {null,
            new Face("Cityray-GeelyBCTF-Regular.ttf", 0x3992620L, 83288,
                    "5c9b8ed5763106dffaa65a0264e8fe98351be75604403520a81eaf544ff05db2"),
            new Face(SWITCHES, 0x39a6b80L, 82312, SWITCHES_SHA256),
            new Face("Cityray-GeelyBCTF-Bold.ttf", 0x396aa1cL, 80268,
                    "0a59701719059ee8b42684b2067611872456fda438d7155cb56b13da5ee3ac27")};
    private static File dir;

    private ClusterFont() {
    }

    static synchronized void init(Context context) {
        if (dir == null) {
            dir = context.getApplicationContext().getFilesDir();
        }
    }

    /** Начертание или null: системный шрифт либо оно ещё не забрано с QNX. */
    static synchronized Typeface typeface(int font, String vehicle) {
        Face face = face(font, vehicle);
        if (face == null || dir == null) {
            return null;
        }
        File file = new File(dir, face.file);
        if (face.typeface == null && file.length() == face.size) {
            try {
                face.typeface = Typeface.createFromFile(file);
            } catch (RuntimeException e) {
                Log.w(TAG, "шрифт приборки не читается: " + e.getMessage());
            }
        }
        return face.typeface;
    }

    static boolean saved(int font, String vehicle) {
        return face(font, vehicle) == null || typeface(font, vehicle) != null;
    }

    /** Композиции нужно начертание, которого ещё нет, — без telnet не обойтись. */
    static boolean missing(SceneConfig config) {
        return config.clock.enabled && !saved(config.clockFont, config.vehicle)
                || config.fuel.enabled && !saved(config.fuelFont, config.vehicle)
                || config.temp.enabled && !saved(config.tempFont, config.vehicle);
    }

    /** Забирает с QNX недостающие начертания композиции. */
    static void fetch(QnxShell qnx, SceneConfig config) throws IOException {
        if (config.clock.enabled) {
            fetch(qnx, config.clockFont, config.vehicle);
        }
        if (config.fuel.enabled) {
            fetch(qnx, config.fuelFont, config.vehicle);
        }
        if (config.temp.enabled) {
            fetch(qnx, config.tempFont, config.vehicle);
        }
    }

    private static void fetch(QnxShell qnx, int font, String vehicle) throws IOException {
        if (saved(font, vehicle)) {
            return;
        }
        // Не та прошивка — не тот SHA-256 ниже: файл не сохраняется.
        Face face = face(font, vehicle);
        int block = face.block();
        QnxShell.Result r = qnx.exec("for f in " + KZB + "; do dd if=$f bs=" + block + " skip="
                + face.offset / block + " count=" + face.size / block
                + " 2>/dev/null | openssl base64; break; done", FETCH_TIMEOUT_MS);
        byte[] data = Base64.decode(r.output.replaceAll("[^A-Za-z0-9+/=]", ""), Base64.DEFAULT);
        if (data.length != face.size || !face.sha256.equals(sha256(data))) {
            throw new IOException("в resource.kzb этой прошивки нет " + face.file);
        }
        File file = new File(dir, face.file);
        File part = new File(dir, face.file + ".part");
        try (FileOutputStream out = new FileOutputStream(part)) {
            out.write(data);
        }
        synchronized (ClusterFont.class) {
            if (!part.renameTo(file)) {
                throw new IOException("не удалось сохранить " + face.file);
            }
            face.typeface = null;
        }
        Log.i(TAG, "шрифт приборки сохранён: " + file);
    }

    private static Face face(int font, String vehicle) {
        Face[] faces = Vehicle.cityray(vehicle) ? CITYRAY_FACES : FACES;
        return font > SYSTEM && font < faces.length ? faces[font] : null;
    }

    static String sha256(byte[] data) {
        try {
            StringBuilder sb = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
