package util;

/**
 * Created by MachPro on 5/10/17.
 */
public class QuantizationUtil {
    private static int[][] quantiaztion = {{16, 11, 10, 16, 24, 40, 51, 61},
            {12, 12, 14, 19, 26, 58, 60, 55}, {14, 13, 16, 24, 40, 57, 69, 56},
            {14, 17, 22, 29, 51, 87, 80, 62}, {18, 22, 37, 56, 68, 109, 103, 77},
            {24, 35, 55, 64, 81, 104, 113, 92}, {49, 64, 78, 87, 103, 121, 120, 101},
            {72, 92, 95, 98, 112, 100, 103, 99}};

    public static void quantize(int[] dctVals, int factor) {
        for (int i = 0; i < quantiaztion.length; ++i) {
            for (int j = 0; j < quantiaztion[i].length; ++j) {
                dctVals[i * quantiaztion[i].length + j] /= (quantiaztion[i][j] * factor);
            }
        }
    }

    public static void quantize(int[][] dctVals) {
        for (int i = 0; i < dctVals.length; ++i) {
            for (int j = 0; j < dctVals[i].length; ++j) {
                dctVals[i][j] /= quantiaztion[i][j];
            }
        }
    }

    public static void dequantize(int[] dctVals, int factor) {
        for (int i = 0; i < quantiaztion.length; ++i) {
            for (int j = 0; j < quantiaztion[i].length; ++j) {
                dctVals[i * quantiaztion[i].length + j] *= (quantiaztion[i][j] * factor);
            }
        }
    }

    public static void dequantize(int[][] dctVals) {
        for (int i = 0; i < dctVals.length; ++i) {
            for (int j = 0; j < dctVals[i].length; ++j) {
                dctVals[i][j] *= quantiaztion[i][j];
            }
        }
    }
}
