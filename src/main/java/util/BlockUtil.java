package util;

import conf.Configuration;
import exec.Decoder;

import java.util.HashSet;
import java.util.Set;

/**
 * Block related utility functions.
 * <br/>
 * To extract block, fill block, get block around point and so on.
 * <p/>
 * Created by MachPro on 17-4-21.
 */
public class BlockUtil {

    public static int height = Configuration.HEIGHT;

    public static int width = Configuration.WIDTH;

    public static int dctBlkLen = Configuration.DCT_BLOCK_LEN;
    // number of DCT block in x (horizon) and y (vertical) axis
    public static int xBlockCount = (width + dctBlkLen - 1) / dctBlkLen;

    /**
     * Extract multiple blocks from the frame.
     */
    public static void getBlocks(byte[] frame, int[] baseIndices, int x, int y,
                                 int blockLen, int[][] blocks) {
        for (int i = 0; i < baseIndices.length; ++i) {
            getBlock(frame, baseIndices[i], x, y, blockLen, blocks[i]);
        }
    }

    /**
     * Extract one block from the frame.
     *
     * @param frame    the frame we are extracting block from
     * @param baseIdx  base index for R (or G or B) component data in the frame
     * @param x        row offset of block in the component
     * @param y        column offset of block in the component
     * @param blockLen the size of block
     * @param block    the specified block we will put data into
     */
    public static void getBlock(byte[] frame, int baseIdx, int x, int y,
                                int blockLen, int[] block) {
        for (int i = 0; i < blockLen; ++i) {
            for (int j = 0; j < blockLen; ++j) {
                if (x + j >= width || y + i >= height) {
                    block[i * blockLen + j] = 0;
                } else {
                    block[i * blockLen + j] = frame[baseIdx + (y + i) * width + (x + j)]
                            & Configuration.BYTE_MASK;
                }
            }
        }
    }

    /**
     * Extract one block from the channel.
     *
     * @param channel  channel data to extract block from
     * @param baseIdx  in this case baseIdx is always 0
     * @param x        x (horizontal) offset of block in the channel
     * @param y        y (vertical) offset of block in the channel
     * @param blockLen size of block
     * @param block    buffer to store the block
     */
    public static void getBlock(int[] channel, int baseIdx, int x, int y,
                                int blockLen, int[] block) {
        for (int i = 0; i < blockLen; ++i) {
            for (int j = 0; j < blockLen; ++j) {
                if (x + j >= width || y + i >= height) {
                    block[i * blockLen + j] = 0;
                } else {
                    block[i * blockLen + j] = channel[baseIdx + (y + i) * width + (x + j)];
                }
            }
        }
    }

    /**
     * Fill the specified block of current frame using the same color in last frame.
     */
    public static void fillBlockInFrame(byte[] currentFrame, int blockIdx, byte[] lastFrame) {
        // offset in both x and y axis
        int yOffset = blockIdx / xBlockCount;
        int xOffset = blockIdx % xBlockCount;
        // r g b channel
        for (int k = 0; k < Configuration.CHANNEL_NUM; ++k) {
            // channel base index in frame
            int base = (width * height * k) + (width * dctBlkLen * yOffset + dctBlkLen * xOffset);
            for (int i = 0; i < dctBlkLen; ++i) {
                for (int j = 0; j < dctBlkLen; ++j) {
                    int idx = base + (i * width + j);

                    if (idx < width * height * (k + 1)) {
                        currentFrame[idx] = lastFrame[idx];
                    }
                }
            }
        }
    }

    /**
     * Fill the specified block in frame with rgb values.
     *
     * @param blockIdx the index of block in the frame
     */
    public static void fillBlockInFrame(byte[] frame, int blockIdx, int[] rgbVal) {
        // offset in both x and y axis
        int yOffset = blockIdx / xBlockCount;
        int xOffset = blockIdx % xBlockCount;
        // for r g b channel
        for (int k = 0; k < Configuration.CHANNEL_NUM; ++k) {
            // channel base index in frame
            int base = (width * height * k) + (width * dctBlkLen * yOffset + dctBlkLen * xOffset);
            // for every color value
            for (int i = 0; i < dctBlkLen; ++i) {
                for (int j = 0; j < dctBlkLen; ++j) {
                    // index for this pixel
                    int pixelIdx = base + (i * width + j);
                    int dataIdx = dctBlkLen * dctBlkLen * k + dctBlkLen * i + j;

                    if (pixelIdx < width * height * (k + 1)) {
                        frame[pixelIdx] = (byte) rgbVal[dataIdx];
                    }
                }
            }
        }
    }

    /**
     * Fill one whole frame with specified rgb values.
     *
     * @param frameIdx the index of frame
     * @param rgbVal   rgb values for to be used for filling
     */
    public static void fillFrame(int frameIdx, byte[] frame, int[][] rgbVal, short[] layer) {
        for (int blockIdx = 0; blockIdx < rgbVal.length; ++blockIdx) {
            // do not paint the background whose neighbors are all background
            if (layer[blockIdx] == 0 &&
                    frameIdx % Configuration.BACKGROUND_REPAINT_RATE != 0 &&
                    DecodeUtil.isAroundAllBackground(blockIdx, layer)) {
                continue;
            } else {
                fillBlockInFrame(frame, blockIdx, rgbVal[blockIdx]);
            }
        }
    }

    /**
     * Get the indices of dct blocks in one macroblock.
     *
     * @param xOffset the offset in x (horizontal) axis for the macroblock
     * @param yOffset the offset in y (vertical) axis for the macroblock
     * @return four dct block indices
     */
    public static int[] getDCTBlockIdx(int xOffset, int yOffset) {
        int base = (xBlockCount * 2) * yOffset + (2 * xOffset);

        return new int[]{base, base + 1, base + xBlockCount, base + xBlockCount + 1};
    }

    /**
     * Get blocks around point (x, y) within the range of size.
     *
     * @param x    x-coordinate of point
     * @param y    y-coordinate of point
     * @param size the size of search block centered on this point
     * @return
     */
    public static Set<Integer> getBlockIdxAroundPoint(int x, int y, int size) {
        Set<Integer> set = new HashSet<>();
        // determine the range of search
        int minX = Math.max(x - size / 2, 0);
        int maxX = Math.min(x + size / 2, width - 1);
        int minY = Math.max(y - size / 2, 0);
        int maxY = Math.min(y + size / 2, height - 1);
        // go through the range
        for (int i = minY; i <= maxY; i += dctBlkLen) {
            int yBlockOffset = i / dctBlkLen;
            for (int j = minX; j <= maxX; j += dctBlkLen) {
                int xBlockOffset = j / dctBlkLen;
                int blockIdx = yBlockOffset * xBlockCount + xBlockOffset;
                set.add(blockIdx);
            }
        }
        return set;
    }

}
