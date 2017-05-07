import java.io.*;
import javax.swing.*;
import java.util.HashMap;


public class MyEncoderMV {
    
    
    
    
    public static void main(String[] args) {

        int width = 960;
        int height = 540;

        try {
            File file = new File(args[0]);
            InputStream is = new FileInputStream(file);
            
            long len = file.length();
            byte[] bytes = new byte[(int)len];
            
            int frame_len = (int) len/(height*width*3);
           
            int offset = 0;
            int numRead = 0;
            while (offset < bytes.length && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
                offset += numRead;
            }
            
            
            byte[] order_bytes = new byte[frame_len*3*(width*536)];
            short[] dct_ints = new short[frame_len*3*(width*536)];
            //short[] out_ints = new short[frame_len*((width/8)*(536/8) + width*536*3)];
            byte[] layers = new byte[frame_len*((width/8)*(536/8))];
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
            for(int j = 0; j<frame_len; j++){
                int frame = j*3*height*width;
                for(int y = 0; y < height/8; y++)
                    for(int x = 0; x < width/8; x++)
                        for(int i = 0; i < 3; i++)
                            for(int yy = 0; yy < 8; yy++)
                                for(int xx = 0; xx < 8; xx++)
                                    //write order_bytes of 8*8 unit
                                    order_bytes[order_ind++] = bytes[frame + 960*(8*y+yy) + 8*x+xx + height*width*i];
                
            }
            
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
            
            System.out.println("calculating motion vectors...");
            //calculate motion vectors
            //byte[] motionVector = new byte[(frame_len - 1)*120*67*2];
            int[] motionVector = new int[1*120*67*2];
            int[] sumVector = new int[1*120*67];
            //for(int m = 0; m < (frame_len - 1)*120*67; m++){
            for(int m = (30)*120*67; m < (31)*120*67; m++){
                int[] macroBlock = new int[64];
                //record each macroBlock pixel value;
                for(int yy = 0; yy < 8; yy++)
                    for(int xx = 0; xx < 8; xx++){
                        int[] rgb = new int[3];
                        for(int i = 0; i < 3; i++){
                             rgb[i] = (order_bytes[xx + yy*8 + i*64 + (1 + m)*64*3] & 0xff);
//                            if((order_bytes[xx + yy*8 + i*64 + (1 + m)*64*3] & 0xff) != 0)
//                                System.out.println("1:  " + (order_bytes[xx + yy*8 + i*64 + (1 + m)*64*3] & 0xff));
                        }
                        macroBlock[xx + yy*8] = (int) (0.299*rgb[0] + 0.587*rgb[1] + 0.114*rgb[2]);
//                        if(macroBlock[xx + yy*8] != 0)
//                            System.out.println("2:  " + macroBlock[xx + yy*8]);
                    }
                
                
                
                
                
                //calculate difference(square sums) between each macroBlock and its surrounding blocks, say 7*7
                int range = 8;  //needs calibration
                int minSum = Integer.MAX_VALUE;
                
                int coordInByte = (m/(120*67))*(960*540);
                int coordM = (m*64 - (m/(120*67))*960*536)/64;  //extra m in last frame page
                int coordX = (coordM % 120)*8;
                int coordY = (coordM / 120)*8;
                
               
                boolean boundary = false;
                //boundary cases
                if(coordX < range || coordX > (952 - range) || coordY < range || coordY > (528 - range))
                    boundary = true;
                
                int dx = 0;
                int dy = 0;
                
                if(!boundary){
                    for(int y = 0; y <= range ; y++){
                        for(int x = 0; x <= range; x++){
                            
                            for(int up = -1 * y; up <= y ; up += y==0 ? 1 : 2*y){
                                for (int left = -1*x; left <= x; left += x==0 ? 1 : 2*x){
                                    int sum = 0;
                                    
                                    for(int yy = 0; yy < 8; yy++)
                                        for(int xx = 0; xx < 8; xx++){
                                            int pix = 0;
                                            int coordShiftedX = coordX + left + xx;
                                            int coordShiftedY = coordY + up + yy;
                                            int coordShifted = coordInByte + coordShiftedY*960 + coordShiftedX;
                                            int[] rgb = new int[3];
                                            for(int i = 0; i < 3; i++){
                                                rgb[i] = (bytes[coordShifted + frame_len*height*width*i] & 0xff);
                                                
                                            }
                                            
                                            pix = (int)(0.299*rgb[0] + 0.587*rgb[1] + 0.114*rgb[2]);
                                            int difference = pix - macroBlock[xx + yy*8];
                                            sum += Math.abs(difference);
                                        }
                                    
                                    sum = (int) Math.sqrt(sum);
                                    //System.out.println("sum:  " + sum);
                                    
                                    if(sum < minSum){
                                        minSum = sum;
                                        
                                        dx = left;
                                        dy = up;
                                    }
                                }
                            }
                        }
                    }
                    
                }
                
//                if(minSum != 0)
//                    System.out.println("5:  " + minSum);
                
                
                int irrelevent1 = 0; //needs calibration
                int irrelevent2 = 8000; //needs calibration
                if(minSum < irrelevent1 || minSum > irrelevent2){
                    dx = 0;
                    dy = 0;
                    minSum = 0;
                }

                
                motionVector[2*(m - (30)*120*67)] = dx;
                motionVector[2*(m - (30)*120*67) + 1] = dy;
                //test: print motionvector here
                System.out.print(dx + " " + dy + " ");
                if (m%120 == 0)
                	System.out.println();
                
                sumVector[m - (30)*120*67] = minSum;
                if(m%(120*67*11)==0){
                    System.out.print("*");
                }
    
            }
            System.out.println();
            //decide 0 for background, 1 for foreground
            
//            for(int m = 0; m < (frame_len - 1)*120*67; m++){
//                int dx = motionVector[2*m];
//                int dy = motionVector[2*m + 1];
//                
//                //layers[m] = (byte)0;
//            }
          
            String fileName = "data.txt";
            
            File outfile = new File(fileName);
            FileWriter fileWriter = new FileWriter(outfile);
            
            
            for (int m = 0; m < 67; m++){
                for (int n = 0; n < 120; n++){
                    fileWriter.write(Integer.toString(motionVector[m*120*2 + n*2]));
                    fileWriter.write("\t");
                }
                fileWriter.write("\n");
            }
            
            for (int m = 0; m < 67; m++){
                for (int n = 0; n < 120; n++){
                    fileWriter.write(Integer.toString(motionVector[m*120*2 + n*2 + 1]));
                    fileWriter.write("\t");
                }
                fileWriter.write("\n");
            }
            
            for (int m = 0; m < 67; m++){
                for (int n = 0; n < 120; n++){
                    fileWriter.write(Integer.toString(sumVector[m*120 + n]));
                    fileWriter.write("\t");
                }
                fileWriter.write("\n");
            }
            
            fileWriter.flush();
            fileWriter.close();
            
            
            
//            System.out.println("calculating dct...");
            //encode order_bytes[] into dct_ints[]
            //calculate coeff for DCT
//            for(int m = 0; m < order_bytes.length;){
//                
//                double temp[][] = new double[8][8];
//                double temp1;
//                
//                for (int i = 0; i < 8; i++){
//                    for (int j = 0; j < 8; j++){
//                        temp[i][j] = 0.0;
//                        for (int k = 0; k < 8; k++){
//                            int input = order_bytes[m + i*8 + k] & 0xff;
//                            temp[i][j] += input * cT[k][j];
//                        }
//                    }
//                }
//                
//                for (int i = 0; i < 8; i++){
//                    for (int j = 0; j < 8; j++){
//                        temp1 = 0.0;
//                        
//                        for (int k = 0; k < 8; k++){
//                            temp1 += (c[i][k] * temp[k][j]);
//                        }
//                        
//                        dct_ints[m] = (short) Math.round(temp1);
//                        m++;
//                    }
//                }
//                
//               
//                
//                if(m%(960*536*3*11)==0){
//                    System.out.print("*");
//                }
//            }
           
            
            //combine layers and dct_ints[] to out_ints[]
//            int out_ind = 0;
//            for(int i=0; i < order_bytes.length/64/3; i++){
//                out_ints[out_ind++] = layers[i];
//                for(int j = 0; j < 64*3; j++){
//                    out_ints[out_ind++] = dct_ints[i*64*3 + j];
//                }
//            }
            
            //save output DCT
//            String[] file_name = args[0].split("\\.");
//            String output_file_name = file_name[0] + "test.cmp";
//            System.out.println("\nCompressed video Saved As " + output_file_name);
//            File out = new File(output_file_name);
//            if (!out.exists()) {
//                out.createNewFile();
//            }
//            FileOutputStream fos = new FileOutputStream(out);
//            ObjectOutputStream outputStream = new ObjectOutputStream(fos);
            
            //outputStream.writeUnshared(layers);
            
            //outputStream.writeUnshared(dct_ints);
            //outputStream.reset();
            
//            fos.flush();
//            fos.close();
          
            
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        
    }
    
}
