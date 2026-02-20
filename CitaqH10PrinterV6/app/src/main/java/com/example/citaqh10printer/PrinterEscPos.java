package com.example.citaqh10printer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import android.graphics.Bitmap;

public class PrinterEscPos {

    public static final Charset ESC_POS_CHARSET = Charset.forName("ISO-8859-1");
    public enum DitherMode { THRESHOLD, FLOYD_STEINBERG }

    public static void initialize(OutputStream os) throws IOException {
        os.write(new byte[]{0x1B, '@'}); // ESC @
    }

    public static void setAlignCenter(OutputStream os) throws IOException {
        os.write(new byte[]{0x1B, 'a', 0x01});
    }

    public static void feed(OutputStream os, int lines) throws IOException {
        for (int i = 0; i < lines; i++) os.write(' ');
    }

    public static void cut(OutputStream os) throws IOException {
        os.write(new byte[]{0x1D, 'V', 66, 0}); // GS V 66 0
    }


    /**
     * Print a monochrome bitmap using ESC/POS raster bit image (GS v 0).
     * Converts the bitmap to 1-bpp (packed) and sends it to the printer.
     *
     * @param os         printer OutputStream
     * @param bmp        source bitmap (ARGB_8888 recommended)
     * @param mode       dithering mode (THRESHOLD = fastest, FLOYD_STEINBERG = best quality)
     * @throws IOException if writing fails
     */
    public static void printBitmap(OutputStream os, Bitmap bmp, DitherMode mode) throws IOException {
        if (bmp == null || bmp.getWidth() <= 0 || bmp.getHeight() <= 0) return;

        final int width = bmp.getWidth();
        final int height = bmp.getHeight();
        final int bytesPerRow = (width + 7) / 8;

        // 1) Center align
        setAlignCenter(os);

        // 2) Convert to packed 1-bpp (leftmost pixel = MSB)
        byte[] mono = (mode == DitherMode.FLOYD_STEINBERG)
                ? toMono1bppFloydSteinberg(bmp)
                : toMono1bppThreshold(bmp);

        // 3) ESC/POS raster header: GS v 0 m xL xH yL yH, with m=0 (normal)
        int xL = (bytesPerRow) & 0xFF;
        int xH = (bytesPerRow >> 8) & 0xFF;
        int yL = (height) & 0xFF;
        int yH = (height >> 8) & 0xFF;

        os.write(new byte[]{0x1D, 0x76, 0x30, 0x00, (byte) xL, (byte) xH, (byte) yL, (byte) yH});
        os.write(mono);
    }

    /** Convenience overload: default to THRESHOLD (fast). */
    public static void printBitmap(OutputStream os, Bitmap bmp) throws IOException {
        printBitmap(os, bmp, DitherMode.THRESHOLD);
    }

    /** Global threshold to 1-bpp (fast). Packed 8 px per byte, MSB first. */
    private static byte[] toMono1bppThreshold(Bitmap src) {
        final int w = src.getWidth();
        final int h = src.getHeight();
        final int bytesPerRow = (w + 7) / 8;
        final byte[] out = new byte[bytesPerRow * h];

        final int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            src.getPixels(row, 0, w, 0, y, w, 1);
            int byteIndex = y * bytesPerRow;
            int bit = 7;
            int acc = 0;
            for (int x = 0; x < w; x++) {
                final int p = row[x];
                final int r = (p >> 16) & 0xFF;
                final int g = (p >> 8)  & 0xFF;
                final int b = (p)       & 0xFF;
                // Luma BT.601
                final int y8 = (int)(0.299 * r + 0.587 * g + 0.114 * b);
                final boolean black = (y8 < 128);
                if (black) acc |= (1 << bit);
                bit--;
                if (bit < 0) { out[byteIndex++] = (byte) acc; bit = 7; acc = 0; }
            }
            if (bit != 7) out[byteIndex] = (byte) acc;
        }
        return out;
    }

    /** Floyd–Steinberg error diffusion to 1-bpp (quality). Packed 8 px per byte, MSB first. */
    private static byte[] toMono1bppFloydSteinberg(Bitmap src) {
        final int w = src.getWidth();
        final int h = src.getHeight();
        final int bytesPerRow = (w + 7) / 8;
        final byte[] out = new byte[bytesPerRow * h];

        final int[] argbRow = new int[w];
        final int[] cur = new int[w];   // current row (luma + error)
        final int[] nxt = new int[w];   // next row error

        for (int y = 0; y < h; y++) {
            // Load ARGB and compute luma + carry error from previous step
            src.getPixels(argbRow, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                final int p = argbRow[x];
                final int r = (p >> 16) & 0xFF;
                final int g = (p >> 8)  & 0xFF;
                final int b = (p)       & 0xFF;
                cur[x] = (int)(0.299 * r + 0.587 * g + 0.114 * b) + cur[x]; // add carried error
            }

            int bit = 7;
            int acc = 0;
            int byteIndex = y * bytesPerRow;

            for (int x = 0; x < w; x++) {
                int old = cur[x];
                if (old < 0) old = 0; else if (old > 255) old = 255;
                final int newPix = (old < 128) ? 0 : 255;
                final int err = old - newPix;

                if (newPix == 0) acc |= (1 << bit); // black=1
                bit--;
                if (bit < 0) { out[byteIndex++] = (byte) acc; bit = 7; acc = 0; }

                // propagate error (classic FS: 7/16, 3/16, 5/16, 1/16)
                if (x + 1 < w)    cur[x + 1] += (err * 7) / 16;
                if (y + 1 < h) {
                    if (x > 0)    nxt[x - 1] += (err * 3) / 16;
                    nxt[x]        += (err * 5) / 16;
                    if (x + 1 < w) nxt[x + 1] += (err * 1) / 16;
                }
            }
            if (bit != 7) out[byteIndex] = (byte) acc;

            // Move next-row errors down and clear
            java.util.Arrays.fill(cur, 0);
            System.arraycopy(nxt, 0, cur, 0, w);
            java.util.Arrays.fill(nxt, 0);
        }
        return out;
    }

    /**
     * ESC/POS QR Code (Model 2). size: 1..16; ecc: 48=L,49=M,50=Q,51=H
     */
    public static void printQr(OutputStream os, String data, int size, int ecc) throws IOException {
        byte[] bytes = data.getBytes(ESC_POS_CHARSET);
        // Select model 2
        os.write(new byte[]{0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00});
        // Module size
        if (size < 1) size = 6; if (size > 16) size = 16;
        os.write(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, (byte) size});
        // ECC level
        if (ecc < 48 || ecc > 51) ecc = 51;
        os.write(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, (byte) ecc});
        // Store data
        int len = bytes.length + 3;
        byte pL = (byte) (len & 0xFF);
        byte pH = (byte) ((len >> 8) & 0xFF);
        os.write(new byte[]{0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30});
        os.write(bytes);
        // Print
        os.write(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30});
    }
}
