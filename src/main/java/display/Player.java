package display;

import conf.Configuration;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * Created by MachPro on 17-5-7.
 */
public class Player {

    private boolean playFlag;
    private JFrame myFrame;
    private JLabel lbText1, lbText2;
    private JPanel myPanel;
    private GridBagConstraints constraint;
    private Point p;
    private JButton toggleButton;

    private long pauseTime = 0;
    private long restartTime = 0;
    private long waitTime = 0;
    private long totalWaitTime = 0;

    public Player() {
        this.initPlayer();
    }

    public void initPlayer() {
        myFrame = new JFrame();
        myFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        GridBagLayout gLayout = new GridBagLayout();
        myFrame.getContentPane().setLayout(gLayout);

        constraint = new GridBagConstraints();
        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 0;
        constraint.gridy = 0;

        lbText1 = new JLabel("");
        lbText1.setHorizontalAlignment(SwingConstants.LEFT);
        lbText2 = new JLabel("");
        lbText2.setHorizontalAlignment(SwingConstants.RIGHT);

        myFrame.getContentPane().add(lbText1, constraint);
        myFrame.getContentPane().add(lbText2, constraint);

        initPanel();
        myFrame.getContentPane().add(myPanel, constraint);

        p = new Point();
        playFlag = true;
    }

    /**
     * Control the window to display video
     */
    private void initPanel() {
        myPanel = new JPanel();

        toggleButton = new JButton("PAUSE");
        toggleButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        if (playFlag) {
                            toggleButton.setText("PLAY");
                            pauseTime = System.currentTimeMillis();
//                        	System.out.println("Video Paused!");
                        } else {
                            toggleButton.setText("PAUSE");
                            restartTime = System.currentTimeMillis();
                            waitTime = restartTime - pauseTime;
//                        	System.out.println("Video Play!");
                        }
                        playFlag = !playFlag;
                    }
                }
        );

        myPanel.add(toggleButton);
    }

    public long getWaitTime() {
        totalWaitTime += waitTime;
        waitTime = 0;
        return totalWaitTime;
    }

    /**
     * Control the mouse event
     */
    private class MyMouseAdapter extends MouseAdapter {
        @Override
        public void mouseMoved(MouseEvent event) {
            p.x = event.getX();
            p.y = event.getY();
            //System.out.println("Mouse moved! Pos is: " + event.getX()+ "," + event.getY() + ".");
        }

    }

    public void resetTime() {
        totalWaitTime = 0;
        waitTime = 0;
    }

    public int[] getMouse() {
        return new int[]{p.x, p.y};
    }

    /**
     * Display every frame as image
     *
     * @param data the rgb data in byte to display
     */
    public boolean doPlay(byte[] data, int frameNum) {
        int width = Configuration.WIDTH;
        int height = Configuration.HEIGHT;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        //Obtain the rgb value of the images        
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                byte r = data[index];
                byte g = data[index + height * width];
                byte b = data[index + height * width * 2];

                int pixel = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                img.setRGB(x, y, pixel);
                index++;
            }
        }

        //Update the information displayed in the panel
        String videoInfo = String.format("height*width: %d*%d", height, width);
        String gazeControlInfo = String.format("Loc:x=%d,y=%d  ", p.x, p.y);
        lbText1.setText(videoInfo);
        lbText2.setText(gazeControlInfo);

        //Update the image
        JLabel myImgLabel = new JLabel(new ImageIcon(img));
        MouseAdapter adapter = new MyMouseAdapter();
        myImgLabel.addMouseMotionListener(adapter);
        constraint.gridx = 0;
        constraint.gridy = 1;
        myFrame.getContentPane().add(myImgLabel, constraint, 0);
        myFrame.getContentPane().remove(1);

        myFrame.pack();
        myFrame.repaint();
        myFrame.setVisible(true);

        return playFlag;
    }
}