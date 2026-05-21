package Main;
import javax.swing.JFrame;

class Main {
    public static void main(String[] args) {
        JFrame window = new JFrame();
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);
        window.setTitle("Pokemon Bronze Brick");

        GamePanel gamePanel = new GamePanel();
        window.add(gamePanel);
        window.pack();
        window.setLocationRelativeTo(null);

        // setupGame() populates gp.obj; the EDT iterates that list in paintComponent.
        // Finish populating before the window becomes visible so the first paint can't
        // race with obj.add() and throw ConcurrentModificationException.
        gamePanel.setupGame();
        window.setVisible(true);
        gamePanel.startGameThread();
    }
}
