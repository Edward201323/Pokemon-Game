package Main;
import java.io.File;
import java.net.URL;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
public class Sound {
    Clip clip;
    URL musicUrls[] = new URL[30]; //sound path of sound files
    URL SEUrls[] = new URL [2];
    int timeStopped;

    public Sound(){
        getMusic();
        getSE();
    }

    public void getMusic(){
        File directory = new File("./src/res/music");
        File[] files = directory.listFiles();
        for(File file: files){
            String fileName = file.getName();
            String[] parts = fileName.split("_");
            int fileIndex = Integer.parseInt(parts[0]);
            musicUrls[fileIndex] = getClass().getResource("/res/music/"+fileName);
        }
    }

    public void setMusicFile(int i){
        try {
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(musicUrls[i]);
            clip = AudioSystem.getClip();
            clip.open(audioStream);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    
    public void getSE(){
        File directory = new File("./src/res/SoundEffects");
        File[] files = directory.listFiles();
        for(File file: files){
            String fileName = file.getName();
            String[] parts = fileName.split("_");
            int fileIndex = Integer.parseInt(parts[0]);
            SEUrls[fileIndex] = getClass().getResource("/res/SoundEffects/"+fileName);
        }
    }
    
    public void setSEFile(int i){
        try {
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(SEUrls[i]);
            clip = AudioSystem.getClip();
            clip.open(audioStream);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void play(){
        clip.start();
    }

    public void loop(){
        clip.loop(Clip.LOOP_CONTINUOUSLY);
    }

    public void stop(Boolean startLater){
        if(startLater == true){
            timeStopped = (int) clip.getMicrosecondPosition();
        }
        clip.stop();
    }

    public void resume() {
        clip.setMicrosecondPosition(timeStopped);
        clip.start();
    }
}
