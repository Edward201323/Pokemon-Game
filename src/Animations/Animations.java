package Animations;
import java.awt.Color;
import java.awt.Graphics2D;
import Main.GamePanel;
public class Animations {
    GamePanel gp;
    Graphics2D g2;
 
    public int counterA;
 
    public int counterB;
    public Animations(GamePanel gp){
        this.gp = gp;
        counterA = 0;
        counterB = 0;
    }
 
    public void FadeIn(Graphics2D g2){
        counterA++;
        if(counterA * 3 <= 255){
            Color c = new Color(0, 0, 0, (counterA*3));
            g2.setColor(c);
            g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);
        }
        if(counterA * 3 > 255){
            Color c = new Color(0, 0, 0, 255);
            g2.setColor(c);
            g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);
        }
    }
 
    public void FadeOut(Graphics2D g2){
        counterB++;
        if(255-counterB * 3>=0){
            Color c = new Color(0, 0, 0, 255-(counterB * 3));
            g2.setColor(c);
            g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);
        }
    }
}