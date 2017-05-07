package util;

import conf.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by yanliw on 17-5-2.
 */
public class SegmentationUtil {

    public static int width = Configuration.WIDTH;

    public static int height = Configuration.HEIGHT;

    public static int dctBlkLen = Configuration.DCT_BLOCK_LEN;

    public static int mvBlkLen = Configuration.MOTION_VECTOR_BLOCK_LEN;

    // number of dct blocks in one channel of frame
    public static int blockCount = ((width + dctBlkLen - 1) / dctBlkLen) *
            ((height + dctBlkLen - 1) / dctBlkLen);

    public static int mvSearchSize = Configuration.MOTION_VECTOR_SEARCH_SIZE;

    /**
     * Find frequent motion vector in one frame, and average them to find global mv.
     * Then use distance threshold to find background.
     *
     * @param currentYChannel
     * @param previousYChannel
     * @param layer
     */
    public static void segmentationMostMV(int[] currentYChannel, int[] previousYChannel, int[] layer) {
        int[] yBlock = new int[mvBlkLen * mvBlkLen];
        int[][] mvs = new int[blockCount][2];
        Map<Integer, Integer> map = new HashMap<>();

        int xBlockCount = (width + mvBlkLen - 1) / mvBlkLen;
        // number of motion vector blocks in x and y axis
        int xMvBlockCount = (width + mvBlkLen - 1) / mvBlkLen;
        int yMvBlockCount = (height + mvBlkLen - 1) / mvBlkLen;

        int generalX = 0;
        int generalY = 0;
        int count = 0;
        if (previousYChannel != null) {
            for (int i = 1; i < yMvBlockCount; ++i) {
                for (int j = 1; j < xMvBlockCount; ++j) {
                    if (i == 0 || i == yMvBlockCount - 1 || j == 0 || j == xMvBlockCount - 1) {
                        continue;
                    }
                    BlockUtil.getBlock(currentYChannel, 0, width * height,
                            mvBlkLen * j, mvBlkLen * i, mvBlkLen, yBlock);
                    // calculate motion vector and determine the layer the block belongs to
                    int[] mv = MotionVectorUtil.calcMotionVector(previousYChannel, yBlock,
                            mvSearchSize, mvBlkLen * j, mvBlkLen * i);

                    int diffX = mv[0] - mvBlkLen * j;
                    int diffY = mv[1] - mvBlkLen * i;
                    mvs[i * xBlockCount + j][0] = diffX;
                    mvs[i * xBlockCount + j][1] = diffY;
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
//            System.out.println("overall general x y " + generalX + " " + generalY);

//            Set<Integer> bigSet = new HashSet<>();
//            int sum = 0;
//            for (Integer key: map.keySet()) {
//                int val = map.get(key);
//                if (val > 200) {
//                    bigSet.add(key);
//                    sum += val;
//                }
//            }
//            if (bigSet.size() == 0) {
//                System.out.println("Could find big vector!");
//            } else {
            count = 0;
            for (int i = 1; i < yMvBlockCount; ++i) {
                for (int j = 1; j < xMvBlockCount; ++j) {
                    int blockIdx = i * xBlockCount + j;
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
//            }
        }
    }

    /**
     * Iteratively calculate the global mv.
     *
     * @param currentYChannel
     * @param previousYChannel
     * @param layer
     */
    public static void segmentationIterate(int[] currentYChannel, int[] previousYChannel, int[] layer) {
        int[] yBlock = new int[mvBlkLen * mvBlkLen];
        int[][] mvs = new int[blockCount][2];

        // number of motion vector blocks in x and y axis
        int xMvBlockCount = (width + mvBlkLen - 1) / mvBlkLen;
        int yMvBlockCount = (height + mvBlkLen - 1) / mvBlkLen;

        double generalX = 0;
        double generalY = 0;
        int count = 0;
        if (previousYChannel != null) {
            for (int i = 1; i < yMvBlockCount - 1; ++i) {
                for (int j = 1; j < xMvBlockCount - 1; ++j) {
                    BlockUtil.getBlock(currentYChannel, 0, width * height,
                            mvBlkLen * j, mvBlkLen * i, mvBlkLen, yBlock);
                    // calculate motion vector and determine the layer the block belongs to
                    int[] mv = MotionVectorUtil.calcMotionVector(previousYChannel, yBlock,
                            mvSearchSize, mvBlkLen * j, mvBlkLen * i);
                    if (mv != null) {
                        mvs[i * xMvBlockCount + j][0] = mv[0];
                        mvs[i * xMvBlockCount + j][1] = mv[1];

                        int diffX = mv[0] - mvBlkLen * j;
                        int diffY = mv[1] - mvBlkLen * i;

                        generalX += diffX;
                        generalY += diffY;
                        ++count;
                    } else {
                        mvs[i * xMvBlockCount + j][0] = Integer.MAX_VALUE;
                        mvs[i * xMvBlockCount + j][1] = Integer.MAX_VALUE;
                    }
                }
            }
            generalX = generalX / count;
            generalY = generalY / count;
//            System.out.print(generalX + "\t" + generalY + "\t");
            for (int i = 1; i <= 2; ++i) {
                double[] result = iterateMV(mvs, generalX, generalY, layer);
                generalX = result[0];
                generalY = result[1];
            }

            count = 0;
            for (int i = 1; i < yMvBlockCount - 1; ++i) {
                for (int j = 1; j < xMvBlockCount - 1; ++j) {

                    int blockIdx = i * xMvBlockCount + j;
                    double diffX = mvs[blockIdx][0] - mvBlkLen * j;
                    double diffY = mvs[blockIdx][1] - mvBlkLen * i;

                    int[] indices = BlockUtil.getDCTBlockIdx(j, i);
                    if (mvs[blockIdx][0] >= Integer.MAX_VALUE) {
                        for (int idx : indices) {
                            layer[idx] = 0;
                        }
                    } else{
                        if (isSimilar(diffX, diffY, generalX, generalY, 5.0)) {
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

//                    System.out.print(diffX + "\t" + diffY + "\t");
                }
//                System.out.println("");
            }
//            System.out.println(count + "\t");
        }
    }

    public static void segmentationMultiParam(int[] currentYChannel, int[] previousYChannel, int[] layer) {
        int[] yBlock = new int[mvBlkLen * mvBlkLen];
        int[][] mvs = new int[blockCount][2];

        // number of motion vector blocks in x and y axis
        int xMvBlockCount = (width + mvBlkLen - 1) / mvBlkLen;
        int yMvBlockCount = (height + mvBlkLen - 1) / mvBlkLen;

        if (previousYChannel != null) {
            for (int i = 1; i < yMvBlockCount - 1; ++i) {
                for (int j = 1; j < xMvBlockCount - 1; ++j) {
                    BlockUtil.getBlock(currentYChannel, 0, width * height,
                            mvBlkLen * j, mvBlkLen * i, mvBlkLen, yBlock);
                    // calculate motion vector and determine the layer the block belongs to
                    int[] mv = MotionVectorUtil.calcMotionVector(previousYChannel, yBlock,
                            mvSearchSize, mvBlkLen * j, mvBlkLen * i);
                    if (mv != null) {
                        mvs[i * xMvBlockCount + j][0] = mv[0];
                        mvs[i * xMvBlockCount + j][1] = mv[1];
                    } else {
                        mvs[i * xMvBlockCount + j][0] = Integer.MAX_VALUE;
                        mvs[i * xMvBlockCount + j][1] = Integer.MAX_VALUE;
                    }
                }
            }
            double[] params = initGlobalMV(mvs);
//            System.out.print(params[4] + "\t");
            for (int i = 2; i <= 3; ++i) {
                params = iterateMV(mvs, params[0], params[1], params[2], params[3], 5.0);
//                System.out.print(params[4] + "\t");
            }

            int count = 0;
            for (int i = 1; i < yMvBlockCount - 1; ++i) {
                for (int j = 1; j < xMvBlockCount - 1; ++j) {
                    if (mvs[i * xMvBlockCount + j][0] >= Integer.MAX_VALUE) {
                        continue;
                    }
                    int vx = mvs[i * xMvBlockCount + j][0];
                    int vy = mvs[i * xMvBlockCount + j][1];
                    int sx = mvBlkLen * j;
                    int sy = mvBlkLen * i;
                    int[] indices = BlockUtil.getDCTBlockIdx(j, i);

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
            System.out.println(count);
        }

    }

    public static double[] initGlobalMV(int[][] mvs) {
        return iterateMV(mvs, 0, 0, 0, 0, Integer.MAX_VALUE);
    }

    public static double[] iterateMV(int[][] mvs, double kx, double bx, double ky, double by, double lenThreshold) {
        int count = 0;
        int xMvBlockCount = (width + mvBlkLen - 1) / mvBlkLen;
        int yMvBlockCount = (height + mvBlkLen - 1) / mvBlkLen;

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

    public static boolean isSimilar(double kx, double bx, double ky, double by,
                                    int vx, int vy, int sx, int sy, double lenThreshold) {
        double diffX = vx - kx * sx - bx;
        double diffY = vy - ky * sy - by;
        double distance = Math.pow(diffX, 2) + Math.pow(diffY, 2);
        return distance < lenThreshold;
    }

    public static double[] iterateMV(int[][] mvs, double globalX, double globalY, int[] layer) {
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

    public static boolean isSimilar(double diffX, double diffY,
                                    double generalX, double generalY, double lenThreshold) {
        double lenDiff = Math.pow(diffX - generalX, 2) + Math.pow(diffY - generalY, 2);

        return lenDiff < lenThreshold;
    }

    /**
     * Justify the layer the block belongs to via its motion vector.
     */
    public void segmentationThreshold(int[] currentYChannel, int[] previousYChannel, int[] layer) {
        int[] yBlock = new int[mvBlkLen * mvBlkLen];
        // number of motion vector blocks in x and y axis
        int xMvBlockCount = (width + mvBlkLen - 1) / mvBlkLen;
        int yMvBlockCount = (height + mvBlkLen - 1) / mvBlkLen;

        if (previousYChannel != null) {
            for (int i = 0; i < yMvBlockCount; ++i) {
                for (int j = 0; j < xMvBlockCount; ++j) {
                    BlockUtil.getBlock(currentYChannel, 0, width * height,
                            mvBlkLen * j, mvBlkLen * i, mvBlkLen, yBlock);
                    // TODO
                    // calculate motion vector and determine the layer the block belongs to
                    int[] mv = MotionVectorUtil.calcMotionVector(previousYChannel, yBlock,
                            mvSearchSize, mvBlkLen * j, mvBlkLen * i);

                    double diffX = Math.pow(mv[0] - mvBlkLen * j, 2);
                    double diffY = Math.pow(mv[1] - mvBlkLen * i, 2);
                    int[] indices = BlockUtil.getDCTBlockIdx(j, i);
                    if (i == 0 || i == yMvBlockCount - 1 || j == 0 || j == xMvBlockCount - 1) {
                        for (int idx : indices) {
                            layer[idx] = 0;
                        }
                    } else {
                        if (diffX + diffY > 6) {
                            for (int idx : indices) {
                                layer[idx] = 1;
                            }
                        } else {
                            for (int idx : indices) {
                                layer[idx] = 0;
                            }
                        }
                    }

//                    System.out.print(((mv[0] - mvBlkLen * j) * (mv[0] - mvBlkLen * j) + (mv[1] - mvBlkLen * i) * (mv[1] - mvBlkLen * i)) + " ");
//                    System.out.print((mv[0] - mvBlkLen * j) + " " + (mv[1] - mvBlkLen * i) + " ");
//                    if (mv[0] != mvBlkLen * j || mv[1] != mvBlkLen * i) {
//                        double distance = Math.pow(mv[0] - mvBlkLen * j, 2) + Math.pow(mv[1] - mvBlkLen * i, 2);
//                        System.out.println("(x, y): (" + mvBlkLen * j + "," + mvBlkLen * i + ") " +
//                                "vector: (" + mv[0] + ", " + mv[1] + ")" + " distance: " + distance);
//                        count++;
//                    }

                }
//                System.out.println();
            }
        }
    }

}
