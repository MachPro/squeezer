package thread;

import conf.Configuration;
import exec.Decoder;
import util.ArrayUtil;
import util.BlockUtil;
import util.DCTUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by yanliw on 17-5-4.
 */
public class DecoderWorker implements Runnable{

    public static int width = Configuration.WIDTH;

    public static int height = Configuration.HEIGHT;

    public static int dctBlkLen = Configuration.DCT_BLOCK_LEN;

    public static int blockCount = ((width + dctBlkLen - 1) / dctBlkLen) *
            ((height + dctBlkLen - 1) / dctBlkLen);

    public static int frameByteLen = Configuration.FRAME_BYTE_LEN;

    private int frameCount;
    private int frameIdx;
    private byte[] displayFrame;
    private byte[] fileFrame;
    private InputStream is;
    private short[] layer;
    private int[][] dctCof;
    private int N1;
    private int N2;
    private boolean frameAlready = false;
    private int[][] rgbVal;
    private Set<Integer> background;

    public DecoderWorker(InputStream is, int N1, int N2, int frameCount) {
        int fileFrameLen = 2 * blockCount * (dctBlkLen * dctBlkLen * 3 + 1);
        this.fileFrame = new byte[fileFrameLen];

        this.displayFrame = new byte[frameByteLen];
        this.layer = new short[blockCount];
        this.dctCof = new int[blockCount][dctBlkLen * dctBlkLen * 3];
        this.rgbVal = new int[blockCount][dctBlkLen * dctBlkLen * 3];
        this.background = new HashSet<>();
        this.is = is;
        this.N1 = N1;
        this.N2 = N2;
        this.frameCount = frameCount;
    }

    public void setFrameIdx(int idx) {
        this.frameIdx = idx;
    }

    public int getFrameIdx() {
        return this.frameIdx;
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

    public Set<Integer> getBackground() {
        return this.background;
    }

    @Override
    public void run() {
        while (frameIdx < frameCount) {
//            System.out.println("worker on :" + frameIdx);
//            background.clear();
            this.displayFrame = new byte[frameByteLen];
            frameAlready = false;
            read(is, fileFrame, layer, dctCof);
            doDecode(N1, N2, layer, dctCof, rgbVal);
            BlockUtil.fillFrame(frameIdx, displayFrame, rgbVal, layer, null);
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

    public void read(InputStream is, byte[] buf, short[] layers, int[][] dctCof) {
        synchronized (is) {
            // read byte data from file
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
            // convert every two byte data to one short data
            int blockIdx = 0;
            int i = 0;
            while (blockIdx < blockCount) {
                byte first = buf[i];
                byte second = buf[i + 1];
                i = i + 2;

                short layer = (short) ((first & 0xFF) << 8 | (second & 0XFF));
                layers[blockIdx] = layer;
                int j = 0;
                while (j < dctBlkLen * dctBlkLen * 3) {
                    first = buf[i];
                    second = buf[i + 1];
                    i = i + 2;

                    short dctVal = (short) ((first & 0xFF) << 8 | (second & 0XFF));
                    dctCof[blockIdx][j] = dctVal;

                    ++j;
                }
                ++blockIdx;
            }
        }

    }

    public void doDecode(int N1, int N2, short[] layers, int[][] dctCof, int[][] rgbVal) {
        int[][] rowColBlock = new int[dctBlkLen][dctBlkLen];
        for (int i = 0; i < layers.length; ++i) {
            int quantization = (layers[i] != 0) ? N1 : N2;
            if (quantization > 1 && frameIdx % Configuration.PASS_FRAME_RATE != 0) {
//                if (!lastForeground.contains(i)) {
//                    continue;
//                }
                if (Decoder.isAroundBackground(i, layers)) {
                    background.add(i);
                    continue;
                }
            }
            // for R G B channel
            for (int j = 0; j < Configuration.CHANNEL_NUM; ++j) {
                int idxBase = dctBlkLen * dctBlkLen * j;
                ArrayUtil.oneDToTwoD(dctCof[i], idxBase, rowColBlock);
              
                int[] oneDDCT = new int[64];
                ArrayUtil.twoDToOneD(rowColBlock, oneDDCT);
                int[] iDCTVal = DCTUtil.aanIDCT(quantization, oneDDCT);
               // int[][] iDCTVal = DCTUtil.doiDCTN3(quantization, rowColBlock);
                ArrayUtil.fill(iDCTVal, rgbVal[i], idxBase);
            }
        }
    }
}
