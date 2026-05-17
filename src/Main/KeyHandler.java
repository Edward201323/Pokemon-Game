package Main;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class KeyHandler implements KeyListener {
    public boolean upPressed, downPressed, leftPressed, rightPressed;
    public boolean zPressed, xPressed, cPressed, vPressed, iPressed, pPressed;

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        setKey(e.getKeyCode(), true);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        setKey(e.getKeyCode(), false);
    }

    private void setKey(int code, boolean pressed) {
        switch (code) {
            case KeyEvent.VK_W:
            case KeyEvent.VK_UP:
                upPressed = pressed;
                break;
            case KeyEvent.VK_S:
            case KeyEvent.VK_DOWN:
                downPressed = pressed;
                break;
            case KeyEvent.VK_A:
            case KeyEvent.VK_LEFT:
                leftPressed = pressed;
                break;
            case KeyEvent.VK_D:
            case KeyEvent.VK_RIGHT:
                rightPressed = pressed;
                break;
            case KeyEvent.VK_Z: zPressed = pressed; break;
            case KeyEvent.VK_X: xPressed = pressed; break;
            case KeyEvent.VK_C: cPressed = pressed; break;
            case KeyEvent.VK_V: vPressed = pressed; break;
            case KeyEvent.VK_P: pPressed = pressed; break;
            case KeyEvent.VK_I: iPressed = pressed; break;
        }
    }
}
