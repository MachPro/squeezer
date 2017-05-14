package util;

import conf.Configuration;

/**
 * Created by MachPro on 5/14/17.
 */
public class DecodeUtil {

    private static int width = Configuration.WIDTH;

    private static int height = Configuration.HEIGHT;

    private static int dctBlkLen = Configuration.DCT_BLOCK_LEN;

    /**
     * Determine whether one block has all its neighbors background block.
     *
     * @param blockIdx the index of block we are going to determine
     * @param layers   foreground and background coefficients for all blocks
     */
    public static boolean isAroundAllBackground(int blockIdx, short[] layers) {
        // blocks count in horizontal and vertical axis
        int xBlockCount = (width + dctBlkLen - 1) / dctBlkLen;
        int yBlockCount = (height + dctBlkLen - 1) / dctBlkLen;

        int xBlockOffset = blockIdx % xBlockCount;
        int yBlockOffset = blockIdx / xBlockCount;
        if (xBlockOffset - 1 >= 0 && layers[blockIdx - 1] == 1) {
            return false;
        }
        if (xBlockOffset + 1 < xBlockCount && layers[blockIdx + 1] == 1) {
            return false;
        }
        if (yBlockOffset - 1 >= 0 && layers[blockIdx - xBlockCount] == 1) {
            return false;
        }
        if (yBlockOffset + 1 < yBlockCount && layers[blockIdx + xBlockCount] == 1) {
            return false;
        }
        return true;
    }
}
