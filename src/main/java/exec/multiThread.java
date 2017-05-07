package exec;

import conf.Configuration;
import util.ArrayUtil;
import util.BlockUtil;
import util.DCTUtil;
import util.PlayerUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;



public class multiThread {

	

    public static int width = Configuration.WIDTH;

    public static int height = Configuration.HEIGHT;

    public static int dctBlkLen = Configuration.DCT_BLOCK_LEN;

    public static int mvBlkLen = Configuration.MOTION_VECTOR_BLOCK_LEN;

    public static int blockCount = ((width + dctBlkLen - 1) / dctBlkLen) *
            ((height + dctBlkLen - 1) / dctBlkLen);

    public static int frameByteLen = Configuration.FRAME_BYTE_LEN;
    
    public int currentFrameIdx = 0;
    private static int FRAMERATE = Configuration.FRAME_RATE;
	private static boolean playFlag = true;
	private static byte[] mydisplayFrame = new byte[frameByteLen];

	
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		double N1 = Double.parseDouble(args[0]);
        double N2 = Double.parseDouble(args[1]);
        boolean isGazeControl = (1 == Integer.valueOf(args[2]));

        System.out.println("Input args: N1 " + N1 + ", N2 " + N2 + " gaze Control " + isGazeControl);
        multiThread prog = new multiThread();
        prog.run(N1, N2, isGazeControl);
        
	}

	
	public void run(double N1, double N2, boolean isGazeControl) {
		
		 File file = new File(Configuration.DCT_OUTPUT_FILENAME);
	        // a short is two bytes, a frame has 120 * 68 blocks,
	        // a block has 8 * 8 * 3 dct coefficients plus 1 layer coefficient
	        int fileFrameLen = 2 * blockCount * (dctBlkLen * dctBlkLen * 3 + 1);
	        // a frame of data read from file
	        byte[] fileFrame = new byte[fileFrameLen];

	        int frameCount = (int) (file.length() / fileFrameLen);
	        System.out.println("file length (in byte): " + file.length());
	        System.out.println("frame number: " + frameCount);

	        //Separate the layer and DCT coefficients
	        short[] layer = new short[blockCount];
	        int[][] dctCof = new int[blockCount][dctBlkLen * dctBlkLen * 3];
	        // the frame that will be displayed
	        byte[] lastDisplay = new byte[frameByteLen];
	        Set<Integer> set = new HashSet<>();
	        int gazeX = -1;
	        int gazeY = -1;

	        long begin = System.currentTimeMillis();
	        System.out.println("begin exec at: " + begin);
	        try (FileInputStream fis = new FileInputStream(file)) {
	            PlayerUtil.initPlayer(frameCount);
	            while (currentFrameIdx < frameCount) {
	            	
					if(playFlag){
		                read(fis, fileFrame, layer, dctCof);
		                
		                System.out.println("r1 : " + (System.currentTimeMillis() - begin));
		                TransferThread R1 = new TransferThread( "Transfer", currentFrameIdx, gazeX, gazeY, (int) N1, (int) N2, isGazeControl, layer, set, dctCof);
		      	      	R1.start();
		      	     
		      	      	try {
							Thread.sleep(1000);
						
						} catch (InterruptedException e) {
					         System.out.println("Main thread interrupted.");
					    }
		                //doDecode(currentFrameIdx, gazeX, gazeY, (int) N1, (int) N2, isGazeControl, layer, set, dctCof);
		                BlockUtil.fillFrame(currentFrameIdx, mydisplayFrame, dctCof, layer, lastDisplay);
		                ++currentFrameIdx;
	            	}
					
	                // show frame here, use the byte array displayFrame
					System.out.println("r2 : " + (System.currentTimeMillis() - begin));
					PlayThread R2 = new PlayThread( "Play", mydisplayFrame, currentFrameIdx);
		      	    R2.start();
		      	    playFlag = R2.getPlayFlag();
	                //playFlag = PlayerUtil.doPlay(mydisplayFrame,currentFrameIdx);
	                 
		      	    
		      	  
	                int[] gaze=PlayerUtil.getMouse();
	                if (gaze != null) {
//	                    System.out.println(gaze[0] + " " + gaze[1]);
	                    gazeX = gaze[0];
	                    gazeY = gaze[1];
	                }
	                
	            }
	            long end = System.currentTimeMillis();
	            System.out.println("finish at:" + end + " last for " + (end - begin));
	           
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
		
	}
	
	   
			
		public void read(InputStream is, byte[] buf, short[] layers, int[][] dctCof) {
			// read byte data from file
			int offset = 0;
			int numRead = 0;
			try {
			  while (offset < buf.length
			          && (numRead = is.read(buf, offset, buf.length - offset)) >= 0) {
			      offset += numRead;
			  }
			} catch (IOException e) {
			  e.printStackTrace();
			}
			// convert every two byte data to one short data
			int blockIdx = 0;
			int i = 0;
			while (blockIdx < blockCount) {
			  byte first = buf[i];
			  byte second = buf[i + 1];
			  i = i + 2;
			
			  short layer = (short) ((first & 0xFF) << 8 | (second & 0XFF));
			  layers[blockIdx] = layer;
			  int j = 0;
			  while (j < dctBlkLen * dctBlkLen * 3) {
			      first = buf[i];
			      second = buf[i + 1];
			      i = i + 2;
			
			      short dctVal = (short) ((first & 0xFF) << 8 | (second & 0XFF));
			      dctCof[blockIdx][j] = dctVal;
			
			      ++j;
			  }
			  ++blockIdx;
			}
		}
}


	


