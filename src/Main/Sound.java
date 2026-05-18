package Main;
import java.io.File;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

// Each Sound instance owns its own Clip, so a Sound used for music
// is not clobbered when another Sound plays a sound effect.
public class Sound {
    private Clip clip;
    private final File[] musicFiles = new File[30];
    private final File[] seFiles = new File[2];
    private long timeStopped;

    public Sound() {
        loadFiles("./src/res/music", musicFiles);
        loadFiles("./src/res/SoundEffects", seFiles);
    }

    private void loadFiles(String dirPath, File[] dest) {
        File directory = new File(dirPath);
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            String fileName = file.getName();
            if (fileName.startsWith(".")) continue;
            int underscore = fileName.indexOf('_');
            if (underscore < 0) continue;
            try {
                int index = Integer.parseInt(fileName.substring(0, underscore));
                if (index >= 0 && index < dest.length) {
                    dest[index] = file;
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public void setMusicFile(int i) {
        openClip(musicFiles[i]);
    }

    public void setSEFile(int i) {
        openClip(seFiles[i]);
    }

    private void openClip(File file) {
        if (file == null) return;
        try {
            if (clip != null) clip.close();
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);
            clip = AudioSystem.getClip();
            clip.open(audioStream);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void play() {
        if (clip != null) clip.start();
    }

    public void loop() {
        if (clip != null) clip.loop(Clip.LOOP_CONTINUOUSLY);
    }

    public void stop(boolean rememberPosition) {
        if (clip == null) return;
        if (rememberPosition) timeStopped = clip.getMicrosecondPosition();
        clip.stop();
    }

    public void resume() {
        if (clip == null) return;
        clip.setMicrosecondPosition(timeStopped);
        clip.start();
    }
}
