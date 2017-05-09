package util;

import conf.Configuration;

import java.util.Arrays;

/**
 * Different ways to calculate DCT and iDCT.
 * <p/>
 * Created by MachPro on 17-4-19.
 */
public class DCTUtil {

    private static final double SQRT_TWO = Math.sqrt(2);
    // cos(pi * 1 / 16) << 10
    private static final int c1 = 1004;
    // sin(pi * 1 / 16) << 10
    private static final int s1 = 200;
    // cos(pi * 3 / 16) << 10
    private static final int c3 = 851;
    // sin(pi * 3 / 16) << 10
    private static final int s3 = 569;
    // sqrt(2) * cos(pi * 6 / 16) << 10
    private static final int r2c6 = 554;
    // sqrt(2) * sin(pi * 6 / 16) << 10
    private static final int r2s6 = 1337;
    // sqrt(2) << 7
    private static final int r2 = 181;

    private static double B0 = 1.0000000000000000000000;
    // cos(pi * 1 / 16) * sqrt(2)
    private static double B1 = 1.3870398453221474618216;
    // cos(pi * 2 / 16) * sqrt(2)
    private static double B2 = 1.3065629648763765278566;
    // cos(pi * 3 / 16) * sqrt(2)
    private static double B3 = 1.1758756024193587169745;
    // cos(pi * 4 / 16) * sqrt(2)
    private static double B4 = 1.0000000000000000000000;
    // cos(pi * 5 / 16) * sqrt(2)
    private static double B5 = 0.7856949583871021812779;
    // cos(pi * 6 / 16) * sqrt(2)
    private static double B6 = 0.5411961001461969843997;
    // cos(pi * 7 / 16) * sqrt(2)
    private static double B7 = 0.2758993792829430123360;
    // cos(pi * 2 / 16)
    private static double A2 = 0.92387953251128675613;
    // cos(pi * 4 / 16)
    private static double A4 = 0.70710678118654752438;

    private static double[] prescale = new double[64];

    private static double[][] cosineValue;

    private static int dctBlkLen = Configuration.DCT_BLOCK_LEN;

    private static double[][] c, cT;

