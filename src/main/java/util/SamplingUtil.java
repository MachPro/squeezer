package util;

/**
 * Different ways for subsampling.
 * <p/>
 * Created by yanliw on 17-4-20.
 */
public class SamplingUtil {

    /**
     * Implementation for 4:2:0 subsampling.
     * <br/>
     * The frame is divided into 16 * 16 block first. The orginal Y channel is maintained.
     * The Cb and Cr was subsampled as the average of every 2 * 2 pixel.
     * There should be four 8 * 8 block for Y channel
     * and one 8 * 8 block for Cb and Cr channel after subsampling.
     * @param originalBlock
     */
    public void FourTwoZeroSampling(int[][] originalBlock) {
        // TODO
    }
}
