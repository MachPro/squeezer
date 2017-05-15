package exec;

import conf.Configuration;
import display.Player;
import thread.DecoderManager;
import thread.DecoderWorker;
import util.*;

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

    private int dctBlkLen = Configuration.DCT_BLOCK_LEN;

    public static void main(String[] args) {
        Decoder decoder = new Decoder();
        decoder.processArgs(args);
        decoder.run();
    }

    private void processArgs(String[] args) {
        Configuration.CMP_FILENAME = args[0];
        if (args.length > 1) {
            Configuration.WIDTH = Integer.parseInt(args[1]);
        }
        if (args.length > 2) {
            Configuration.HEIGHT = Integer.parseInt(args[2]);
        }
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
                // read # of frame
                int frameCount = readMetaData(fis);
                player.resetTime();
                // thread manager is in charge of the creation and running of decoder workers
                DecoderManager manager = new DecoderManager(Configuration.THREAD_COUNT, fis, frameCount);
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
        // last frame used to fill the background
        byte[] lastFrame = null;
        short[] lastLayer = null;

        long beginTime = System.currentTimeMillis();
        long lastTime = System.currentTimeMillis();
        System.out.println(frameCount);
        int currentFrameIdx = 0;
        while (currentFrameIdx < frameCount) {
            if (!player.playFlag) {
                // if paused, keep the image
                synchronized (this) {
                    this.wait(100);
                }
            } else {
                // fetch the worker which is responsible to calculate the current frame
                DecoderWorker worker = manager.getWorker(currentFrameIdx % Configuration.THREAD_COUNT);
                while (!worker.isFrameAlready()) {
                    synchronized (worker) {
                        worker.wait(5);
                    }
                }
                // get data of current frame
                int[][] dctCof = worker.getDctCof();
                byte[] displayFrame = worker.getDisplayFrame();
                short[] layer = worker.getLayer();
                Set<Integer> background = worker.getBackgrounds();

                if (lastFrame != null) {
                    // paint the background
                    for (Integer blockIdx : background) {
                        // if it was foreground block in last frame at the same space
                        if (lastLayer[blockIdx] == 1) {
                            // then decode it using background quantization factor
                            int[] rgbVal = new int[dctBlkLen * dctBlkLen * 3];
                            doBlockDecode(dctCof[blockIdx], rgbVal, Configuration.BACK_GROUND_QUANTIZATION_FACTOR);
                            BlockUtil.fillBlockInFrame(displayFrame, blockIdx, rgbVal);
                        } else {
                            // using the block in last frame
                            BlockUtil.fillBlockInFrame(displayFrame, blockIdx, lastFrame);
                        }
                    }
                }
                lastFrame = Arrays.copyOf(displayFrame, displayFrame.length);
                lastLayer = Arrays.copyOf(layer, layer.length);

                player.doPlay(displayFrame);
                if (currentFrameIdx == 0) {
                    beginTime = System.currentTimeMillis();
                } else {
                    // TODO not busy waiting
                    // frame rate control
                    long waitTime = player.getWaitTime();
                    while (System.currentTimeMillis() - waitTime - beginTime <
                            currentFrameIdx * 1000.0 / Configuration.FRAME_RATE)
                        ;
                }
//                System.out.println(currentFrameIdx + ": " + (System.currentTimeMillis() - lastTime));
                lastTime = System.currentTimeMillis();
                // wake the worker
                worker.setFrameIdx(currentFrameIdx + Configuration.THREAD_COUNT);
                synchronized (worker) {
                    worker.notifyAll();
                }
                ++currentFrameIdx;
            }
        }
        System.out.println("last for: " + (System.currentTimeMillis() - beginTime - player.getWaitTime()));
    }

    /**
     * Decode specified block using given quantization factor.
     *
     * @param dctCof DCT coefficients used for decoding
     * @param rgbVal rgb values after decoding
     * @param factor quantization factor
     */
    private void doBlockDecode(int[] dctCof, int[] rgbVal, int factor) {
        for (int j = 0; j < Configuration.CHANNEL_NUM; ++j) {
            // base index for rgb channel coefficients
            int cofBaseIdx = dctBlkLen * dctBlkLen * j;
            // copy the DCT value into one dimensional array
            int[] oneDDCT = Arrays.copyOfRange(dctCof, cofBaseIdx, cofBaseIdx + dctBlkLen * dctBlkLen);
            QuantizationUtil.dequantize(oneDDCT, factor);
            DCTUtil.doAANiDCT(oneDDCT);
            ArrayUtil.fill(oneDDCT, rgbVal, cofBaseIdx);
        }
    }

    /**
     * Read some meta data, here the number of frame.
     */
    private int readMetaData(InputStream is) throws IOException {
        int offset = 0;
        int numRead;
        byte[] buf = new byte[4];
        while (offset < buf.length
                && (numRead = is.read(buf, offset, buf.length - offset)) >= 0) {
            offset += numRead;
        }
        // convert the byte stream to integer
        return ByteUtil.byteToInt(buf, 0, 4);
    }
}
