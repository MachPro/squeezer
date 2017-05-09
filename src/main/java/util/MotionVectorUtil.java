package util;

import conf.Configuration;

/**
 * Created by MachPro on 17-4-23.
 */
public class MotionVectorUtil {

    public static int width = Configuration.WIDTH;

    public static int height = Configuration.HEIGHT;

    public static int mvBlkLen = Configuration.MOTION_VECTOR_BLOCK_LEN;

    /**
     * Motion vector calculation based on brute force search.
     *
     * @param prevYChannel Y channel in previous frame
     * @param block        the block we are going to calculate motion vector for
     * @param k            the size of search
     * @param x            row offset of block in frame
     * @param y            col offset of block in frame
     * @return motion vector of this block
     */
    public static int[] calcMotionVector(int[] prevYChannel, int[] block, int k, int x, int y) {
        // the range of search
        int prevMinX = Math.max(x - k, 0);
        int prevMaxX = Math.min(x + k, width - mvBlkLen - 1);
        int prevMinY = Math.max(y - k, 0);
        int prevMaxY = Math.min(y + k, height - mvBlkLen - 1);

        int[] prevBlock = new int[mvBlkLen * mvBlkLen];
        int[] motionVector = new int[2];
        int minDiff = Integer.MAX_VALUE;
        // for every block in the range
        for (int i = prevMinY; i <= prevMaxY; ++i) {
            for (int j = prevMinX; j <= prevMaxX; ++j) {
                // get block from previous Y channel
                BlockUtil.getBlock(prevYChannel, 0,
                        j, i, mvBlkLen, prevBlock);
                int diff = getSAD(prevBlock, block);
                if (diff < minDiff) {
                    minDiff = diff;
                    motionVector[0] = j;
                    motionVector[1] = i;
                }
            }
        }
        // if the difference between block is too large
        if (minDiff > 6000 || minDiff < 180) {
            return null;
        }
        return motionVector;
    }

    /**
     * Get the Sum of Absolute Difference for given blocks.
     */
    public static int getSAD(int[] prevBlock, int[] currentBlock) {
        int diff = 0;
        for (int i = 0; i < prevBlock.length; ++i) {
            diff += Math.abs(prevBlock[i] - currentBlock[i]);
        }
        return diff;
    }

    /**
     * Get the Sum of Square Difference for given blocks.
     */
    public static int getSSD(int[] prevBlock, int[] currentBlock) {
        int diff = 0;
        for (int i = 0; i < prevBlock.length; ++i) {
            diff += Math.pow(currentBlock[i] - prevBlock[i], 2);
        }
        return diff;
    }

    // TODO faster search way
}