    static {
        double[] bVals = new double[]{B0, B1, B2, B3, B4, B5, B6, B7};
        for (int i = 0; i < 8; ++i) {
            for (int j = 0; j < 8; ++j) {
                prescale[i * 8 + j] = bVals[i] * bVals[j] / 8;
            }
        }

        cosineValue = new double[dctBlkLen][dctBlkLen];
        for (int i = 0; i < dctBlkLen; ++i) {
            for (int j = 0; j < dctBlkLen; ++j) {
                cosineValue[i][j] = Math.cos((2 * i + 1) * j * Math.PI / 2 / dctBlkLen);
            }
        }

        c = new double[8][8];
        // transformed cosine matrix, N*N.
        cT = new double[8][8];

        //initialize c[][] and cT[][]
        for (int j = 0; j < 8; j++) {
            c[0][j] = 1.0 / Math.sqrt(8.0);
            cT[j][0] = c[0][j];
        }

        double sqrtQuarter = Math.sqrt(2.0 / 8.0);
        for (int i = 1; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                c[i][j] = sqrtQuarter * cosineValue[j][i];
                cT[j][i] = c[i][j];
            }
        }
    }

    /**
     * Convert the 1D 1 * 64 array into 2D 8 * 8 array first and calculate DCT value.
     */
    public static int[] do2DDCTWithAAN(int[] originalBlock) {
        int[][] rowColBlock = new int[dctBlkLen][dctBlkLen];
        int[] result = new int[dctBlkLen * dctBlkLen];

        ArrayUtil.oneDToTwoD(originalBlock, 0, rowColBlock);
        int[][] dctVals = do2DDCTWithAAN(rowColBlock);
        ArrayUtil.twoDToOneD(dctVals, result);

        return result;
    }

    /**
     * Implementation of a faster 2D DCT calculation based on 1D AAN DCT.
     *
     * @see <a href="https://unix4lyfe.org/dct">https://unix4lyfe.org/dct</a>
     */
    public static int[][] do2DDCTWithAAN(int[][] originalBlock) {
        int[][] dctVals = new int[dctBlkLen][dctBlkLen];
        int[][] rowDctVals = new int[dctBlkLen][dctBlkLen];
        // row DCT using AAN algo.
        for (int i = 0; i < dctBlkLen; ++i) {
            int[] tmp = doAANDCT(originalBlock[i]);
            rowDctVals[i][0] = tmp[6];
            rowDctVals[i][4] = tmp[4];
            rowDctVals[i][2] = tmp[8] >> 10;
            rowDctVals[i][6] = tmp[7] >> 10;
            rowDctVals[i][7] = (tmp[2] - tmp[5]) >> 10;
            rowDctVals[i][1] = (tmp[2] + tmp[5]) >> 10;
            rowDctVals[i][3] = (tmp[3] * r2) >> 17;
            rowDctVals[i][5] = (tmp[0] * r2) >> 17;
        }
        for (int j = 0; j < dctBlkLen; ++j) {
            // transform row
            int[] colDct = new int[dctBlkLen];
            for (int i = 0; i < dctBlkLen; ++i) {
                colDct[i] = rowDctVals[i][j];
            }
            // column DCT using AAN algo.
            int[] tmp = doAANDCT(colDct);
            dctVals[0][j] = (tmp[6] + 16) >> 3;
            dctVals[4][j] = (tmp[4] + 16) >> 3;
            dctVals[2][j] = (tmp[8] + 16384) >> 13;
            dctVals[6][j] = (tmp[7] + 16384) >> 13;
            dctVals[7][j] = (tmp[2] - tmp[5] + 16384) >> 13;
            dctVals[1][j] = (tmp[2] + tmp[5] + 16384) >> 13;
            dctVals[3][j] = ((tmp[3] >> 8) * r2 + 8192) >> 12;
            dctVals[5][j] = ((tmp[0] >> 8) * r2 + 8192) >> 12;
        }
        return dctVals;
    }

    /**
     * Implementation for Arai-Agui-Nakajima algorithm, a better way to calculate 1D DCT.
     *
     * @see <a href="https://unix4lyfe.org/dct">https://unix4lyfe.org/dct</a>
     */
    public static int[] doAANDCT(int[] inBlock) {
        int[] dctVals = Arrays.copyOf(inBlock, dctBlkLen + 1);
        // step 1
        dctVals[8] = dctVals[0] + dctVals[7];
        dctVals[0] -= dctVals[7];
        dctVals[7] = dctVals[1] + dctVals[6];
        dctVals[1] -= dctVals[6];
        dctVals[6] = dctVals[2] + dctVals[5];
        dctVals[2] -= dctVals[5];
        dctVals[5] = dctVals[3] + dctVals[4];
        dctVals[3] -= dctVals[4];
        // step 2
        dctVals[4] = dctVals[5] + dctVals[8];
        dctVals[8] -= dctVals[5];
        dctVals[5] = dctVals[6] + dctVals[7];
        dctVals[7] -= dctVals[6];
        dctVals[6] = c1 * (dctVals[1] + dctVals[2]);
        dctVals[2] = (-s1 - c1) * dctVals[2] + dctVals[6];
        dctVals[1] = (s1 - c1) * dctVals[1] + dctVals[6];
        dctVals[6] = c3 * (dctVals[0] + dctVals[3]);
        dctVals[3] = (-s3 - c3) * dctVals[3] + dctVals[6];
        dctVals[0] = (s3 - c3) * dctVals[0] + dctVals[6];
        // step 3
        dctVals[6] = dctVals[4] + dctVals[5];
        dctVals[4] -= dctVals[5];
        dctVals[5] = r2c6 * (dctVals[7] + dctVals[8]);
        dctVals[7] = (-r2s6 - r2c6) * dctVals[7] + dctVals[5];
        dctVals[8] = (r2s6 - r2c6) * dctVals[8] + dctVals[5];
        dctVals[5] = dctVals[0] + dctVals[2];
        dctVals[0] -= dctVals[2];
        dctVals[2] = dctVals[1] + dctVals[3];
        dctVals[3] -= dctVals[1];

        return dctVals;
    }

    /**
     * Native implementation for 2D DCT calculation.
     * The time complexity is O(N ^ 4) for a N * N block.
     */
    public int[][] doNative2DDCT(int[][] originalBlock) {
        int[][] dctVals = new int[dctBlkLen][dctBlkLen];
        for (int u = 0; u < dctBlkLen; ++u) {
            for (int v = 0; v < dctBlkLen; ++v) {
                double tmp = .0;
                for (int x = 0; x < dctBlkLen; ++x) {
                    for (int y = 0; y < dctBlkLen; ++y) {
                        tmp += cosineValue[x][u] * cosineValue[y][v] * originalBlock[x][y];
                    }
                }
                tmp *= 1 / Math.sqrt(2 * dctBlkLen);
                if (u == 0) {
                    tmp /= SQRT_TWO;
                }
                if (v == 0) {
                    tmp /= SQRT_TWO;
                }
                dctVals[u][v] = (int) Math.round(tmp);
            }
        }
        return dctVals;
    }

    public static int[][] doN3iDCT(int quantization, int[][] rowColBlock) {
        int[][] iDCTVals = new int[dctBlkLen][dctBlkLen];
        double temp[][] = new double[dctBlkLen][dctBlkLen];
        for (int x = 0; x < dctBlkLen; ++x) {
            for (int y = 0; y < dctBlkLen; ++y) {
                temp[x][y] = 0.0;
                for (int k = 0; k < 8; k++) {
                    int input = rowColBlock[x][k] / quantization;
                    temp[x][y] += input * c[k][y];
                }

            }
        }

        for (int x = 0; x < dctBlkLen; ++x) {
            for (int y = 0; y < dctBlkLen; ++y) {
                double sum = 0.0;

                for (int k = 0; k < 8; k++) {
                    sum += (cT[x][k] * temp[k][y]);
                }
                sum *= quantization;
                sum = Math.round(sum);
                if (sum <= 0) {
                    iDCTVals[x][y] = 0;
                } else if (sum >= 255) {
                    iDCTVals[x][y] = 255;
                } else {
                    iDCTVals[x][y] = (int) sum;
                }
            }
        }

        return iDCTVals;
    }

    public static int[] doAANiDCT(int quantization, int[] dctVals) {
        double temp[] = new double[64];

        for (int i = 0; i < 64; i++) {
            temp[i] = (dctVals[i] / quantization) * prescale[i];
        }

        p8iDCT(dctVals, temp, 1, 8, quantization, 0);
        p8iDCT(dctVals, temp, 8, 1, quantization, 1);
        return dctVals;
    }

    public static void p8iDCT(int data[], double temp[], int x, int y, int quantization, int type) {
        double tmp0, tmp1;
        double s04, d04, s17, d17, s26, d26, s53, d53;
        double os07, os16, os25, os34;
        double od07, od16, od25, od34;

        for (int i = 0; i < y * 8; i += y) {
            s17 = temp[1 * x + i] + temp[7 * x + i];
            d17 = temp[1 * x + i] - temp[7 * x + i];
            s53 = temp[5 * x + i] + temp[3 * x + i];
            d53 = temp[5 * x + i] - temp[3 * x + i];

            od07 = s17 + s53;
            od25 = (s17 - s53) * (2 * A4);

            //these 2 are equivalent
            if (i == 0) {
                tmp0 = (d17 + d53) * (2 * A2);
                od34 = d17 * (2 * B6) - tmp0;
                od16 = d53 * (-2 * B2) + tmp0;
            } else {
                od34 = d17 * (2 * (B6 - A2)) - d53 * (2 * A2);
                od16 = d53 * (2 * (A2 - B2)) + d17 * (2 * A2);
            }

            od16 -= od07;
            od25 -= od16;
            od34 += od25;

            s26 = temp[2 * x + i] + temp[6 * x + i];
            d26 = temp[2 * x + i] - temp[6 * x + i];
            tmp1 = d26 * (2 * A4) - s26;

            s04 = temp[0 * x + i] + temp[4 * x + i];
            d04 = temp[0 * x + i] - temp[4 * x + i];

            os07 = s04 + s26;
            os34 = s04 - s26;
            os16 = d04 + tmp1;
            os25 = d04 - tmp1;

            if (type == 0) {
                temp[0 * x + i] = os07 + od07;
                temp[7 * x + i] = os07 - od07;
                temp[1 * x + i] = os16 + od16;
                temp[6 * x + i] = os16 - od16;
                temp[2 * x + i] = os25 + od25;
                temp[5 * x + i] = os25 - od25;
                temp[3 * x + i] = os34 - od34;
                temp[4 * x + i] = os34 + od34;
            } else if (type == 1) {
                int[] sum = new int[8];
                sum[0] = (int) Math.round((os07 + od07) * quantization);
                sum[1] = (int) Math.round((os07 - od07) * quantization);
                sum[2] = (int) Math.round((os16 + od16) * quantization);
                sum[3] = (int) Math.round((os16 - od16) * quantization);
                sum[4] = (int) Math.round((os25 + od25) * quantization);
                sum[5] = (int) Math.round((os25 - od25) * quantization);
                sum[6] = (int) Math.round((os34 - od34) * quantization);
                sum[7] = (int) Math.round((os34 + od34) * quantization);
                for (int k = 0; k < 7; k++) {
                    if (sum[k] <= 0)
                        sum[k] = 0;
                    if (sum[k] >= 255)
                        sum[k] = 255;
                }
                data[0 * x + i] = sum[0];
                data[7 * x + i] = sum[1];
                data[1 * x + i] = sum[2];
                data[6 * x + i] = sum[3];
                data[2 * x + i] = sum[4];
                data[5 * x + i] = sum[5];
                data[3 * x + i] = sum[6];
                data[4 * x + i] = sum[7];
            }
        }
    }
}
