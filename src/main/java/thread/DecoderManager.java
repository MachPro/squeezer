package thread;

import java.io.InputStream;

/**
 * Created by MachPro on 5/7/17.
 */
public class DecoderManager {

    DecoderWorker[] workers;

    public DecoderManager(int n, InputStream is, int N1, int N2, int frameCount) {
        this.workers = new DecoderWorker[n];
        init(is, N1, N2, frameCount);
    }

    private void init(InputStream is, int N1, int N2, int frameCount) {
        for (int i = 0; i < workers.length; ++i) {
            DecoderWorker worker = new DecoderWorker(is, N1, N2, frameCount);
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
