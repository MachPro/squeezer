package exec;

import conf.Configuration;
import util.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Created by yanliw on 17-4-20.
 */
public class Encoder {

    public static int width = Configuration.WIDTH;

    public static int height = Configuration.HEIGHT;

    public static int frameByteLen = Configuration.FRAME_BYTE_LEN;

    public static int dctBlkLen = Configuration.DCT_BLOCK_LEN;

    // number of dct blocks in one channel of frame
    public static int blockCount = ((width + dctBlkLen - 1) / dctBlkLen) *
            ((height + dctBlkLen - 1) / dctBlkLen);

    public int currentFrameIdx = 0;

    public static void main(String[] args) {
        Encoder encoder = new Encoder();
        encoder.run(args[0]);
    }

    /**
     * Get input before encoding and output encoding result to disk.
     * The actual encoding work will be completed in doEncode function.
     */
    public void run(String inputFilename) {
        File infile = new File(inputFilename);
        File outfile = new File(Configuration.DCT_OUTPUT_FILENAME);

        // number of frames
        int frameCount = (int) (infile.length() / frameByteLen);
        // buffer into which the data is loaded from disk
        byte[] frame = new byte[frameByteLen];
        // the DCT values for every block
        int[][] output = new int[blockCount][dctBlkLen * dctBlkLen * 3];

        int[] layer = new int[blockCount];

//        System.out.println(blockCount * dctBlkLen * dctBlkLen + " DCT blocks per frame");
        System.out.println(frameCount + " frames");
        long begin = System.currentTimeMillis();
        System.out.println("begin exec at: " + begin);
        try (InputStream is = new FileInputStream(infile);
             FileOutputStream out = new FileOutputStream(outfile);
             FileChannel fc = out.getChannel()) {
            // buffer for DCT values
            ByteBuffer buf = ByteBuffer.allocate(4 * blockCount * dctBlkLen * dctBlkLen * 3 + 1);
            int[] previousYChannel = null;
            // for each frame
            while (currentFrameIdx < frameCount) {
                // load data from disk
                read(is, frame);
                previousYChannel = doEncode(frame, previousYChannel, layer, output);
                // clear buffer and put output into the buffer
                buf.clear();
                for (int j = 0; j < blockCount; ++j) {
                    buf.putShort((short) layer[j]);
                    for (int k = 0; k < dctBlkLen * dctBlkLen * 3; ++k) {
                        buf.putShort((short) output[j][k]);
                    }
                }
                buf.flip();
                // write to the disk
                while (buf.hasRemaining()) {
                    fc.write(buf);
                }
                ++currentFrameIdx;
//                System.out.println(currentFrameIdx);
            }
            System.out.println("write to " + Configuration.DCT_OUTPUT_FILENAME + " succeed");
            long end = System.currentTimeMillis();
            System.out.println("finish at:" + end + " last for " + (end - begin));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Partition frame, transform color space and calculate DCT value.
     *
     * @param frame  original data from disk
     * @param blocks blocks the original data will be divided into
     * @param output into which the DCT value will be store
     */
    public void doEncode(byte[] frame, int[][] blocks, int[][] output) {
        BlockUtil.partitionFrame(frame, blocks);
        for (int j = 0; j < blockCount; ++j) {
            // convert RGB to YCbCr color space and store the Y channel in blocks array
            TransformUtil.RGBToY(blocks[j], blocks[j + blockCount], blocks[j + blockCount * 2], blocks[j]);
            int[] dctVals = DCTUtil.do2DDCTWithAAN(blocks[j]);
            output[j] = dctVals;
        }
    }

    /**
     * Calculate and encode DCT value and then break frame into layers via motion vector.
     *
     * @param currentFrame     current frame we are dealing with
     * @param previousYChannel Y channel of previous frame
     * @param layer            background and foreground layers which every block belongs to
     * @param output           into which the DCT value will be store
     * @return the Y channel of current frame
     */
    public int[] doEncode(byte[] currentFrame, int[] previousYChannel, int[] layer, int[][] output) {
        doEncodeDCTBlock(currentFrame, output);

        int[] currentYChannel = new int[width * height];
        // transform the current frame into Y channel
        for (int i = 0; i < currentYChannel.length; ++i) {
            currentYChannel[i] = TransformUtil.RGBToY(currentFrame[i],
                    currentFrame[i + width * height], currentFrame[i + width * height * 2]);
        }
//        SegmentationUtil.segmentationMostMV(currentYChannel, previousYChannel, layer);
        SegmentationUtil.segmentationIterate(currentYChannel, previousYChannel, layer);
//        SegmentationUtil.segmentationMultiParam(currentYChannel, previousYChannel, layer);
        return currentYChannel;
    }

    /**
     * Get block from the frame, calculate the DCT value for R G B channel
     * and put these values into an array.
     *
     * @param frame  the frame we are dealing with
     * @param output where these DCT values are stored
     */
    public void doEncodeDCTBlock(byte[] frame, int[][] output) {
        // number of DCT blocks in x and y axis
        int xDctBlockCount = (width + dctBlkLen - 1) / dctBlkLen;
        int yDctBlockCount = (height + dctBlkLen - 1) / dctBlkLen;

        int[][] rgbBlock = new int[3][dctBlkLen * dctBlkLen];
        // base index in frame to get R G B component
        int[] baseIndices = {0, width * height, width * height * 2};
        int[][] dctVals = new int[3][];

        for (int i = 0; i < yDctBlockCount; ++i) {
            for (int j = 0; j < xDctBlockCount; ++j) {
                // get R G B blocks
                BlockUtil.getBlocks(frame, baseIndices, width * height,
                        dctBlkLen * j, dctBlkLen * i, dctBlkLen, rgbBlock);
                // for R G B channel
                for (int k = 0; k < dctVals.length; ++k) {
                    // calculate the DCT value for each channel
                    dctVals[k] = DCTUtil.do2DDCTWithAAN(rgbBlock[k]);
                    // put R G B DCT values into one array
                    ArrayUtil.fill(dctVals[k], output[i * xDctBlockCount + j],
                            dctBlkLen * dctBlkLen * k);
                }
            }
        }
    }

    /**
     * Read up to <code>buf.length</code> bytes of data from input stream into a byte array.
     *
     * @param buf the buffer into which the frame data is read
     */
    public void read(InputStream is, byte[] buf) {
        int offset = 0;
        int numRead = 0;
        try {
            while (offset < buf.length
                    && (numRead = is.read(buf, offset, buf.length - offset)) >= 0) {
                offset += numRead;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
