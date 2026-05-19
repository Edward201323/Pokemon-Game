package Main;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class KeyHandler implements KeyListener {
    public boolean upPressed, downPressed, leftPressed, rightPressed;
    public boolean zPressed, xPressed, cPressed, vPressed, iPressed, pPressed;
    // Enter is the primary "confirm"; Escape is the universal "cancel / back".
    public boolean enterPressed;
    public boolean escPressed;
    // Held as the modifier half of the Shift+S save / Shift+D delete hotkeys. Tracked
    // separately so we can suppress player movement while Shift is held.
    public boolean shiftPressed;
    // Raw D key (also aliased to rightPressed for WASD movement). Exposed on its own so
    // TitleScreen can detect Shift+D specifically without confusing it with Shift+Right.
    public boolean dPressed;

    // Free-text capture for the name-entry screen. When `captureText` is true, each
    // keyTyped event appends to `typedBuffer` (backspace removes the last char).
    // Other UIs ignore this entirely — they read xPressed/zPressed/etc.
    public final StringBuilder typedBuffer = new StringBuilder();
    public boolean captureText = false;
    private static final int TYPED_BUFFER_MAX = 32;

    @Override
    public void keyTyped(KeyEvent e) {
        if (!captureText) return;
        char c = e.getKeyChar();
        if (c == '\b') {
            if (typedBuffer.length() > 0) typedBuffer.deleteCharAt(typedBuffer.length() - 1);
        } else if (c >= 32 && c < 127) {
            if (typedBuffer.length() < TYPED_BUFFER_MAX) typedBuffer.append(c);
        }
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
                rightPressed = pressed;
                dPressed = pressed;
                break;
            case KeyEvent.VK_RIGHT:
                rightPressed = pressed;
                break;
            case KeyEvent.VK_Z: zPressed = pressed; break;
            case KeyEvent.VK_X: xPressed = pressed; break;
            // Escape is its own key — used as universal "back / cancel" in menus.
            // (Not aliased to X, so it can mean "go back" in the move menu without also
            // triggering whatever X does there.)
            case KeyEvent.VK_ESCAPE: escPressed = pressed; break;
            case KeyEvent.VK_C: cPressed = pressed; break;
            case KeyEvent.VK_V: vPressed = pressed; break;
            case KeyEvent.VK_P: pPressed = pressed; break;
            case KeyEvent.VK_I: iPressed = pressed; break;
            case KeyEvent.VK_ENTER: enterPressed = pressed; break;
            case KeyEvent.VK_SHIFT: shiftPressed = pressed; break;
        }
    }
}
