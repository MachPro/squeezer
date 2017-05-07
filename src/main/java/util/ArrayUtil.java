package util;

import java.util.Arrays;

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
     * @param from        the array which will be used to fill another array
     * @param to          the array to be filled
     * @param startingIdx the starting idx of the array which will be filled
     */
    public static void fill(int[] from, int[] to, int startingIdx) {
        for (int i = 0; i < from.length && startingIdx + i < to.length; ++i) {
            to[startingIdx + i] = from[i];
        }
    }

    public static void fill(int[][] from, int[] to, int startingIdx) {
        int count = 0;
        for (int i = 0; i < from.length; ++i) {
            for (int j = 0; j < from[0].length; ++j) {
                if (startingIdx + count < to.length) {
                    to[startingIdx + count] = from[i][j];
                    ++count;
                } else {
                    break;
                }
            }
        }
    }
}
