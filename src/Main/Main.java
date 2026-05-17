
package Main;
import javax.swing.JFrame;
class Main{
    public static void main(String[]args){
        JFrame window = new JFrame();
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);
        window.setTitle("Pokemon Game");

        GamePanel gamePanel = new GamePanel();
        window.add(gamePanel);

        window.pack(); //causes window to be sized to fit the preferred size adn layouts of its subcomponets

        window.setLocationRelativeTo(null);
        window.setVisible(true);

        gamePanel.setupGame(); //sets up objects
        gamePanel.startGameThread();
    }
}