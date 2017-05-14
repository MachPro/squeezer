package thread;

import java.io.InputStream;

/**
 * DecoderManger manages the creation and execution of DecoderWorker.
 * <p/>
 * Created by MachPro on 5/7/17.
 */
public class DecoderManager {

    private DecoderWorker[] workers;

    public DecoderManager(int n, InputStream is, int frameCount) {
        this.workers = new DecoderWorker[n];
        init(is, frameCount);
    }

    private void init(InputStream is, int frameCount) {
        for (int i = 0; i < workers.length; ++i) {
            DecoderWorker worker = new DecoderWorker(is, frameCount);
            worker.setFrameIdx(i);

            workers[i] = worker;
        }
    }

    public void start() throws InterruptedException {
        for (int i = 0; i < workers.length; ++i) {
            Thread thread = new Thread(workers[i]);
            thread.start();
            while (!workers[i].isFrameAlready()) {
                synchronized (workers[i]) {
                    workers[i].wait(10);
                }
            }
        }
    }

    public DecoderWorker getWorker(int i) {
        return this.workers[i];
    }
}
