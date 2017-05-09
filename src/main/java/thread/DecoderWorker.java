package thread;

import conf.Configuration;
import exec.Decoder;
import util.ArrayUtil;
import util.BlockUtil;
import util.DCTUtil;

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
    // buffer used to read one compressed frame
    private byte[] cmpFileFrame = new byte[2 * dctBlockCount * (dctBlkLen * dctBlkLen * 3 + 1)];
    // the background blocks which will not be calculated during iDCT process
    private Set<Integer> backgrounds = new HashSet<>();
    // whether the process of one-frame decoding is completed
    private boolean frameAlready = false;

    private int frameIdx;

    private InputStream is;

    private int N1;

    private int N2;

    private int frameCount;

    public DecoderWorker(InputStream is, int N1, int N2, int frameCount) {
        this.is = is;
        this.N1 = N1;
        this.N2 = N2;
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
            read(is, cmpFileFrame, layer, dctCof);
            doDecode(N1, N2, layer, dctCof, rgbVal);
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
     *
     * @param is     input stream to the compressed file
     * @param buf    buffer to store the file
     * @param layers layer coefficient
     * @param dctCof DCT value of rgb channel
     */
    public void read(InputStream is, byte[] buf, short[] layers, int[][] dctCof) {
        synchronized (is) {
            // read byte data from file
            try {
                int offset = 0;
                int numRead;
                while (offset < buf.length
                        && (numRead = is.read(buf, offset, buf.length - offset)) >= 0) {
                    offset += numRead;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            // convert every two byte data to one short data
            int blockIdx = 0;
            int bufIdx = 0;
            while (blockIdx < dctBlockCount) {
                // layer coefficient
                byte firstByte = buf[bufIdx];
                byte secondByte = buf[bufIdx + 1];
                bufIdx = bufIdx + 2;

                short layer = (short) ((firstByte & 0xFF) << 8 | (secondByte & 0xFF));
                layers[blockIdx] = layer;
                // DCT coefficients for rgb channel
                int cofIdx = 0;
                while (cofIdx < dctBlkLen * dctBlkLen * Configuration.CHANNEL_NUM) {
                    firstByte = buf[bufIdx];
                    secondByte = buf[bufIdx + 1];
                    bufIdx = bufIdx + 2;

                    short dctVal = (short) ((firstByte & 0xFF) << 8 | (secondByte & 0xFF));
                    dctCof[blockIdx][cofIdx] = dctVal;

                    ++cofIdx;
                }
                ++blockIdx;
            }
        }
    }

    /**
     * Decode the current frame and do iDCT to covert the coefficients to rgb value.
     */
    private void doDecode(int N1, int N2, short[] layers, int[][] dctCof, int[][] rgbVal) {
        for (int i = 0; i < layers.length; ++i) {
            int quantization = (layers[i] != 0) ? N1 : N2;
            // do not repaint the background every frame
            if (quantization == N2 && frameIdx % Configuration.BACKGROUND_REPAINT_RATE != 0) {
                // ignore these background blocks if their neighbors are all background blocks
                if (Decoder.isAroundAllBackground(i, layers)) {
                    backgrounds.add(i);
                    continue;
                }
            }
            // for R G B channel
            for (int j = 0; j < Configuration.CHANNEL_NUM; ++j) {
                int idxBase = dctBlkLen * dctBlkLen * j;

                int[] oneDDCT = Arrays.copyOfRange(dctCof[i], idxBase, idxBase + dctBlkLen * dctBlkLen);
                int[] iDCTVal = DCTUtil.doAANiDCT(quantization, oneDDCT);
                ArrayUtil.fill(iDCTVal, rgbVal[i], idxBase);
            }
        }
    }
}
