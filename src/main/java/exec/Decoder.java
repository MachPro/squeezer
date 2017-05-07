package exec;

import conf.Configuration;
//import thread.DecoderWorker;
import thread.DecoderManager;
import thread.DecoderWorker;
import util.ArrayUtil;
import util.BlockUtil;
import util.DCTUtil;
import util.PlayerUtil;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by yanliw on 17-4-23.
 */
public class Decoder {

    public static int width = Configuration.WIDTH;

    public static int height = Configuration.HEIGHT;

    public static int dctBlkLen = Configuration.DCT_BLOCK_LEN;

    public static int blockCount = ((width + dctBlkLen - 1) / dctBlkLen) *
            ((height + dctBlkLen - 1) / dctBlkLen);

    public static int frameByteLen = Configuration.FRAME_BYTE_LEN;

    public static int frameRate = Configuration.FRAME_RATE;

    private static boolean playFlag = true;

//    private static byte[] displayFrame;

    public int currentFrameIdx = 0;

    public static void main(String[] args) {
        double N1 = Double.parseDouble(args[0]);
        double N2 = Double.parseDouble(args[1]);
        boolean isGazeControl = (1 == Integer.valueOf(args[2]));

//        System.out.println("Input args: N1 " + N1 + ", N2 " + N2 + " gaze Control " + isGazeControl);
        Decoder decoder = new Decoder();
//        decoder.run(N1, N2, isGazeControl);
        decoder.runMultiThread(N1, N2, isGazeControl);
    }

