package com.sokbantv.game;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

/**
 * Small runtime-synthesized sound effects. Sounds are generated in code so the
 * game needs no bundled audio assets. Each effect uses its own preloaded
 * MODE_STATIC AudioTrack, which is the reliable way to replay short blips.
 */
public final class SoundEffects {

    private static final String TAG = "SokobanSound";
    private static final int SAMPLE_RATE = 22050;
    private static final float VOLUME = 0.6f;

    private final AudioTrack stepTrack;
    private final AudioTrack pushTrack;
    private final AudioTrack undoTrack;
    private final AudioTrack winTrack;

    public SoundEffects() {
        stepTrack = build(synthStep());
        pushTrack = build(synthPush());
        undoTrack = build(synthUndo());
        winTrack = build(synthWin());
    }

    public void playStep() {
        play(stepTrack);
    }

    public void playPush() {
        play(pushTrack);
    }

    public void playUndo() {
        play(undoTrack);
    }

    public void playWin() {
        play(winTrack);
    }

    public void release() {
        release(stepTrack);
        release(pushTrack);
        release(undoTrack);
        release(winTrack);
    }

    private AudioTrack build(short[] samples) {
        AudioTrack track = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                samples.length * 2,
                AudioTrack.MODE_STATIC);
        setVolume(track, VOLUME);
        track.write(samples, 0, samples.length);
        return track;
    }

    /** AudioTrack#setVolume(float) is API 21; older releases only have the stereo setter. */
    @SuppressWarnings("deprecation")
    private static void setVolume(AudioTrack track, float volume) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            track.setVolume(volume);
        } else {
            track.setStereoVolume(volume, volume);
        }
    }

    private void play(AudioTrack track) {
        if (track == null) {
            return;
        }
        try {
            int state = track.getPlayState();
            if (state == AudioTrack.PLAYSTATE_PLAYING
                    || state == AudioTrack.PLAYSTATE_PAUSED) {
                track.stop();
            }
            track.reloadStaticData();
            track.play();
        } catch (IllegalStateException e) {
            Log.w(TAG, "play failed, trying fallback", e);
            try {
                track.play();
            } catch (IllegalStateException ignored) {
                // Released or unusable; drop the sound.
            }
        }
    }

    private void release(AudioTrack track) {
        if (track == null) {
            return;
        }
        try {
            track.stop();
        } catch (IllegalStateException ignored) {
            // Never played or already stopped.
        }
        track.release();
    }

    // ------------------------------------------------------------------
    // synthesis
    // ------------------------------------------------------------------
    private static short[] synthStep() {
        return tone(660.0, 60, 0.6, 1, 45);
    }

    private static short[] synthPush() {
        return tone(160.0, 130, 0.85, 2, 110);
    }

    private static short[] synthUndo() {
        return concat(
                tone(523.25, 90, 0.55, 2, 70),
                tone(349.23, 90, 0.55, 2, 70));
    }

    private static short[] synthWin() {
        return concat(
                tone(523.25, 140, 0.65, 5, 120),
                tone(659.25, 140, 0.65, 5, 120),
                tone(783.99, 140, 0.65, 5, 120),
                tone(1046.50, 340, 0.8, 5, 290));
    }

    /** One sine note with a short attack and a linear release fade. */
    private static short[] tone(double hz, double ms, double gain,
            double attackMs, double releaseMs) {
        int count = (int) (SAMPLE_RATE * ms / 1000.0);
        short[] out = new short[count];
        int attack = (int) (SAMPLE_RATE * attackMs / 1000.0);
        int release = (int) (SAMPLE_RATE * releaseMs / 1000.0);
        for (int i = 0; i < count; i++) {
            double env = 1.0;
            if (i < attack) {
                env = attack <= 0 ? 1.0 : (double) i / attack;
            } else if (i >= count - release) {
                env = release <= 0 ? 1.0 : (double) (count - i) / release;
            }
            double t = (double) i / SAMPLE_RATE;
            double value = Math.sin(2.0 * Math.PI * hz * t) * gain * env;
            out[i] = (short) (value * Short.MAX_VALUE);
        }
        return out;
    }

    private static short[] concat(short[]... parts) {
        int total = 0;
        for (short[] part : parts) {
            total += part.length;
        }
        short[] out = new short[total];
        int pos = 0;
        for (short[] part : parts) {
            System.arraycopy(part, 0, out, pos, part.length);
            pos += part.length;
        }
        return out;
    }
}
