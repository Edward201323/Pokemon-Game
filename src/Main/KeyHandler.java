package Main;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
public class KeyHandler implements KeyListener{
    public boolean upPressed, downPressed, leftPressed, rightPressed, 
    zPressed, xPressed, cPressed, vPressed, iPressed, pPressed;
 
    @Override
    public void keyTyped(KeyEvent e) {
    }
 
    @Override
    public void keyPressed(KeyEvent e) {
 
        int code = e.getKeyCode();
        //Movement
        if(code == KeyEvent.VK_W || code == KeyEvent.VK_UP){
            upPressed = true;
        }
        if(code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN){
            downPressed = true;
        }
        if(code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT){
            leftPressed = true;
        }
        if(code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT){
            rightPressed = true;
        }
 
        //Encounter Keys
        if(code==KeyEvent.VK_Z){
            zPressed = true;
        }
        if(code==KeyEvent.VK_X){
            xPressed = true;
        }
        if(code==KeyEvent.VK_C){
            cPressed = true;
        }
        if(code==KeyEvent.VK_V){
            vPressed = true;
        }
        if(code==KeyEvent.VK_P){
            pPressed = true;
        }
        if(code==KeyEvent.VK_I){
            iPressed = true;
        }
    }
 
    @Override
    public void keyReleased(KeyEvent e) {
 
        int code = e.getKeyCode();
 
        if(code == KeyEvent.VK_W || code == KeyEvent.VK_UP){
            upPressed = false;
        }
        if(code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN){
            downPressed = false;
        }
        if(code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT){
            leftPressed = false;
        }
        if(code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT){
            rightPressed = false;
        }
 
        //Encounter Keys
        if(code==KeyEvent.VK_Z){
            zPressed = false;
        }
        if(code==KeyEvent.VK_X){
            xPressed = false;
        }
        if(code==KeyEvent.VK_C){
            cPressed = false;
        }
        if(code==KeyEvent.VK_V){
            vPressed = false;
        }
        if(code==KeyEvent.VK_V){
            vPressed = false;
        }
        if(code==KeyEvent.VK_P){
            pPressed = false;
        }
        if(code==KeyEvent.VK_I){
            iPressed = false;
        }
    }
 
}
 