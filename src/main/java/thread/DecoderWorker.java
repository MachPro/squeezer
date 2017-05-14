package thread;

import conf.Configuration;
import exec.Decoder;
import util.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Decoder thread which completes most decoding job.
 * <p/>
 * Created by MachPro on 17-5-4.
 */
public class DecoderWorker implements Runnable {

    private int width = Configuration.WIDTH;

    private int height = Configuration.HEIGHT;

    private int dctBlkLen = Configuration.DCT_BLOCK_LEN;

    private int dctBlockCount = ((width + dctBlkLen - 1) / dctBlkLen) *
            ((height + dctBlkLen - 1) / dctBlkLen);

    private short[] layer = new short[dctBlockCount];

    private int[][] dctCof = new int[dctBlockCount][dctBlkLen * dctBlkLen * 3];
    // decoded rgb frame
    private byte[] displayFrame = new byte[Configuration.FRAME_BYTE_LEN];
    // the background blocks which will not be calculated during iDCT process
    private Set<Integer> backgrounds = new HashSet<>();
    // whether the process of one-frame decoding is completed
    private boolean frameAlready = false;

    private int frameIdx;

    private InputStream is;

    private int frameCount;

    public DecoderManager manager;

    public boolean shouldStart;

    public DecoderWorker(InputStream is, int frameCount) {
        this.is = is;
        this.frameCount = frameCount;
    }

    public void setFrameIdx(int idx) {
        this.frameIdx = idx;
    }

    public byte[] getDisplayFrame() {
        return this.displayFrame;
    }

    public int[][] getDctCof() {
        return this.dctCof;
    }

    public boolean isFrameAlready() {
        return this.frameAlready;
    }

    public Set<Integer> getBackgrounds() {
        return this.backgrounds;
    }

    public short[] getLayer() {
        return this.layer;
    }

    @Override
    public void run() {
        int[][] rgbVal = new int[dctBlockCount][dctBlkLen * dctBlkLen * 3];

        while (frameIdx < frameCount) {
            // clear data for last frame
            backgrounds.clear();
            frameAlready = false;
            read();
            doDecode(layer, dctCof, rgbVal);
            BlockUtil.fillFrame(frameIdx, displayFrame, rgbVal, layer);
            frameAlready = true;
            synchronized (this) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Read one frame from the compressed file and store the coefficients.
     */
    private void read() {
        try {
            byte[] buf;
            // read frame data from compressed file
            synchronized (is) {
                // the length of one frame data
                int frameLen = readFrameLen(is);
                buf = new byte[frameLen];

                int offset = 0;
                int numRead;
                while (offset < buf.length
                        && (numRead = is.read(buf, offset, buf.length - offset)) >= 0) {
                    offset += numRead;
                }
            }

            int bufIdx = 0;
            int blockIdx = 0;
            while (blockIdx < dctBlockCount) {
                layer[blockIdx] = buf[bufIdx];
                ++bufIdx;

                int cofIdx = 0;
                while (cofIdx < dctCof[blockIdx].length) {
                    if (buf[bufIdx] == 0) {
                        ++bufIdx;
                        int consecutiveZero = buf[bufIdx];
                        for (int i = 0; i < consecutiveZero; ++i) {
                            dctCof[blockIdx][cofIdx] = 0;
                            ++cofIdx;
                        }
                    } else {
                        dctCof[blockIdx][cofIdx] = buf[bufIdx];
                        ++cofIdx;
                    }
                    ++bufIdx;
                }
                ++blockIdx;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int readFrameLen(InputStream is) throws IOException {
        int offset = 0;
        int numRead;
        byte[] buf = new byte[4];
        while (offset < buf.length
                && (numRead = is.read(buf, offset, buf.length - offset)) >= 0) {
            offset += numRead;
        }
        return ByteUtil.byteToInt(buf,0, 4);
    }

    /**
     * Decode the current frame and do iDCT to covert the coefficients to rgb value.
     */
    private void doDecode(short[] layers, int[][] dctCof, int[][] rgbVal) {
        for (int i = 0; i < layers.length; ++i) {
            int factor = (layers[i] != 0) ? Configuration.FORE_GROUND_QUANTIZATION_FACTOR :
                    Configuration.BACK_GROUND_QUANTIZATION_FACTOR;
            // do not repaint the background every frame
            if (layer[i] == 0 && frameIdx % Configuration.BACKGROUND_REPAINT_RATE != 0) {
                // ignore these background blocks if their neighbors are all background blocks
                if (DecodeUtil.isAroundAllBackground(i, layers)) {
                    backgrounds.add(i);
                    continue;
                }
            }
            // for R G B channel
            for (int j = 0; j < Configuration.CHANNEL_NUM; ++j) {
                int idxBase = dctBlkLen * dctBlkLen * j;

                int[] oneDDCT = Arrays.copyOfRange(dctCof[i], idxBase, idxBase + dctBlkLen * dctBlkLen);
                QuantizationUtil.dequantize(oneDDCT, factor);
                DCTUtil.doAANiDCT(oneDDCT);
                ArrayUtil.fill(oneDDCT, rgbVal[i], idxBase);
            }
        }
    }
}
