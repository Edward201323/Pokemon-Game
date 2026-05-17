package tile;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import javax.imageio.ImageIO;
import Main.GamePanel;

public class TileManager {
    private static final int TILE_COUNT = 266;
    private final GamePanel gp;
    public final Tile[] tile;
    public final int[][] mapTileNum;

    public TileManager(GamePanel gp) {
        this.gp = gp;
        this.tile = new Tile[TILE_COUNT];
        for (int i = 0; i < TILE_COUNT; i++) tile[i] = new Tile();
        this.mapTileNum = new int[gp.maxWorldCol][gp.maxWorldRow];

        loadTileImages();
        loadMap("Route1.txt");
    }

    private void loadTileImages() {
        File directory = new File("./src/res/tiles");
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            String fileName = file.getName();
            if (fileName.startsWith(".") || !fileName.toLowerCase().endsWith(".png")) continue;

            // Filename is either "<n>.png" or "<n>_false.png" (passable tile).
            String stem = fileName.substring(0, fileName.lastIndexOf('.'));
            int underscore = stem.indexOf('_');
            int index;
            try {
                index = Integer.parseInt(underscore < 0 ? stem : stem.substring(0, underscore));
            } catch (NumberFormatException e) {
                continue;
            }
            if (index < 0 || index >= TILE_COUNT) continue;

            tile[index].fileName = fileName;
            tile[index].collision = !stem.contains("_false");
            try {
                tile[index].image = ImageIO.read(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void loadMap(String fileName) {
        File file = new File("./src/res/maps/" + fileName);
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            for (int row = 0; row < gp.maxWorldRow; row++) {
                String line = reader.readLine();
                if (line == null) break;
                String[] numbers = line.split(" ");
                for (int col = 0; col < gp.maxWorldCol && col < numbers.length; col++) {
                    mapTileNum[col][row] = Integer.parseInt(numbers[col]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void draw(Graphics2D g2) {
        for (int row = 0; row < gp.maxWorldRow; row++) {
            for (int col = 0; col < gp.maxWorldCol; col++) {
                int tileNum = mapTileNum[col][row];
                BufferedImage img = tile[tileNum].image;
                if (img == null) continue;
                int screenX = col * gp.tileSize - gp.player.worldX + gp.player.screenX;
                int screenY = row * gp.tileSize - gp.player.worldY + gp.player.screenY;
                g2.drawImage(img, screenX, screenY, gp.tileSize, gp.tileSize, null);
            }
        }
    }
}
