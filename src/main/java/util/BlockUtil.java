package util;

import conf.Configuration;
import exec.Decoder;

import java.util.*;

/**
 * Created by yanliw on 17-4-21.
 */
public class BlockUtil {

    public static int height = Configuration.HEIGHT;

    public static int width = Configuration.WIDTH;

    public static int dctBlkLen = Configuration.DCT_BLOCK_LEN;

    public static int channelNum = Configuration.CHANNEL_NUM;

    /**
     * Partition the RGB frame into blocks and convert the byte frame data to integer at the same time.
     *
     * @param blocks the blocks buffer into which the frame is divided
     */
    public static void partitionFrame(byte[] frame, int[][] blocks) {
        // number of block in x and y axis
        int xBlockCount = (width + dctBlkLen - 1) / dctBlkLen;
        int yBlockCount = (height + dctBlkLen - 1) / dctBlkLen;
        // for R G B channel
        for (int channel = 0; channel < channelNum; ++channel) {
            // starting block index in 2-D blocks array
            int channelBlkIdxBase = channel * (xBlockCount * yBlockCount);
            // starting original data index in frame
            int channelValIdxBase = channel * (width * height);
            // from upper left to lower right
            for (int ythBlock = 0; ythBlock < yBlockCount; ++ythBlock) {
                for (int xthBlock = 0; xthBlock < xBlockCount; ++xthBlock) {
                    int blockIdx = (ythBlock * xBlockCount + xthBlock) + channelBlkIdxBase;
                    // for each block
                    for (int row = 0; row < dctBlkLen; ++row) {
                        for (int col = 0; col < dctBlkLen; ++col) {
                            // index in 1-D block array
                            int valIdx = row * dctBlkLen + col;
                            int originalIdxOffset = getOffset(xthBlock, ythBlock, row, col);
                            // when the index is beyond the border fill the block with 0
                            if (originalIdxOffset >= width * height) {
                                blocks[blockIdx][valIdx] = 0;
                            } else {
                                // convert the byte data into integer using a mask
                                blocks[blockIdx][valIdx] = frame[originalIdxOffset + channelValIdxBase]
                                        & Configuration.BYTE_MASK;
                            }
                        }
                    }
                }
            }
        }
    }

    public static int getOffset(int xthBlock, int ythBlock, int row, int col) {
        int base = ythBlock * (width * dctBlkLen) + xthBlock * (dctBlkLen);
        int offset = base + row * width + col;
        return offset;
    }

    /**
     * Get multiple blocks from the frame.
     */
    public static void getBlocks(byte[] frame, int[] baseIndices, int limit,
                                 int x, int y, int blockLen, int[][] blocks) {
        for (int i = 0; i < baseIndices.length; ++i) {
            getBlock(frame, baseIndices[i], limit, x, y, blockLen, blocks[i]);
        }
    }

    /**
     * Get one block from the frame.
     *
     * @param frame    the frame we are extracting block from
     * @param baseIdx  base index for R (or G or B) component data in the frame
     * @param limit    currently unused
     * @param x        row offset of block in the component
     * @param y        column offset of block in the component
     * @param blockLen the size of block
     * @param block    the specified block we will put data into
     */
    public static void getBlock(byte[] frame, int baseIdx, int limit,
                                int x, int y, int blockLen, int[] block) {
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

    public static void fillBlockInFrame(int idx, byte[] frame, int blockIdx, int[] block) {
        int xBlockCount = (width + dctBlkLen - 1) / dctBlkLen;
        int ythBlock = blockIdx / xBlockCount;
        int xthBlock = blockIdx % xBlockCount;

        for (int k = 0; k < Configuration.CHANNEL_NUM; ++k) {
            int base = (width * height * k) + (width * dctBlkLen * ythBlock + dctBlkLen * xthBlock);
            for (int i = 0; i < dctBlkLen; ++i) {
                for (int j = 0; j < dctBlkLen; ++j) {
                    int frameIdx = base + (i * width + j);
                    int dataIdx = dctBlkLen * dctBlkLen * k + dctBlkLen * i + j;

                    if (frameIdx < width * height * (k + 1)) {
                        frame[frameIdx] = (byte) block[dataIdx];
                    }
                }
            }
        }
    }

    public static void fillFrame(int idx, byte[] frame, int[][] blocks, short[] layer, byte[] lastFrame) {
        int xBlockCount = (width + dctBlkLen - 1) / dctBlkLen;

        for (int blockIdx = 0; blockIdx < blocks.length; ++blockIdx) {
            int ythBlock = blockIdx / xBlockCount;
            int xthBlock = blockIdx % xBlockCount;

            for (int k = 0; k < Configuration.CHANNEL_NUM; ++k) {
                int base = (width * height * k) + (width * dctBlkLen * ythBlock + dctBlkLen * xthBlock);
                for (int i = 0; i < dctBlkLen; ++i) {
                    for (int j = 0; j < dctBlkLen; ++j) {
                        int frameIdx = base + (i * width + j);
                        int dataIdx = dctBlkLen * dctBlkLen * k + dctBlkLen * i + j;
                        if (layer[blockIdx] == 0 && idx % Configuration.PASS_FRAME_RATE != 0 &&
                                Decoder.isAroundBackground(blockIdx, layer)) {
                            continue;
                        } else {
                            if (frameIdx < width * height * (k + 1)) {
                                frame[frameIdx] = (byte) blocks[blockIdx][dataIdx];
                            }
                        }
                    }
                }
            }
        }
    }
    public static void getBlock(int[] data, int baseIdx, int limit,
                                int x, int y, int blockLen, int[] block) {
        for (int i = 0; i < blockLen; ++i) {
            for (int j = 0; j < blockLen; ++j) {
                if (x + j >= width || y + i >= height) {
                    block[i * blockLen + j] = 0;
                } else {
                    block[i * blockLen + j] = data[baseIdx + (y + i) * width + (x + j)];
                }
            }
        }
    }

    public static int[] getDCTBlockIdx(int x, int y) {
        int xBlockCount = (width + dctBlkLen - 1) / dctBlkLen;
//        int yBlockCount = (height + dctBlkLen - 1) / dctBlkLen;
//        int[] indices = new int[4];
        int base = (xBlockCount * 2) * y + (2 * x);

        return new int[]{base, base + 1, base + xBlockCount, base + xBlockCount + 1};
    }

    public static Set<Integer> getBlockIdxAroundPoint(int x, int y, int size) {
        Set<Integer> set = new HashSet<>();
        int xBlockCount = (width + dctBlkLen - 1) / dctBlkLen;
        int prevMinX = Math.max(x - size, 0);
        int prevMaxX = Math.min(x + size, width - 1);
        int prevMinY = Math.max(y - size, 0);
        int prevMaxY = Math.min(y + size, height - 1);

        for (int i = prevMinY; i <= prevMaxY; i += dctBlkLen) {
            int ythBlock = i / dctBlkLen;
            for (int j = prevMinX; j <= prevMaxX; j += dctBlkLen) {
                int xthBlock = j / dctBlkLen;
                int idx = ythBlock * xBlockCount + xthBlock;
                set.add(idx);
            }
        }
        return set;
    }

    public static int getBlockIdxFromPoint(int x, int y) {
        int xBlockCount = (width + dctBlkLen - 1) / dctBlkLen;

        int ythBlock = y / dctBlkLen;
        int xthBlock = x / dctBlkLen;

        return ythBlock * xBlockCount + xthBlock;
    }
}
