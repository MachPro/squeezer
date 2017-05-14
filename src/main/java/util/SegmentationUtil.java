package util;

import conf.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Segment the block into foreground and background according to motion vector.
 * <p/>
 * Created by MachPro on 17-5-2.
 */
public class SegmentationUtil {

    private static int width = Configuration.WIDTH;

    private static int height = Configuration.HEIGHT;

    private static int dctBlkLen = Configuration.DCT_BLOCK_LEN;

    private static int mvBlkLen = Configuration.MOTION_VECTOR_BLOCK_LEN;

    // number of dct blocks in one channel of frame
    private static int blockCount = ((width + dctBlkLen - 1) / dctBlkLen) *
            ((height + dctBlkLen - 1) / dctBlkLen);

    private static int mvSearchSize = Configuration.MOTION_VECTOR_SEARCH_SIZE;
    // number of motion vector blocks in x and y axis
    private static int xMvBlockCount = (width + mvBlkLen - 1) / mvBlkLen;

    private static int yMvBlockCount = (height + mvBlkLen - 1) / mvBlkLen;

    /**
     * Find frequent motion vector in one frame, and average them to find global mv.
     * Then use distance threshold to find background.
     */
    public static void segmentationMostMV(int[] currentYChannel, int[] previousYChannel, int[] layer) {
        int[] yBlock = new int[mvBlkLen * mvBlkLen];
        int[][] mvs = new int[blockCount][2];
        Map<Integer, Integer> map = new HashMap<>();

        int generalX = 0;
        int generalY = 0;
        int count = 0;
        if (previousYChannel != null) {
            for (int i = 1; i < yMvBlockCount; ++i) {
                for (int j = 1; j < xMvBlockCount; ++j) {
                    if (i == 0 || i == yMvBlockCount - 1 || j == 0 || j == xMvBlockCount - 1) {
                        continue;
                    }
                    BlockUtil.getBlock(currentYChannel, 0,
                            mvBlkLen * j, mvBlkLen * i, mvBlkLen, yBlock);
                    // calculate motion vector and determine the layer the block belongs to
                    int[] mv = MotionVectorUtil.calcMotionVector(previousYChannel, yBlock,
                            mvSearchSize, mvBlkLen * j, mvBlkLen * i);

                    int diffX = mv[0] - mvBlkLen * j;
                    int diffY = mv[1] - mvBlkLen * i;
                    mvs[i * xMvBlockCount + j][0] = diffX;
                    mvs[i * xMvBlockCount + j][1] = diffY;
                    int key = (diffX + 8) * 17 + (diffY + 8);
                    map.putIfAbsent(key, 0);
                    map.put(key, map.get(key) + 1);

                    generalX += diffX;
                    generalY += diffY;
                    ++count;
                }
            }
            generalX = generalX / count;
            generalY = generalY / count;
            count = 0;
            for (int i = 1; i < yMvBlockCount; ++i) {
                for (int j = 1; j < xMvBlockCount; ++j) {
                    int blockIdx = i * xMvBlockCount + j;
                    double diffX = (mvs[blockIdx][0] - generalX) * (mvs[blockIdx][0] - generalX);
                    double diffY = (mvs[blockIdx][1] - generalY) * (mvs[blockIdx][1] - generalY);

                    int[] indices = BlockUtil.getDCTBlockIdx(j, i);
                    if (i == 0 || i == yMvBlockCount - 1 || j == 0 || j == xMvBlockCount - 1) {
                        for (int idx : indices) {
                            layer[idx] = 0;
                        }
                    } else {
                        if (diffX + diffY > 5) {
                            for (int idx : indices) {
                                layer[idx] = 1;
                            }
                        } else {
                            ++count;
                            for (int idx : indices) {
                                layer[idx] = 0;
                            }
                        }
                    }
                }
            }
            System.out.println(count);
        }
    }