class TransferThread implements Runnable {
	   private Thread t;
	   private String threadName;
	   private int currentFrameIdx;
	   private int gazeX; 
	   private int gazeY; 
	   private int N1; 
	   private int N2; 
	   private boolean isGazeControl;
	   private short[] layer;
	   private Set<Integer> set;
	   private int[][] dctCof;
       
	   TransferThread( String name, int idx, int gazeX, int gazeY, int N1, int N2, boolean isGazeControl,
               short[] layers, Set<Integer> lastForeground, int[][] dctCof) {
	      threadName = name;
	      this.currentFrameIdx = idx;
	      this.N1 = N1;
	      this.N2 = N2;
	      this.gazeX = gazeX;
	      this.gazeY = gazeY;
	      this.isGazeControl = isGazeControl;
	      this.layer = layers;
	      this.set = lastForeground;
	      this.dctCof = dctCof;
	      
	      System.out.println("Creating " +  threadName  + currentFrameIdx);
	   }
	   
	   public void run() {
	      System.out.println("Running " +  threadName  + currentFrameIdx);
	      try {
	    	  // TO-DO
	    	  
	    	  
	    	  doDecode(currentFrameIdx, gazeX, gazeY, (int) N1, (int) N2, isGazeControl, layer, set, dctCof);
	    	  
	    	  
	    	  
	    	  Thread.sleep(1);
	      }catch (InterruptedException e) {
	         System.out.println("Thread " +  threadName + currentFrameIdx + " interrupted.");
	      }
	      System.out.println("Thread " +  threadName  + currentFrameIdx + " exiting.");
	   }
	   
	   public void start () {
	      System.out.println("Starting " +  threadName  + currentFrameIdx);
	      if (t == null) {
	         t = new Thread (this, threadName);
	         t.start ();
	      }
	   }
	   
