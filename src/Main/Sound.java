package Main;
import java.io.File;
import java.net.URL;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

// Each Sound instance owns its own Clip, so a Sound used for music
// is not clobbered when another Sound plays a sound effect.
public class Sound {
    private Clip clip;
    private final URL[] musicUrls = new URL[30];
    private final URL[] seUrls = new URL[2];
    private long timeStopped;

    public Sound() {
        loadFiles("./src/res/music", "/res/music/", musicUrls);
        loadFiles("./src/res/SoundEffects", "/res/SoundEffects/", seUrls);
    }

    private void loadFiles(String dirPath, String resourcePrefix, URL[] dest) {
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
                    dest[index] = getClass().getResource(resourcePrefix + fileName);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public void setMusicFile(int i) {
        openClip(musicUrls[i]);
    }

    public void setSEFile(int i) {
        openClip(seUrls[i]);
    }

    private void openClip(URL url) {
        if (url == null) return;
        try {
            if (clip != null) clip.close();
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(url);
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