    /**
     * Using two parameters to describe the global motion vector and calculate the global mv iteratively.
     */
    public static void segmentationIterate(int[] currentYChannel, int[] previousYChannel, int[] layer) {
        int[] yBlock = new int[mvBlkLen * mvBlkLen];
        int[][] mvs = new int[blockCount][2];

        double globalX = 0;
        double globalY = 0;
        int count = 0;
        if (previousYChannel != null) {
            // calculate motion vector for all blocks
            for (int i = 1; i < yMvBlockCount - 1; ++i) {
                for (int j = 1; j < xMvBlockCount - 1; ++j) {
                    // get one block from current frame
                    BlockUtil.getBlock(currentYChannel, 0,
                            mvBlkLen * j, mvBlkLen * i, mvBlkLen, yBlock);
                    int[] mv = MotionVectorUtil.calcMotionVector(previousYChannel, yBlock,
                            mvSearchSize, mvBlkLen * j, mvBlkLen * i);
                    if (mv != null) {
                        mvs[i * xMvBlockCount + j][0] = mv[0];
                        mvs[i * xMvBlockCount + j][1] = mv[1];

                        int diffX = mv[0] - mvBlkLen * j;
                        int diffY = mv[1] - mvBlkLen * i;

                        globalX += diffX;
                        globalY += diffY;
                        ++count;
                    } else {
                        // if cannot find an valid motion vector
                        mvs[i * xMvBlockCount + j][0] = Integer.MAX_VALUE;
                        mvs[i * xMvBlockCount + j][1] = Integer.MAX_VALUE;
                    }
                }
            }
            // get the initial global motion vector
            globalX = globalX / count;
            globalY = globalY / count;
            // iterate twice to obtain more accuracy
            for (int i = 1; i <= 2; ++i) {
                double[] result = iterateMV(mvs, globalX, globalY);
                // update global motion vector
                globalX = result[0];
                globalY = result[1];
            }
            // determine the foreground and background
            count = 0;
            for (int i = 1; i < yMvBlockCount - 1; ++i) {
                for (int j = 1; j < xMvBlockCount - 1; ++j) {
                    int blockIdx = i * xMvBlockCount + j;
                    double diffX = mvs[blockIdx][0] - mvBlkLen * j;
                    double diffY = mvs[blockIdx][1] - mvBlkLen * i;
                    // four DCT block indices in this macroblock
                    int[] indices = BlockUtil.getDCTBlockIdx(j, i);
                    // if motion vector is not valid
                    if (mvs[blockIdx][0] >= Integer.MAX_VALUE) {
                        // categorize it into background
                        for (int idx : indices) {
                            layer[idx] = 0;
                        }
                    } else {
                        // if it is similar to the global motion vector
                        if (isSimilar(diffX, diffY, globalX, globalY, 5.0)) {
                            // categorize it into background
                            for (int idx : indices) {
                                layer[idx] = 0;
                            }
                            ++count;
                        } else {
                            for (int idx : indices) {
                                layer[idx] = 1;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Iteratively calculate the global motion vector.
     *
     * @param mvs
     * @param globalX
     * @param globalY
     * @return
     */
    private static double[] iterateMV(int[][] mvs, double globalX, double globalY) {
        double updateX = .0;
        double updateY = .0;
        int count = 0;
        int xMvBlockCount = (width + mvBlkLen - 1) / mvBlkLen;
        int yMvBlockCount = (height + mvBlkLen - 1) / mvBlkLen;

        for (int i = 1; i < yMvBlockCount - 1; ++i) {
            for (int j = 1; j < xMvBlockCount - 1; ++j) {
                if (mvs[i * xMvBlockCount + j][0] >= Integer.MAX_VALUE) {
                    continue;
                }
                int diffX = mvs[i * xMvBlockCount + j][0] - mvBlkLen * j;
                int diffY = mvs[i * xMvBlockCount + j][1] - mvBlkLen * i;

                if (isSimilar(diffX, diffY, globalX, globalY, 5.0)) {
                    updateX += diffX;
                    updateY += diffY;
                    ++count;
                }
            }
        }
        updateX /= count;
        updateY /= count;

        return new double[]{updateX, updateY, count};
    }

    private static boolean isSimilar(double diffX, double diffY,
                                     double generalX, double generalY, double lenThreshold) {
        double lenDiff = Math.pow(diffX - generalX, 2) + Math.pow(diffY - generalY, 2);

        return lenDiff < lenThreshold;
    }

    /**
     * Using four parameters kx, bx, ky, by to predicate the global motion vector and iteratively calculate it.
     * <br/>
     * GlobalX = kx * x + bx, GlobalY = ky * y + by;
     */
    public static void segmentationMultiParam(int[] currentYChannel, int[] previousYChannel, int[] layer) {
        int[] yBlock = new int[mvBlkLen * mvBlkLen];
        int[][] mvs = new int[blockCount][2];

        if (previousYChannel != null) {
            // calculate motion vector for every block
            for (int i = 1; i < yMvBlockCount - 1; ++i) {
                for (int j = 1; j < xMvBlockCount - 1; ++j) {
                    BlockUtil.getBlock(currentYChannel, 0,
                            mvBlkLen * j, mvBlkLen * i, mvBlkLen, yBlock);
                    // calculate motion vector and determine the layer the block belongs to
                    int[] mv = MotionVectorUtil.calcMotionVector(previousYChannel, yBlock,
                            mvSearchSize, mvBlkLen * j, mvBlkLen * i);
                    if (mv != null) {
                        mvs[i * xMvBlockCount + j][0] = mv[0];
                        mvs[i * xMvBlockCount + j][1] = mv[1];
                    } else {
                        // invalid mv
                        mvs[i * xMvBlockCount + j][0] = Integer.MAX_VALUE;
                        mvs[i * xMvBlockCount + j][1] = Integer.MAX_VALUE;
                    }
                }
            }
            // calculate the parameters
            double[] params = initGlobalMV(mvs);
            // iterate twice
            for (int i = 2; i <= 3; ++i) {
                params = iterateMV(mvs, params[0], params[1], params[2], params[3], 5.0);
            }
            // determine background and foreground
            int count = 0;
            for (int i = 1; i < yMvBlockCount - 1; ++i) {
                for (int j = 1; j < xMvBlockCount - 1; ++j) {
                    int[] indices = BlockUtil.getDCTBlockIdx(j, i);
                    if (mvs[i * xMvBlockCount + j][0] >= Integer.MAX_VALUE) {
                        // invalid mv, background
                        for (int idx : indices) {
                            layer[idx] = 0;
                        }
                    } else {
                        int vx = mvs[i * xMvBlockCount + j][0];
                        int vy = mvs[i * xMvBlockCount + j][1];
                        int sx = mvBlkLen * j;
                        int sy = mvBlkLen * i;
                        if (isSimilar(params[0], params[1], params[2], params[3], vx, vy, sx, sy, 5.0)) {
                            ++count;
                            for (int idx : indices) {
                                layer[idx] = 0;
                            }
                        } else {
                            for (int idx : indices) {
                                layer[idx] = 1;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Find the initial parameters to describe the global motion vector.
     *
     * @param mvs motion vectors for all blocks
     * @return four parameters to describe the global motion vector
     */
    private static double[] initGlobalMV(int[][] mvs) {
        return iterateMV(mvs, 0, 0, 0, 0, Integer.MAX_VALUE);
    }

    /**
     * Iteratively update the parameters to describe the global motion vector.
     *
     * @see <a href="">Golam Sorwar et al. A novel filter for block-based motion estimation</a>
     */
    private static double[] iterateMV(int[][] mvs, double kx, double bx, double ky, double by, double lenThreshold) {
        int count = 0;

        long sumVx = 0;
        long sumVy = 0;
        long sumSx = 0;
        long sumSy = 0;
        long sumSxSq = 0;
        long sumSySq = 0;
        long sumVxSx = 0;
        long sumVySy = 0;
        for (int i = 1; i < yMvBlockCount - 1; ++i) {
            for (int j = 1; j < xMvBlockCount - 1; ++j) {
                if (mvs[i * xMvBlockCount + j][0] >= Integer.MAX_VALUE) {
                    continue;
                }
                int vx = mvs[i * xMvBlockCount + j][0];
                int vy = mvs[i * xMvBlockCount + j][1];
                int sx = mvBlkLen * j;
                int sy = mvBlkLen * i;
                // only consider those motion vectors which are similar to current predicated global one
                if (isSimilar(kx, bx, ky, by, vx, vy, sx, sy, lenThreshold)) {
                    ++count;

                    sumSx += sx;
                    sumSy += sy;
                    sumVx += vx;
                    sumVy += vy;
                    sumSxSq += (sx * sx);
                    sumSySq += (sy * sy);
                    sumVxSx += (vx * sx);
                    sumVySy += (vy * sy);
                }
            }
        }
        kx = 1.0 * (count * sumVxSx - sumVx * sumSx) / (count * sumSxSq - sumSx * sumSx);
        bx = 1.0 * (sumVx * sumSxSq - sumVxSx * sumSx) / (count * sumSxSq - sumSx * sumSx);
        ky = 1.0 * (count * sumVySy - sumVy * sumSy) / (count * sumSySq - sumSy * sumSy);
        by = 1.0 * (sumVy * sumSySq - sumVySy * sumSy) / (count * sumSySq - sumSy * sumSy);

        return new double[]{kx, bx, ky, by, count};
    }

    /**
     * To determine whether the calculated motion vector is similar to the predicated global one.
     *
     * @param kx           params to predicate global motion vector
     * @param bx
     * @param ky
     * @param by
     * @param vx           the motion vector we calculate
     * @param vy
     * @param sx           the coordinate in current frame
     * @param sy
     * @param lenThreshold
     * @return
     */
    private static boolean isSimilar(double kx, double bx, double ky, double by,
                                     int vx, int vy, int sx, int sy, double lenThreshold) {
        double diffX = vx - kx * sx - bx;
        double diffY = vy - ky * sy - by;
        double distance = Math.pow(diffX, 2) + Math.pow(diffY, 2);
        return distance < lenThreshold;
    }
}
