package conf;

/**
 * Basic configuration for this project.
 * <p/>
 * Created by MachPro on 17-4-18.
 */
public class Configuration {

    public static int WIDTH = 960;

    public static int HEIGHT = 540;

    // R G B three channels
    public static final int CHANNEL_NUM = 3;

    // the size of a RGB frame
    public static final int FRAME_BYTE_LEN = WIDTH * HEIGHT * CHANNEL_NUM;

    // size of DCT block
    public static final int DCT_BLOCK_LEN = 8;

    // size of motion vector block
    public static final int MOTION_VECTOR_BLOCK_LEN = 16;

    public static String CMP_FILENAME = "default.cmp";

    public static final int BYTE_MASK = 0xFF;

    public static final int MOTION_VECTOR_SEARCH_SIZE = 8;

    public static final int GAZE_BLOCK_LEN = 64;

    public static final int BACKGROUND_REPAINT_RATE = 3;
    
    public static final int FRAME_RATE = 20;

    public static final int THREAD_COUNT = 4;
}
