package util;

import conf.Configuration;

/**
 * Convertion from RGB to YCbCr and back.
 * <p/>
 * Created by yanliw on 17-4-18.
 */
public class TransformUtil {

    /**
     * JPEG conversion for RGB to YCbCr.
     *
     * @see <a href="https://en.wikipedia.org/wiki/YCbCr">Wikipedia YCbCr</a>
     */
    public static int[] RGBToYCbCr(int r, int g, int b) {
        int y;
        int cb;
        int cr;
        y = (int) (0.299 * r + 0.587 * g + 0.114 * b);
        cb = (int) (128 - 0.168736 * r - 0.331264 * g + 0.5 * b);
        cr = (int) (128 + 0.5 * r - 0.418688 * g - 0.081312 * b);
        return new int[]{y, cb, cr};
    }

    /**
     * Return only Y channel after conversion.
     *
     * @param yBlock the Y channel that will be returned
     */
    public static void RGBToY(int[] rBlock, int[] gBlock, int[] bBlock, int[] yBlock) {
        for (int i = 0; i < rBlock.length; ++i) {
            int[] tmp = RGBToYCbCr(rBlock[i], gBlock[i], bBlock[i]);
            yBlock[i] = tmp[0];
        }
    }

    public static int RGBToY(byte r, byte g, byte b) {
        return RGBToY(r & Configuration.BYTE_MASK, g & Configuration.BYTE_MASK, b & Configuration.BYTE_MASK);
    }

    public static int RGBToY(int r, int g, int b) {
        return RGBToYCbCr(r, g, b)[0];
    }

    public int[] RGBToYCbCr(int[] RGB) {
        return RGBToYCbCr(RGB[0], RGB[1], RGB[2]);
    }

    /**
     * JPEG conversion for YCbCr to RGB.
     *
     * @see <a href="https://en.wikipedia.org/wiki/YCbCr">Wikipedia YCbCr</a>
     */
    public int[] YCbCrToRGB(int y, int cb, int cr) {
        int r;
        int g;
        int b;
        r = (int) (y + 1.402 * (cr - 128));
        g = (int) (y - 0.344136 * (cb - 128) - 0.714136 * (cr - 128));
        b = (int) (y + 1.772 * (cb - 128));
        return new int[]{r, g, b};
    }

    public int[] YCbCrToRGB(int[] YCbCr) {
        return YCbCrToRGB(YCbCr[0], YCbCr[1], YCbCr[2]);
    }
}
