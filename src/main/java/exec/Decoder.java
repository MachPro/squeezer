package exec;

import conf.Configuration;
import display.Player;
import thread.DecoderManager;
import thread.DecoderWorker;
import util.ArrayUtil;
import util.BlockUtil;
import util.DCTUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by yanliw on 17-4-23.
 */
public class Decoder {

    private int N1;

    private int N2;

    private boolean gazeControl;

    public static int width = Configuration.WIDTH;

    public static int height = Configuration.HEIGHT;

    public static int dctBlkLen = Configuration.DCT_BLOCK_LEN;

    public static int blockCount = ((width + dctBlkLen - 1) / dctBlkLen) *
            ((height + dctBlkLen - 1) / dctBlkLen);

    public static void main(String[] args) {
//        System.out.println("Input args: N1 " + N1 + ", N2 " + N2 + " gaze Control " + isGazeControl);
        Decoder decoder = new Decoder(args);
//        decoder.run(N1, N2, isGazeControl);
        decoder.runMultiThread();
    }

    public Decoder(String[] args) {
        this.N1 = (int) Double.parseDouble(args[0]);
        this.N2 = (int) Double.parseDouble(args[1]);
        this.gazeControl = (1 == Integer.valueOf(args[2]));
    }

    public void runMultiThread() {
        File file = new File(Configuration.DCT_OUTPUT_FILENAME);
        // a short is two bytes, a frame has 120 * 68 blocks,
        // a block has 8 * 8 * 3 dct coefficients plus 1 layer coefficient
        int fileFrameLen = 2 * blockCount * (dctBlkLen * dctBlkLen * 3 + 1);

        int frameCount = (int) (file.length() / fileFrameLen);
        System.out.println("frame count: " + frameCount);

        Player player = new Player(frameCount);
        player.initPlayer();
        while (true) {
            try (FileInputStream fis = new FileInputStream(file)) {
                player.resetTime();

                DecoderManager manager = new DecoderManager(Configuration.THREAD_COUNT, fis, N1, N2, frameCount);
                manager.start();

                doDecode(manager, player, frameCount);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void doDecode(DecoderManager manager, Player player, int frameCount)
            throws InterruptedException {
        int currentFrameIdx = 0;
        int gazeX = -1;
        int gazeY = -1;
        boolean playFlag = true;

        byte[] lastFrame = null;
        short[] lastLayer = null;

        long beginTime = System.currentTimeMillis();
        long lastTime = System.currentTimeMillis();
        while (currentFrameIdx < frameCount) {
            Set<Integer> gazeBlocks = null;
            if (gazeControl && (gazeX >= 0 && gazeY >= 0)) {
                gazeBlocks = BlockUtil.getBlockIdxAroundPoint(gazeX, gazeY,
                        Configuration.GAZE_BLOCK_LEN / 2);
            }

            DecoderWorker worker = manager.getWorker(currentFrameIdx % Configuration.THREAD_COUNT);
            while (!worker.isFrameAlready()) {
                synchronized (worker) {
                    worker.wait(5);
                }
            }

            if (!playFlag) {
                playFlag = player.doPlay(lastFrame, currentFrameIdx);
            } else {
                int[][] dctCof = worker.getDctCof();
                byte[] displayFrame = worker.getDisplayFrame();
                short[] layer = worker.getLayer();
                Set<Integer> background = worker.getBackground();

                if (lastFrame != null) {
                    for (Integer blockIdx : background) {
                        if (lastLayer[blockIdx] == 1) {
                            int[] rgbVal = new int[dctBlkLen * dctBlkLen * 3];
                            doBlockDecode(blockIdx, dctCof[blockIdx], rgbVal, N2);
                            BlockUtil.fillBlockInFrame(currentFrameIdx, displayFrame, blockIdx, rgbVal);
                        } else {
                            BlockUtil.fillBlockInFrame(displayFrame, lastFrame, blockIdx);
                        }
                    }
                }
                lastFrame = Arrays.copyOf(displayFrame, displayFrame.length);
                lastLayer = Arrays.copyOf(layer, layer.length);

                if (gazeBlocks != null) {
                    for (int blockIdx : gazeBlocks) {
                        int[] rgbVal = new int[dctBlkLen * dctBlkLen * 3];
                        doBlockDecode(blockIdx, dctCof[blockIdx], rgbVal, 1);
                        BlockUtil.fillBlockInFrame(currentFrameIdx, displayFrame, blockIdx, rgbVal);
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

//    public void run(double N1, double N2, boolean isGazeControl) {
//        File file = new File(Configuration.DCT_OUTPUT_FILENAME);
//        // a short is two bytes, a frame has 120 * 68 blocks,
//        // a block has 8 * 8 * 3 dct coefficients plus 1 layer coefficient
//        int fileFrameLen = 2 * blockCount * (dctBlkLen * dctBlkLen * 3 + 1);
//        // a frame of data read from file
//        byte[] fileFrame = new byte[fileFrameLen];
//
//        int frameCount = (int) (file.length() / fileFrameLen);
//        System.out.println("file length (in byte): " + file.length());
//        System.out.println("frame number: " + frameCount);
//
//        //Separate the layer and DCT coefficients
//        short[] layer = new short[blockCount];
//        Set<Integer> lastForeground = new HashSet<>();
//
//        int[][] dctCof = new int[blockCount][dctBlkLen * dctBlkLen * 3];
//        // the frame that will be displayed
//        byte[] displayFrame = new byte[frameByteLen];
//        byte[] lastDisplay = new byte[frameByteLen];
//
//        int gazeX = -1;
//        int gazeY = -1;
//
//        Player player = new Player(frameCount);
//
//        long begin = System.currentTimeMillis();
//        System.out.println("begin exec at: " + begin);
//        try (FileInputStream fis = new FileInputStream(file)) {
//            player.initPlayer();
//
//            while (currentFrameIdx < frameCount) {
//                read(fis, fileFrame, layer, dctCof);
//                doDecode(currentFrameIdx, gazeX, gazeY, (int) N1, (int) N2, isGazeControl, layer, lastForeground, dctCof);
//                BlockUtil.fillFrame(currentFrameIdx, displayFrame, dctCof, layer, lastDisplay);
//                // show frame here, use the byte array displayFrame
//                player.doPlay(displayFrame, currentFrameIdx);
////                lastDisplay = displayFrame;
////                if (gaze != null) {
//////                    System.out.println(gaze[0] + " " + gaze[1]);
////                    gazeX = gaze[0];
////                    gazeY = gaze[1];
////                }
//                ++currentFrameIdx;
//            }
//            long end = System.currentTimeMillis();
//            System.out.println("finish at:" + end + " last for " + (end - begin));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    public void doBlockDecode(int blockIdx, int[] dctCof, int[] rgbVal, int N) {
        int[][] rowColBlock = new int[dctBlkLen][dctBlkLen];
        for (int j = 0; j < Configuration.CHANNEL_NUM; ++j) {
            int idxBase = dctBlkLen * dctBlkLen * j;
            ArrayUtil.oneDToTwoD(dctCof, idxBase, rowColBlock);
//                int[][] iDCTVal = DCTUtil.doiDCT(quantization, rowColBlock);
            int[][] iDCTVal = DCTUtil.doiDCTN3(N, rowColBlock);
            ArrayUtil.fill(iDCTVal, rgbVal, idxBase);
        }
    }

    public void doDecode(int idx, int gazeX, int gazeY, int N1, int N2, boolean isGazeControl,
                         short[] layers, Set<Integer> lastForeground, int[][] dctCof) {
        Set<Integer> gazeBlocks = null;
        if (isGazeControl && (gazeX >= 0 && gazeY >= 0)) {
            gazeBlocks = BlockUtil.getBlockIdxAroundPoint(gazeX, gazeY, 32);
        }
        Set<Integer> foreground = new HashSet<>();
        int[][] rowColBlock = new int[dctBlkLen][dctBlkLen];

        for (int i = 0; i < layers.length; ++i) {
            int quantization = (layers[i] != 0) ? N1 : N2;
            if (layers[i] != 0) {
                foreground.add(i);
            }
            if (gazeBlocks != null && gazeBlocks.contains(i)) {
                quantization = 1;
                layers[i] = 2;
            }
            if (quantization > 1 && idx % Configuration.PASS_FRAME_RATE != 0) {
//                if (!lastForeground.contains(i)) {
//                    continue;
//                }
                if (isAroundBackground(i, layers)) {
                    continue;
                }
            }
            // for R G B channel
            for (int j = 0; j < Configuration.CHANNEL_NUM; ++j) {
                int idxBase = dctBlkLen * dctBlkLen * j;
                ArrayUtil.oneDToTwoD(dctCof[i], idxBase, rowColBlock);
//                int[][] iDCTVal = DCTUtil.doiDCT(quantization, rowColBlock);
                int[][] iDCTVal = DCTUtil.doiDCTN3(quantization, rowColBlock);
                ArrayUtil.fill(iDCTVal, dctCof[i], idxBase);
            }
        }
        lastForeground.clear();
        lastForeground.addAll(foreground);
    }

    public static boolean isAroundBackground(int blockIdx, short[] layers) {
        int xBlockCount = (width + dctBlkLen - 1) / dctBlkLen;
        int yBlockCount = (height + dctBlkLen - 1) / dctBlkLen;

        int x = blockIdx % xBlockCount;
        int y = blockIdx / xBlockCount;
        if (x - 1 >= 0 && layers[blockIdx - 1] == 1) {
            return false;
        }
        if (x + 1 < xBlockCount && layers[blockIdx + 1] == 1) {
            return false;
        }
        if (y - 1 >= 0 && layers[blockIdx - xBlockCount] == 1) {
            return false;
        }
        if (y + 1 < yBlockCount && layers[blockIdx + xBlockCount] == 1) {
            return false;
        }
        return true;
    }

    public void read(InputStream is, byte[] buf, short[] layers, int[][] dctCof) {
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
