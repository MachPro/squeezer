package util;

import conf.Configuration;
import java.awt.*;
import java.awt.image.*;
import javax.swing.*;
import java.awt.event.*;


public class PlayerUtil {
	public static int width = Configuration.WIDTH;

    public static int height = Configuration.HEIGHT;	
		
	private static boolean playFlag=true;
	
    private static int frameLen;
	private static JFrame myFrame;
    private static JLabel myImgLabel=new JLabel(),lbText1,lbText2;
    private static JPanel myPanel;
    private static GridBagConstraints constraint;
    private static BufferedImage img;
    private static Point p;
    
	private static JButton playButton;         
	private static JButton pauseButton;
	private static JButton toggleButton;
    
    private static long pauseTime=0;
    private static long restartTime=0;
    private static long waitTime=0;
    private static long totalWaitTime=0;
	
	/**
	 * Control the window to display video
	 */
	private static void initPanel()  
	{ 
		// �½���� 
        myPanel = new JPanel();            

        
        toggleButton = new JButton("PAUSE");

        toggleButton.addActionListener( 
                new ActionListener() 
                { 
                    public void actionPerformed(ActionEvent e) 
                    { 
                    	if(playFlag){
                    		toggleButton.setText("PLAY");
                    		pauseTime=System.currentTimeMillis();
//                        	System.out.println("Video Paused!");
                    	}
                    	else{
                    		toggleButton.setText("PAUSE");
                    		restartTime=System.currentTimeMillis();
                        	waitTime=restartTime-pauseTime;
//                        	System.out.println("Video Play!");
                    	}
                    	playFlag=!playFlag;
                    } 
                } 
        ); 
     
        myPanel.add(toggleButton);
	} 
	
	public static long getWaitTime(){
		totalWaitTime+=waitTime;
		waitTime=0;
		return totalWaitTime;
	}
	

	/**
	 * Control the mouse event
	 */
	private static class adapter extends MouseAdapter {  
		  @Override public void mouseMoved(MouseEvent event) {
			p.x=event.getX();
			p.y=event.getY();
		    //System.out.println("Mouse moved! Pos is: " + event.getX()+ "," + event.getY() + ".");
		  }		  
		  
	}
	
	public static void resetTime(){
		totalWaitTime=0;
		waitTime=0;
	}
	
	public static void initPlayer(int frameCount){
        frameLen=frameCount;        
		initPanel();
        
        myFrame = new JFrame();
        myFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        
        p = new Point();
        
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
        myFrame.getContentPane().add(myPanel, constraint);     
	}
	
	
	public static int[] getMouse(){
        //Return the mouse position
        int[] pos={p.x,p.y};
        return pos;
	}
	
	/**
	 * Display every frame as image
	 * 
	 * @param data	the rgb data in byte to display
	 */
	public static boolean doPlay(byte[] data, int frameNum){		
        img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);;
        
        //Obtain the rgb value of the images        
        int index = 0;
        for(int y = 0; y < height; y++){ 
            for(int x = 0; x < width; x++){           
                byte r = data[index];
                byte g = data[index+height*width];
                byte b = data[index+height*width*2];
            
                int pixel = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                img.setRGB(x,y,pixel);
                index++;
            }
        }

        //Update the information displayed in the panel
        String videoInfo = String.format("height*width: %d*%d, frame: %3d/%d", height, width,frameNum,frameLen);
        String gazeControlInfo = String.format("Loc:x=%d,y=%d  ", p.x,p.y);
        lbText1.setText(videoInfo);
        lbText2.setText(gazeControlInfo);
        
        
        //Update the image
        myImgLabel = new JLabel(new ImageIcon(img));
        MouseAdapter adapter = new adapter();
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