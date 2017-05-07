import java.io.*;
import javax.swing.*;
import java.util.HashMap;
import conf.Configuration;

public class MyEncoder {

    public static int width = Configuration.WIDTH;

    public static int height = Configuration.HEIGHT;

    public static int frameByteLen = Configuration.FRAME_BYTE_LEN;

    public static void main(String[] args) {
        try {
            File file = new File(args[0]);
            InputStream is = new FileInputStream(file);
            
            long len = file.length();
            byte[] bytes = new byte[(int) len];
            
            int frameCount = (int) (len / frameByteLen);

            int offset = 0;
            int numRead = 0;
            while (offset < bytes.length
                    && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
                offset += numRead;
            }

            byte[] order_bytes = new byte[frameCount * frameByteLen];
            short[] dct_ints = new short[frameCount * frameByteLen];
            //short[] out_ints = new short[frameCount*((width/8)*(536/8) + width*536*3)];
            byte[] layers = new byte[frameCount*((width/8)*(536/8))];
            /**
             * Cosine matrix. N * N.
             */
            double c[][] = new double[8][8];
            
            /**
             * Transformed cosine matrix, N*N.
             */
            double cT[][] = new double[8][8];
            //index for order_bytes[]
            int order_ind = 0;
            
            //re-order bytes[] into order_bytes[]
            for(int j = 0; j<frameCount; j++){
                int frame = j*3*height*width;
                for(int y = 0; y < height/8; y++)
                    for(int x = 0; x < width/8; x++)
                        for(int i = 0; i < 3; i++)
                            for(int yy = 0; yy < 8; yy++)
                                for(int xx = 0; xx < 8; xx++)
                                    //write order_bytes of 8*8 unit
                                    order_bytes[order_ind++] = bytes[frame + 960*(8*y+yy) + 8*x+xx + height*width*i];
                
            }
            System.out.println("calculating dct...");
            //initialize c[][] and cT[][]
            for (int j = 0; j < 8; j++){
                c[0][j]  = 1.0 / Math.sqrt(8.0);
                cT[j][0] = c[0][j];
            }
            
            for (int i = 1; i < 8; i++){
                for (int j = 0; j < 8; j++){
                    double jj = (double)j;
                    double ii = (double)i;
                    c[i][j]  = Math.sqrt(2.0/8.0) * Math.cos(((2.0 * jj + 1.0) * ii * Math.PI) / (2.0 * 8.0));
                    cT[j][i] = c[i][j];
                }
            }

            for(int m = 0; m < frameCount*120*67; m++){
                //HashMap<Integer, Integer> source = new HashMap<Integer, Integer>();
                long average = 0;
                
                for(int yy = 0; yy < 8; yy++)
                    for(int xx = 0; xx < 8; xx++){
                        for(int i = 0; i < 3; i++){
                            int temp = order_bytes[xx + yy*8 + i*64 + m*64*3] & 0xff;
                            //source.put(i*64 + yy*8 + xx, temp);
                            average += temp;
                        }
                        
                    }
                //1.needed for fore/back-ground determination
                //2.may need to calculate motion vectors here
                //
                average /= 3*8*8;
                //0 for background, 1 for foreground
                layers[m] = (byte)0;
    
            }
            //encode order_bytes[] into dct_ints[]
            //calculate coeff for DCT
            for(int m = 0; m < order_bytes.length;){
                
                double temp[][] = new double[8][8];
                double temp1;
                
                for (int i = 0; i < 8; i++){
                    for (int j = 0; j < 8; j++){
                        temp[i][j] = 0.0;
                        for (int k = 0; k < 8; k++){
                            int input = order_bytes[m + i*8 + k] & 0xff;
                            temp[i][j] += input * cT[k][j];
                        }
                    }
                }
                
                for (int i = 0; i < 8; i++){
                    for (int j = 0; j < 8; j++){
                        temp1 = 0.0;
                        
                        for (int k = 0; k < 8; k++){
                            temp1 += (c[i][k] * temp[k][j]);
                        }
                        
                        dct_ints[m] = (short) Math.round(temp1);
                        m++;
                    }
                }
                if(m%(960*536*3*11)==0){
                    System.out.print("*");
                }
            }
            //combine layers and dct_ints[] to out_ints[]
//            int out_ind = 0;
//            for(int i=0; i < order_bytes.length/64/3; i++){
//                out_ints[out_ind++] = layers[i];
//                for(int j = 0; j < 64*3; j++){
//                    out_ints[out_ind++] = dct_ints[i*64*3 + j];
//                }
//            }
            
            //save output DCT
            String[] file_name = args[0].split("\\.");
            String output_file_name = file_name[0] + ".cmp";
            System.out.println("\nCompressed video Saved As " + output_file_name);
            File out = new File(output_file_name);
            if (!out.exists()) {
                out.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(out);
            ObjectOutputStream outputStream = new ObjectOutputStream(fos);
            
            outputStream.writeUnshared(layers);
            
            outputStream.writeUnshared(dct_ints);
            //outputStream.reset();
            
            fos.flush();
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
