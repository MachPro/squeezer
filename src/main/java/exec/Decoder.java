package exec;

import conf.Configuration;
import display.Player;
import thread.DecoderManager;
import thread.DecoderWorker;
import util.ArrayUtil;
import util.BlockUtil;
import util.ByteUtil;
import util.DCTUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;

/**
 * Decode compressed file and display video.
 * <p/>
 * Created by MachPro on 17-4-23.
 */
public class Decoder {
    // quantization factor for foreground
    private int N1;
    // quantization factor for background
    private int N2;
    // allow gaze control or not
    private boolean gazeControl;

    private static int width = Configuration.WIDTH;

    private static int height = Configuration.HEIGHT;

    private static int dctBlkLen = Configuration.DCT_BLOCK_LEN;

    public static void main(String[] args) {
        Decoder decoder = new Decoder(args);
        decoder.run();
    }

    public Decoder(String[] args) {
        this.N1 = (int) Double.parseDouble(args[0]);
        this.N2 = (int) Double.parseDouble(args[1]);
//        this.gazeControl = (1 == Integer.valueOf(args[2]));
    }

    /**
     * Read compressed file and do some preparation work before decoding starts.
     */
    private void run() {
        File file = new File(Configuration.CMP_FILENAME);

        Player player = new Player();
        player.initPlayer();
        while (true) {
            try (FileInputStream fis = new FileInputStream(file)) {
                int frameCount = readMetaData(fis);
                player.resetTime();
                // thread manager is in charge of the creation and running of decoder workers
                DecoderManager manager = new DecoderManager(Configuration.THREAD_COUNT, fis, N1, N2, frameCount);
                manager.start();
                // begin decoding
                doDecode(manager, player, frameCount);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Decode and display the video. The gaze control and background paint is implemented in this function.
     */
    private void doDecode(DecoderManager manager, Player player, int frameCount)
            throws InterruptedException {
        int currentFrameIdx = 0;
        // position of mouse
        int gazeX = -1;
        int gazeY = -1;
        // play or pause
        boolean playFlag = true;
        // last frame used to fill the background
        byte[] lastFrame = null;
        short[] lastLayer = null;

        long beginTime = System.currentTimeMillis();
        long lastTime = System.currentTimeMillis();
        while (currentFrameIdx < frameCount) {
            // fetch the worker which is responsible to calculate the current frame
            DecoderWorker worker = manager.getWorker(currentFrameIdx % Configuration.THREAD_COUNT);
            while (!worker.isFrameAlready()) {
                synchronized (worker) {
                    worker.wait(5);
                }
            }

            if (!playFlag) {
                // TODO do not need to call the function
                // if paused, continue play last frame
                playFlag = player.doPlay(lastFrame, currentFrameIdx);
            } else {
                int[][] dctCof = worker.getDctCof();
                byte[] displayFrame = worker.getDisplayFrame();
                short[] layer = worker.getLayer();
                Set<Integer> background = worker.getBackgrounds();

                if (lastFrame != null) {
                    // paint the background
                    for (Integer blockIdx : background) {
                        // if it was foreground block in last frame at the same space
                        if (lastLayer[blockIdx] == 1) {
                            // then decode it using a different quantization factor
                            int[] rgbVal = new int[dctBlkLen * dctBlkLen * 3];
                            doBlockDecode(dctCof[blockIdx], rgbVal, N2);
                            BlockUtil.fillBlockInFrame(displayFrame, blockIdx, rgbVal);
                        } else {
                            // using the block in last frame
                            BlockUtil.fillBlockInFrame(displayFrame, blockIdx, lastFrame);
                        }
                    }
                }
                lastFrame = Arrays.copyOf(displayFrame, displayFrame.length);
                lastLayer = Arrays.copyOf(layer, layer.length);
                // repaint the gaze blocks
                Set<Integer> gazeBlocks = null;
                if (gazeControl && (gazeX >= 0 && gazeY >= 0)) {
                    // gaze blocks around mouse
                    gazeBlocks = BlockUtil.getBlockIdxAroundPoint(gazeX, gazeY,
                            Configuration.GAZE_BLOCK_LEN);
                }
                if (gazeBlocks != null) {
                    for (int blockIdx : gazeBlocks) {
                        int[] rgbVal = new int[dctBlkLen * dctBlkLen * 3];
                        doBlockDecode(dctCof[blockIdx], rgbVal, 1);
                        BlockUtil.fillBlockInFrame(displayFrame, blockIdx, rgbVal);
                    }
                }
                playFlag = player.doPlay(displayFrame, currentFrameIdx);
                if (currentFrameIdx == 0) {
                    beginTime = System.currentTimeMillis();
                } else {
                    long waitTime = player.getWaitTime();
//	                	System.out.println(waitTime);
                    while (System.currentTimeMillis() - waitTime - beginTime <
                            currentFrameIdx * 1000.0 / Configuration.FRAME_RATE)
                        ;
                }
                System.out.println(currentFrameIdx + ": " + (System.currentTimeMillis() - lastTime));
                lastTime = System.currentTimeMillis();

                worker.setFrameIdx(currentFrameIdx + Configuration.THREAD_COUNT);
                synchronized (worker) {
                    worker.notifyAll();
                }
                if (gazeControl) {
                    int[] gaze = player.getMouse();
                    if (gaze != null) {
//                    System.out.println(gaze[0] + " " + gaze[1]);
                        gazeX = gaze[0];
                        gazeY = gaze[1];
                    }
                }
                ++currentFrameIdx;
            }
        }
        System.out.println("last for: " + (System.currentTimeMillis() - beginTime));
    }

    /**
     * Decode specified block using given quantization factor.
     *
     * @param dctCof DCT coefficients used for decoding
     * @param rgbVal rgb values after decoding
     * @param N      quantization factor
     */
    private void doBlockDecode(int[] dctCof, int[] rgbVal, int N) {
        for (int j = 0; j < Configuration.CHANNEL_NUM; ++j) {
            // base index for rgb channel coefficients
            int cofBaseIdx = dctBlkLen * dctBlkLen * j;

            int[] oneDDCT = Arrays.copyOfRange(dctCof, cofBaseIdx, cofBaseIdx + dctBlkLen * dctBlkLen);
            int[] iDCTVal = DCTUtil.doAANiDCT(N, oneDDCT);
            ArrayUtil.fill(iDCTVal, rgbVal, cofBaseIdx);
        }
    }

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

    private int readMetaData(InputStream is) throws IOException {
        int offset = 0;
        int numRead;
        byte[] buf = new byte[4];
        while (offset < buf.length
                && (numRead = is.read(buf, offset, buf.length - offset)) >= 0) {
            offset += numRead;
        }
        return ByteUtil.byteToInt(buf,0, 4);
    }
}
