package util;

/**
 * Created by MachPro on 5/12/17.
 */
public class ByteUtil {

    public static int byteToInt(byte[] bytes, int start, int len) {
        int result = 0;
        for (int i = 0; i < len; ++i) {
            if (start + i < bytes.length) {
                result = (result << 8) | (bytes[start + i] & 0xFF);
            } else {
                break;
            }
        }
        return result;
    }
}
