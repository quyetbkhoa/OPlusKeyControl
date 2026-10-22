package dev.opluskeycontrol.actions;

import de.sciss.jump3r.mp3.*;
import de.sciss.jump3r.mpg.Common;
import de.sciss.jump3r.mpg.Interface;
import de.sciss.jump3r.mpg.MPGLib;
import java.io.Closeable;

/** Mono PCM16 to genuine MPEG Layer III. Uses the core API, not javax.sound. */
public final class Mp3Encoder implements Closeable {
    private final Lame lame = new Lame();
    private final LameGlobalFlags flags;
    private final int[] samples = new int[4096];
    private boolean closed;
    private boolean finished;

    public Mp3Encoder() {
        GainAnalysis gain = new GainAnalysis();
        BitStream bits = new BitStream();
        Presets presets = new Presets();
        QuantizePVT quantizePrivate = new QuantizePVT();
        Quantize quantize = new Quantize();
        VBRTag vbr = new VBRTag();
        Version version = new Version();
        ID3Tag tags = new ID3Tag();
        Reservoir reservoir = new Reservoir();
        Takehiro takehiro = new Takehiro();
        MPGLib mpg = new MPGLib();
        Interface decoder = new Interface();
        Common common = new Common();
        lame.setModules(gain, bits, presets, quantizePrivate, quantize, vbr, version, tags, mpg);
        bits.setModules(gain, mpg, version, vbr);
        tags.setModules(bits, version);
        presets.setModules(lame);
        quantize.setModules(bits, reservoir, quantizePrivate, takehiro);
        quantizePrivate.setModules(takehiro, reservoir, lame.enc.psy);
        reservoir.setModules(bits);
        takehiro.setModules(quantizePrivate);
        vbr.setModules(lame, bits, version);
        mpg.setModules(decoder, common);
        decoder.setModules(vbr, common);
        flags = lame.lame_init();
        flags.num_channels = 1;
        flags.in_samplerate = 44100;
        flags.out_samplerate = 44100;
        flags.mode = MPEGMode.MONO;
        flags.brate = 128;
        flags.quality = 5;
        flags.VBR = VbrMode.vbr_off;
        flags.bWriteVbrTag = false; // Streaming output has no seek-back requirement.
        flags.write_id3tag_automatic = false;
        flags.findReplayGain = false;
        if (lame.lame_init_params(flags) < 0) {
            close();
            throw new IllegalStateException("MP3 encoder initialization failed");
        }
    }

    public int encode(short[] pcm, int count, byte[] output) {
        if (closed || finished || count < 0 || count > pcm.length || count > samples.length)
            throw new IllegalStateException("Invalid encoder input/state");
        // LAME's int API expects signed PCM scaled to the 32-bit range.
        for (int i = 0; i < count; i++) samples[i] = pcm[i] << 16;
        return checked(lame.lame_encode_buffer_int(flags, samples, samples, count, output, 0, output.length));
    }

    public int finish(byte[] output) {
        if (closed || finished) throw new IllegalStateException("Encoder already finished");
        finished = true;
        return checked(lame.lame_encode_flush(flags, output, 0, output.length));
    }

    private static int checked(int result) {
        if (result < 0) throw new IllegalStateException("MP3 encoding failed: " + result);
        return result;
    }

    @Override public void close() {
        if (!closed) { closed = true; lame.lame_close(flags); }
    }
}
