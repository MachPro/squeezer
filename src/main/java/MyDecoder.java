import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;


public class MyDecoder {

    public static void main(String[] args) {
        JFrame frame;
        JLabel lbIm1;
        BufferedImage img;
        int width = 960;
        int height = 540;
        int N1 = Integer.valueOf(args[1]);
        int N2 = Integer.valueOf(args[2]);
        boolean gazeControl = Integer.valueOf(args[3]) != 0;
        try {
            File file = new File(args[0]);
            ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(file));

            int frame_len = 363;

            byte[] layers;
            short[] bytes;
            short[] idct_ints = new short[frame_len * 3 * (width * 536)];

            layers = (byte[]) inputStream.readUnshared();
            System.out.println(layers.length / (120 * 67));

//            for(int i = 0; i < frame_len*3*(width*536); i++){
//                bytes[i] = inputStream.readShort();
//            }
            bytes = (short[]) inputStream.readUnshared();
            System.out.println(bytes.length / (120 * 67 * (8 * 8 * 3)));
            //int frame_len = (int) len/(120*67*(8*8*3+1));

//            int offset = 0;
//            int numRead = 0;
//            while (offset < bytes.length && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
//                offset += numRead;
//            }
            /*
              Cosine matrix. N * N.
             */
            double c[][] = new double[8][8];

            /*
              Transformed cosine matrix, N*N.
             */
            double cT[][] = new double[8][8];
            //index for order_bytes[]
            int order_ind = 0;

            //initialize c[][] and cT[][]
            for (int j = 0; j < 8; j++) {
                c[0][j] = 1.0 / Math.sqrt(8.0);
                cT[j][0] = c[0][j];
            }

            //qunatize with N1, N2 here
            //
            //

            for (int i = 1; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    double jj = (double) j;
                    double ii = (double) i;
                    c[i][j] = Math.sqrt(2.0 / 8.0) * Math.cos(((2.0 * jj + 1.0) * ii * Math.PI) / (2.0 * 8.0));
                    cT[j][i] = c[i][j];
                }
            }
            //System.out.println(frame_len);
            //System.out.println(len);
            //System.out.println(bytes.length);

            //encode order_bytes[] into idct_ints[]
            //calculate coeff for IDCT
            System.out.println("calculating idct...");
            for (int m = 0; m < bytes.length; ) {
                double temp[][] = new double[8][8];
                double temp1;

                for (int n = 0; n < 3; n++) {
                    for (int i = 0; i < 8; i++) {
                        for (int j = 0; j < 8; j++) {
                            temp[i][j] = 0.0;
                            for (int k = 0; k < 8; k++) {
                                int input = bytes[m + i * 8 + k];
                                temp[i][j] += input * c[k][j];
                            }
                        }
                    }

                    for (int i = 0; i < 8; i++) {
                        for (int j = 0; j < 8; j++) {
                            temp1 = 0.0;

                            for (int k = 0; k < 8; k++) {
                                temp1 += (cT[i][k] * temp[k][j]);
                            }
                            if (temp1 < 0) {
                                idct_ints[m] = 0;
                            } else if (temp1 > 255) {
                                idct_ints[m] = 255;
                            } else {
                                idct_ints[m] = (short) Math.round(temp1);
                            }

                            //idct_bytes[m] = (byte)(int) Math.round(temp1);
                            m++;

                        }
                    }
                }

                if (m % (120 * 67 * 193 * 11) == 0) {
                    System.out.print("*");
                }
            }

            System.out.println("\nDisplaying...");
            //play video
            img = new BufferedImage(width, 536, BufferedImage.TYPE_INT_RGB);
            // Use labels to display the images
            frame = new JFrame();
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

            GridBagLayout gLayout = new GridBagLayout();
            frame.getContentPane().setLayout(gLayout);


            GridBagConstraints container = new GridBagConstraints();


            JLabel lbText1 = new JLabel("");
            lbText1.setHorizontalAlignment(SwingConstants.CENTER);

            container.fill = GridBagConstraints.HORIZONTAL;
            container.anchor = GridBagConstraints.CENTER;
            container.weightx = 0.5;
            container.gridx = 0;
            container.gridy = 0;
            frame.getContentPane().add(lbText1, container);
            while (true) {

                for (int j = 0; j < frame_len; j++) {

                    int ind = j * 3 * 536 * width;
                    for (int y = 0; y < 67; y++) {
                        for (int x = 0; x < 120; x++) {
                            for (int yy = 0; yy < 8; yy++) {
                                for (int xx = 0; xx < 8; xx++) {
                                    int r = idct_ints[ind];
                                    int g = idct_ints[ind + 64];
                                    int b = idct_ints[ind + 64 * 2];

                                    int pix = ((r << 16) + (g << 8) + b);
                                    img.setRGB(x * 8 + xx, y * 8 + yy, pix);
                                    ind += 1;
                                }
                            }
                            ind += 64 * 2;
                        }
                    }
                    String result = String.format("height*width: %d*%d, frame: %3d/%d", height, width, ind / height / width / 3, frame_len);
                    lbText1.setText(result);

                    lbIm1 = new JLabel(new ImageIcon(img));
                    container.fill = GridBagConstraints.HORIZONTAL;
                    container.gridx = 0;
                    container.gridy = 1;
                    frame.getContentPane().add(lbIm1, container);
                    frame.pack();

                    frame.setVisible(true);
                }
            }
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }
    }
}
