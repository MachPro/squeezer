package util;

/**
 * Created by yanliw on 17-4-21.
 */
public class ArrayUtil {

    /**
     * Convert 2D row by col array to 1D consecutive array.
     */
    public static void twoDToOneD(int[][] twoD, int[] oneD) {
        for (int i = 0; i < twoD.length; ++i) {
            for (int j = 0; j < twoD[0].length; ++j) {
                oneD[i * twoD.length + j] = twoD[i][j];
            }
        }
    }

    /**
     * Convert 1D consecutive array to 2D row by col array.
     */
    public static void oneDToTwoD(int[] oneD, int startIdx, int[][] twoD) {
        for (int i = 0; i < twoD.length; ++i) {
            for (int j = 0; j < twoD[0].length; ++j) {
                twoD[i][j] = oneD[startIdx + i * twoD.length + j];
            }
        }
    }

    /**
     * Fill an array starting at specified index with another array.
     *
     * @param from     the array which will be used to fill another array
     * @param to       the array to be filled
     * @param startIdx the starting idx of the array which will be filled
     */
    public static void fill(int[] from, int[] to, int startIdx) {
        for (int i = 0; i < from.length && startIdx + i < to.length; ++i) {
            to[startIdx + i] = from[i];
        }
    }

    /**
     * Fill a 1-D array using the data from an 2-D array.
     */
    public static void fill(int[][] from, int[] to, int startIdx) {
        int count = 0;
        for (int i = 0; i < from.length; ++i) {
            for (int j = 0; j < from[0].length; ++j) {
                if (startIdx + count < to.length) {
                    to[startIdx + count] = from[i][j];
                    ++count;
                } else {
                    break;
                }
            }
        }
    }

    /**
     * Fill a range of an array with the data from another array.
     */
    public static void fillRange(byte[] from, byte[] to,
                                 int startFromIdx, int endFromIdx,
                                 int startToIdx, int endToIdx) {
        for (int i = 0; i < endFromIdx - startFromIdx && i < endToIdx - startToIdx; ++i) {
            if (startFromIdx + i < from.length && startToIdx + i < to.length) {
                to[startToIdx + i] = from[startFromIdx + i];
            } else {
                break;
            }
        }
    }
}
