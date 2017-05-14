package exec;

import conf.Configuration;
import util.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Read video file, compress it and write the compression file.
 * <p/>
 * Created by MachPro on 17-4-20.
 */
public class Encoder {

    private int width;

    private int height;

    private int dctBlkLen = Configuration.DCT_BLOCK_LEN;

    // number of dct blocks in one channel of frame
    private int blockCount;

    /**
     * Usage: filePath width height
     */
    public static void main(String[] args) {
        Encoder encoder = new Encoder();
        encoder.processArgs(args);
        encoder.run(args[0]);
    }

    private void processArgs(String[] args) {
        int fileStartIdx = args[0].lastIndexOf('/');
        int fileEndIdx = args[0].lastIndexOf('.');

        String fileName;
        if (fileStartIdx < 0) {
            if (fileEndIdx < 0) {
                fileName = args[0];
            } else {
                fileName = args[0].substring(0, fileEndIdx);
            }
        } else {
            if (fileEndIdx < 0) {
                fileName = args[0].substring(fileStartIdx + 1);
            } else {
                fileName = args[0].substring(fileStartIdx + 1, fileEndIdx);
            }
        }
        Configuration.CMP_FILENAME = fileName + ".cmp";
        if (args.length > 1) {
            Configuration.WIDTH = Integer.parseInt(args[1]);
        }
        if (args.length > 2) {
            Configuration.HEIGHT = Integer.parseInt(args[2]);
        }
        this.width = Configuration.WIDTH;
        this.height = Configuration.HEIGHT;
        this.blockCount = ((width + dctBlkLen - 1) / dctBlkLen) *
                ((height + dctBlkLen - 1) / dctBlkLen);
    }

