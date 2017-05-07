package display;

import conf.Configuration;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Created by yanliw on 17-4-28.
 */
public class Displayer {

    public static int width = Configuration.WIDTH;

    public static int height = Configuration.HEIGHT;

    public JFrame frame;


    public Displayer() {
        this.frame = new JFrame();
        GridBagLayout gLayout = new GridBagLayout();
        this.frame.getContentPane().setLayout(gLayout);

        String result = String.format("Video height: %d, width: %d, frame: %d", height, width);
        JLabel lbText1 = new JLabel(result);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.weightx = 0.5;
        c.gridx = 0;
        c.gridy = 0;
        this.frame.getContentPane().add(lbText1, c);
        this.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 1;
    }

    public void setImg(byte[] data) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int ind = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                byte r = data[ind];
                byte g = data[ind + height * width];
                byte b = data[ind + height * width * 2];

                int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                img.setRGB(x, y, pix);
                ind++;
            }
        }
        JLabel lbIm1 = new JLabel(new ImageIcon(img));
        GridBagConstraints c = new GridBagConstraints();

        frame.getContentPane().add(lbIm1, c);

        frame.pack();
        frame.setVisible(true);
    }

    public static void showFrame(byte[] data, int idx) {
        JFrame frame = new JFrame();
        GridBagLayout gLayout = new GridBagLayout();
        frame.getContentPane().setLayout(gLayout);

        String result = String.format("Video height: %d, width: %d, frame: %d", height, width, idx);
        JLabel lbText1 = new JLabel(result);
        lbText1.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel lbIm1;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int ind = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                byte r = data[ind];
                byte g = data[ind + height * width];
                byte b = data[ind + height * width * 2];

                int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                img.setRGB(x, y, pix);
                ind++;
            }
        }
        lbIm1 = new JLabel(new ImageIcon(img));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.weightx = 0.5;
        c.gridx = 0;
        c.gridy = 0;
        frame.getContentPane().add(lbText1, c);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 1;
        frame.getContentPane().add(lbIm1, c);

        frame.pack();
        frame.setVisible(true);
    }
}