	   public void doDecode(int idx, int gazeX, int gazeY, int N1, int N2, boolean isGazeControl,
               short[] layers, Set<Integer> lastForeground, int[][] dctCof) {
			Set<Integer> gazeBlocks = null;
			if (isGazeControl && (gazeX >= 0 && gazeY >= 0)) {
			  gazeBlocks = BlockUtil.getBlockIdxAroundPoint(gazeX, gazeY, 32);
			}
			Set<Integer> foreground = new HashSet<>();
			int[][] rowColBlock = new int[Configuration.DCT_BLOCK_LEN][Configuration.DCT_BLOCK_LEN];
			
			for (int i = 0; i < layers.length; ++i) {
			  int quantization = (layers[i] != 0) ? N1 : N2;
			  if (layers[i] != 0) {
			      foreground.add(i);
			  }
			  if (gazeBlocks != null && gazeBlocks.contains(i)) {
			      quantization = 1;
			      layers[i] = 2;
			  }
			  if (quantization > 1 && idx % Configuration.PASS_FRAME_RATE != 0) {
			//      if (!lastForeground.contains(i)) {
			//          continue;
			//      }
			      if (isAroundBackground(i, layers)) {
			          continue;
			      }
			  }
			  // for R G B channel
			  for (int j = 0; j < Configuration.CHANNEL_NUM; ++j) {
			      int idxBase = Configuration.DCT_BLOCK_LEN * Configuration.DCT_BLOCK_LEN * j;
			      ArrayUtil.oneDToTwoD(dctCof[i], idxBase, rowColBlock);
			
			      int[] oneDDCT = new int[64];
			      ArrayUtil.twoDToOneD(rowColBlock, oneDDCT);
			      int[] iDCTVal = DCTUtil.aanIDCT(quantization, oneDDCT);
			      //int[][] iDCTVal = DCTUtil.doiDCTN3(quantization, rowColBlock);
			      ArrayUtil.fill(iDCTVal, dctCof[i], idxBase);
			  }
			}
			lastForeground.clear();
			lastForeground.addAll(foreground);
			}
			
		public static boolean isAroundBackground(int blockIdx, short[] layers){
			int xBlockCount = (Configuration.WIDTH + Configuration.DCT_BLOCK_LEN - 1) / Configuration.DCT_BLOCK_LEN;
			int yBlockCount = (Configuration.HEIGHT + Configuration.DCT_BLOCK_LEN - 1) / Configuration.DCT_BLOCK_LEN;
			
			int x = blockIdx % xBlockCount;
			int y = blockIdx / xBlockCount;
			if (x - 1 >= 0 && layers[blockIdx - 1] == 1) {
			  return false;
			}
			if (x + 1 < xBlockCount && layers[blockIdx + 1] == 1) {
			  return false;
			}
			if (y - 1 >= 0 && layers[blockIdx - xBlockCount] == 1) {
			  return false;
			}
			if (y + 1 < yBlockCount && layers[blockIdx + xBlockCount] == 1) {
			  return false;
			}
			return true;
			}
		
	}


class PlayThread implements Runnable {
	   private Thread t;
	   private String threadName;
	   private byte[] mydisplayFrame;
	   private int currentFrameIdx;
	   public boolean playFlag;
	   
	   PlayThread( String name, byte[] data, int frameNum) {
	      threadName = name;
	      mydisplayFrame = data;
	      currentFrameIdx = frameNum;
	      playFlag = true;
	      System.out.println("Creating " +  threadName  + currentFrameIdx);
	   }
	   
	   public void run() {
	      System.out.println("Running " +  threadName  + currentFrameIdx);
	      try {
	    	  // TO-DO
	    	  
	    	  playFlag = PlayerUtil.doPlay(mydisplayFrame,currentFrameIdx);
	    	  
	    	  
	    	  
	    	  
	    	  Thread.sleep(1);
	      }catch (InterruptedException e) {
	         System.out.println("Thread " +  threadName + currentFrameIdx + " interrupted.");
	      }
	      System.out.println("Thread " +  threadName + currentFrameIdx + " exiting.");
	   }
	   
	   public void start () {
	      System.out.println("Starting " +  threadName  + currentFrameIdx);
	      if (t == null) {
	         t = new Thread (this, threadName);
	         t.start ();
	      }
	   }
	   
	   public boolean getPlayFlag() {
	         return playFlag;
	   }
	}