    /**
     * Get input before encoding.
     * The actual encoding work will be completed in doEncode function.
     */
    private void run(String inputFilename) {
        File infile = new File(inputFilename);
        File outfile = new File(Configuration.CMP_FILENAME);

        // number of frames
        int frameCount = (int) (infile.length() / Configuration.FRAME_BYTE_LEN);
        // buffer into which the data is loaded from disk
        byte[] frame = new byte[Configuration.FRAME_BYTE_LEN];
        // the DCT values for every block
        int[][] output = new int[blockCount][dctBlkLen * dctBlkLen * 3];
        // layer for every block
        int[] layer = new int[blockCount];

        System.out.println(frameCount + " frames");
        long begin = System.currentTimeMillis();
        System.out.println("begin exec at: " + begin);
        try (InputStream is = new FileInputStream(infile);
             FileOutputStream out = new FileOutputStream(outfile);
             FileChannel fc = out.getChannel()) {

            // buffer for DCT values
            // 4 byte for frame length
            // for every block one byte for layer and at most two bytes for each DCT value
            ByteBuffer buf = ByteBuffer.allocate(4 + blockCount *
                    (1 + 2 * dctBlkLen * dctBlkLen * Configuration.CHANNEL_NUM));
            // write # of frame
            writeMetaData(fc, buf, frameCount);
            // Y channel for last frame
            int[] previousYChannel = null;
            int currentFrameIdx = 0;
            // for each frame
            while (currentFrameIdx < frameCount) {
                // load data from disk
                read(is, frame);
                // Y channel of current frame will be returned
                previousYChannel = doEncode(frame, previousYChannel, layer, output);
                // write encoding result of one frame to compressed file
                write(layer, output, fc, buf);
                ++currentFrameIdx;
//                System.out.println(currentFrameIdx);
            }
            System.out.println("write to " + Configuration.CMP_FILENAME + " succeed");
            long end = System.currentTimeMillis();
            System.out.println("finish at:" + end + " last for " + (end - begin));
        } catch (IOException e) {
            e.printStackTrace();
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
    private int[] doEncode(byte[] currentFrame, int[] previousYChannel, int[] layer, int[][] output) {
        int[] currentYChannel = new int[width * height];
        // transform the current frame into Y channel
        for (int i = 0; i < currentYChannel.length; ++i) {
            currentYChannel[i] = TransformUtil.RGBToY(currentFrame[i],
                    currentFrame[i + width * height], currentFrame[i + width * height * 2]);
        }
        // segment blocks into background and foreground
        SegmentationUtil.segmentationIterate(currentYChannel, previousYChannel, layer);
        // encode blocks
        doEncodeDCTBlock(currentFrame, layer, output);

        return currentYChannel;
    }

    /**
     * Get block from the frame, calculate the DCT value for R G B channel
     * and put these values into an array.
     *
     * @param frame  the frame we are dealing with
     * @param output where these DCT values are stored
     */
    private void doEncodeDCTBlock(byte[] frame, int[] layer, int[][] output) {
        // number of DCT blocks in x and y axis
        int xDctBlockCount = (width + dctBlkLen - 1) / dctBlkLen;
        int yDctBlockCount = (height + dctBlkLen - 1) / dctBlkLen;

        int[][] rgbBlock = new int[3][dctBlkLen * dctBlkLen];
        // base index in frame to get R G B component
        int[] baseIndices = {0, width * height, width * height * 2};
        int[] dctVals;

        for (int i = 0; i < yDctBlockCount; ++i) {
            for (int j = 0; j < xDctBlockCount; ++j) {
                int blockIdx = i * xDctBlockCount + j;
                // get R G B blocks
                BlockUtil.getBlocks(frame, baseIndices,
                        dctBlkLen * j, dctBlkLen * i, dctBlkLen, rgbBlock);
                // for R G B channel
                for (int k = 0; k < rgbBlock.length; ++k) {
                    // calculate the DCT value for each channel
                    dctVals = DCTUtil.do2DDCTWithAAN(rgbBlock[k]);
                    // quantize DCT values
                    if (layer[blockIdx] == 0) {
                        QuantizationUtil.quantize(dctVals, Configuration.BACK_GROUND_QUANTIZATION_FACTOR);
                    } else {
                        QuantizationUtil.quantize(dctVals, Configuration.FORE_GROUND_QUANTIZATION_FACTOR);
                    }
                    // put R G B DCT values into one array
                    ArrayUtil.fill(dctVals, output[i * xDctBlockCount + j],
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
    private void read(InputStream is, byte[] buf) {
        int offset = 0;
        int numRead;
        try {
            while (offset < buf.length
                    && (numRead = is.read(buf, offset, buf.length - offset)) >= 0) {
                offset += numRead;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Write some meta data. Here write the number of frames to the head of compressed file.
     *
     * @param fc         file channel to the compressed file
     * @param buf        byte buffer to store meta data
     * @param frameCount meta data here is the number of frames
     */
    private void writeMetaData(FileChannel fc, ByteBuffer buf, int frameCount) throws IOException {
        buf.clear();
        buf.putInt(frameCount);
        buf.flip();
        while (buf.hasRemaining()) {
            fc.write(buf);
        }
    }

    /**
     * Write encoding result to compressed file.
     */
    private void write(int[] layer, int[][] output, FileChannel fc, ByteBuffer buf) throws IOException {
        buf.clear();
        // length in byte for this frame after compression
        int frameLen = 0;
        // put a 4 byte placeholder here
        buf.putInt(frameLen);
        for (int i = 0; i < output.length; ++i) {
            // layer for this block
            buf.put((byte) layer[i]);
            ++frameLen;
            int j = 0;
            int consecutiveZero = 0;
            while (j < output[i].length) {
                if (output[i][j] == 0) {
                    // write (0, consecutive 0 count)
                    int k = j;
                    while (k < output[i].length && output[i][k] == 0) {
                        ++consecutiveZero;
                        ++k;
                    }
                    if (consecutiveZero >= 128) {
                        buf.put((byte) 0);
                        buf.put((byte) 127);
                        buf.put((byte) 0);
                        buf.put((byte) (consecutiveZero - 127));
                        frameLen += 4;
                    } else {
                        buf.put((byte) 0);
                        buf.put((byte) consecutiveZero);
                        frameLen += 2;
                    }
                    consecutiveZero = 0;
                    j = k;
                } else {
                    // write DCT value
                    buf.put((byte) output[i][j]);
                    ++frameLen;
                    ++j;
                }
            }
        }
        // put the length at the first place
        buf.putInt(0, frameLen);
        buf.flip();
        // write to disk
        while (buf.hasRemaining()) {
            fc.write(buf);
        }
    }
}