    public void runMultiThread(double N1, double N2, boolean isGazeControl) {
        File file = new File(Configuration.DCT_OUTPUT_FILENAME);
        // a short is two bytes, a frame has 120 * 68 blocks,
        // a block has 8 * 8 * 3 dct coefficients plus 1 layer coefficient
        int fileFrameLen = 2 * blockCount * (dctBlkLen * dctBlkLen * 3 + 1);
        // a frame of data read from file
        byte[] fileFrame = new byte[fileFrameLen];

        int frameCount = (int) (file.length() / fileFrameLen);
//        System.out.println("file length (in byte): " + file.length());
//        System.out.println("frame number: " + frameCount);

        PlayerUtil.initPlayer(frameCount);
        while (true) {
            try (FileInputStream fis = new FileInputStream(file)) {
                PlayerUtil.resetTime();
                int currentFrameIdx = 0;

                DecoderManager manager = new DecoderManager(4, fis, (int) N1, (int) N2, frameCount);
                manager.start();

//                DecoderWorker worker1 = new DecoderWorker(fis, (int) N1, (int) N2, frameCount);
//                worker1.setFrameIdx(0);
//                DecoderWorker worker2 = new DecoderWorker(fis, (int) N1, (int) N2, frameCount);
//                worker2.setFrameIdx(1);
//                DecoderWorker worker3 = new DecoderWorker(fis, (int) N1, (int) N2, frameCount);
//                worker3.setFrameIdx(2);
//                DecoderWorker worker4 = new DecoderWorker(fis, (int) N1, (int) N2, frameCount);
//                worker4.setFrameIdx(3);
//
//                Thread thread1 = new Thread(worker1);
//                thread1.start();
//                while (!worker1.isFrameAlready()) {
//                    synchronized (worker1) {
//                        worker1.wait(10);
//                    }
//                }
//                Thread thread2 = new Thread(worker2);
//                thread2.start();
//                while (!worker2.isFrameAlready()) {
//                    synchronized (worker2) {
//                        worker2.wait(10);
//                    }
//                }
//                Thread thread3 = new Thread(worker3);
//                thread3.start();
//                while (!worker3.isFrameAlready()) {
//                    synchronized (worker3) {
//                        worker3.wait(10);
//                    }
//                }
//                Thread thread4 = new Thread(worker4);
//                thread4.start();

                int gazeX = -1;
                int gazeY = -1;
                byte[] lastFrame = null;
                byte[] displayFrame = new byte[frameByteLen];

                long begin = System.currentTimeMillis();
                long lastTime = System.currentTimeMillis();
                while (currentFrameIdx < frameCount) {
                    Set<Integer> gazeBlocks = null;
                    if (isGazeControl && (gazeX >= 0 && gazeY >= 0)) {
                        gazeBlocks = BlockUtil.getBlockIdxAroundPoint(gazeX, gazeY, Configuration.GAZE_BLOCK_LEN / 2);
                    }

                    DecoderWorker worker = manager.getWorker(currentFrameIdx % 4);

//                    if (currentFrameIdx % 4 == 0) {
//                        worker = worker1;
//                    } else if (currentFrameIdx % 4 == 1) {
//                        worker = worker2;
//                    } else if (currentFrameIdx % 4 == 2) {
//                        worker = worker3;
//                    } else {
//                        worker = worker4;
//                    }

                    while (!worker.isFrameAlready()) {
                        synchronized (worker) {
                            worker.wait(5);
                        }
                    }

                    if (playFlag) {

                        displayFrame = worker.getDisplayFrame();
//                	byte[] displayFrame = worker.getDisplayFrame();
                        if (lastFrame != null) {
                            for (int i = 0; i < displayFrame.length; ++i) {
                                if (displayFrame[i] == 0) {
                                    displayFrame[i] = lastFrame[i];
                                }
                            }
                        }

                        if (gazeBlocks != null) {
                            int[][] dctCof = worker.getDctCof();

                            for (int blockIdx : gazeBlocks) {
                                int[] rgbVal = new int[dctBlkLen * dctBlkLen * 3];
                                doGazeBlockDecode(blockIdx, dctCof[blockIdx], rgbVal);
                                BlockUtil.fillBlockInFrame(currentFrameIdx, displayFrame, blockIdx, rgbVal);
                            }
                        }
                        // - pauseTime
                        long waitTime = 0;
                        playFlag = PlayerUtil.doPlay(displayFrame, currentFrameIdx);
                        if (currentFrameIdx > 0) {
                            waitTime = PlayerUtil.getWaitTime();
//	                	System.out.println(waitTime);
                            while ((System.currentTimeMillis() - waitTime - begin) < (currentFrameIdx) * 1000.0 / frameRate)
                                ;
                        } else {
                            begin = System.currentTimeMillis();
                            lastTime = System.currentTimeMillis();
                        }
//	                System.out.println(currentFrameIdx+":"+(System.currentTimeMillis()-lastTime));
                        lastTime = System.currentTimeMillis();
                        lastFrame = displayFrame;

                        worker.setFrameIdx(currentFrameIdx + 4);
                        synchronized (worker) {
                            worker.notifyAll();
                        }

                        int[] gaze = PlayerUtil.getMouse();
                        if (gaze != null) {
                            //                    System.out.println(gaze[0] + " " + gaze[1]);
                            gazeX = gaze[0];
                            gazeY = gaze[1];
                        }
                        ++currentFrameIdx;
                    } else {
                        playFlag = PlayerUtil.doPlay(displayFrame, currentFrameIdx);
                    }
                }
                long end = System.currentTimeMillis();
//            System.out.println("finish at:" + end + " last for " + (end - begin));
//            currentFrameIdx=0;            
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public void run(double N1, double N2, boolean isGazeControl) {
        File file = new File(Configuration.DCT_OUTPUT_FILENAME);
        // a short is two bytes, a frame has 120 * 68 blocks,
        // a block has 8 * 8 * 3 dct coefficients plus 1 layer coefficient
        int fileFrameLen = 2 * blockCount * (dctBlkLen * dctBlkLen * 3 + 1);
        // a frame of data read from file
        byte[] fileFrame = new byte[fileFrameLen];

        int frameCount = (int) (file.length() / fileFrameLen);
        System.out.println("file length (in byte): " + file.length());
        System.out.println("frame number: " + frameCount);

        //Separate the layer and DCT coefficients
        short[] layer = new short[blockCount];
        Set<Integer> lastForeground = new HashSet<>();

        int[][] dctCof = new int[blockCount][dctBlkLen * dctBlkLen * 3];
        // the frame that will be displayed
        byte[] displayFrame = new byte[frameByteLen];
        byte[] lastDisplay = new byte[frameByteLen];

        int gazeX = -1;
        int gazeY = -1;

        long begin = System.currentTimeMillis();
        System.out.println("begin exec at: " + begin);
        try (FileInputStream fis = new FileInputStream(file)) {
            PlayerUtil.initPlayer(frameCount);

            while (currentFrameIdx < frameCount) {
                read(fis, fileFrame, layer, dctCof);
                doDecode(currentFrameIdx, gazeX, gazeY, (int) N1, (int) N2, isGazeControl, layer, lastForeground, dctCof);
                BlockUtil.fillFrame(currentFrameIdx, displayFrame, dctCof, layer, lastDisplay);
                // TODO
                // show frame here, use the byte array displayFrame
                PlayerUtil.doPlay(displayFrame, currentFrameIdx);
//                lastDisplay = displayFrame;
//                if (gaze != null) {
////                    System.out.println(gaze[0] + " " + gaze[1]);
//                    gazeX = gaze[0];
//                    gazeY = gaze[1];
//                }
                ++currentFrameIdx;
            }
            long end = System.currentTimeMillis();
            System.out.println("finish at:" + end + " last for " + (end - begin));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void doGazeBlockDecode(int blockIdx, int[] dctCof, int[] rgbVal) {
        int[][] rowColBlock = new int[dctBlkLen][dctBlkLen];
        for (int j = 0; j < Configuration.CHANNEL_NUM; ++j) {
            int idxBase = dctBlkLen * dctBlkLen * j;
            ArrayUtil.oneDToTwoD(dctCof, idxBase, rowColBlock);
//                int[][] iDCTVal = DCTUtil.doiDCT(quantization, rowColBlock);
            int[][] iDCTVal = DCTUtil.doiDCTN3(1, rowColBlock);
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
