/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * @author Mark C. Slee <mark@heronarts.com>
 */

package heronarts.lx.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

public class LXAudioInput extends LXAudioBuffer implements LineListener {
  private static final int SAMPLE_BUFFER_SIZE = 512;
  private static final int BYTES_PER_SAMPLE = 2;

  private static final int DEFAULT_NUM_CHANNELS = 2;
  private static final int DEFAULT_FRAME_SIZE = BYTES_PER_SAMPLE * DEFAULT_NUM_CHANNELS;
  private static final int DEFAULT_INPUT_DATA_SIZE = SAMPLE_BUFFER_SIZE * DEFAULT_FRAME_SIZE;

  private TargetDataLine line;
  private AudioFormat format;

  private byte[] rawBytes;
  private int frameSize;
  private int inputDataSize;
  private int numChannels;

  public final LXAudioBuffer left = new LXAudioBuffer(SAMPLE_BUFFER_SIZE);
  public final LXAudioBuffer right = new LXAudioBuffer(SAMPLE_BUFFER_SIZE);
  public final LXAudioBuffer mix = this;

  private boolean closed = true;
  private boolean stopped = false;

  private InputThread inputThread = null;

  private class InputThread extends Thread {
    private final int numChannels;
    private final int inputDataSize;
    private final int frameSize;

    private InputThread(int numChannels, int inputDataSize, int frameSize) {
      super("LXAudioEngine Input Thread");
      this.numChannels = numChannels;
      this.inputDataSize = inputDataSize;
      this.frameSize = frameSize;
    }

    @Override
    public void run() {
      while (!closed) {
        while (stopped) {
          if (closed) {
            return;
          }
          try {
            synchronized (this) {
              wait();
            }
          } catch (InterruptedException ix) {}
        }

        // Read from the audio line
        line.read(rawBytes, 0, rawBytes.length);

        // Put the left and right buffers if stereo
        if (this.numChannels == 2) {
          left.putSamples(rawBytes, 0, this.inputDataSize, this.frameSize);
          right.putSamples(rawBytes, 2, this.inputDataSize, this.frameSize);
          computeMix(left, right);
        } else {
          putSamples(rawBytes, 0, this.inputDataSize, this.frameSize);
        }
      }
    }

  };

  public LXAudioInput() {
    this(new AudioFormat(SAMPLE_RATE, 8 * BYTES_PER_SAMPLE, DEFAULT_NUM_CHANNELS, true, false));
  }

  public LXAudioInput(AudioFormat format) {
    super(SAMPLE_BUFFER_SIZE);
    this.setAudioFormat(format);
  }

  /**
   * Sets a new audio format.
   * Returns this for chaining.
   *
   * @return LXAudioInput
   */
  public LXAudioInput setAudioFormat(AudioFormat format) {
    this.format = format;
    this.numChannels = format.getChannels();
    this.frameSize = BYTES_PER_SAMPLE * this.numChannels;
    this.inputDataSize = SAMPLE_BUFFER_SIZE * this.frameSize;
    this.rawBytes = new byte[this.inputDataSize];
    return this;
  }

  public AudioFormat getFormat() {
    return this.format;
  }

  void open() {
    if (this.line == null) {
      DataLine.Info info = new DataLine.Info(TargetDataLine.class, this.format);
      if (!AudioSystem.isLineSupported(info)) {
        System.err.println("AudioSystem does not support stereo 16-bit input");
        return;
      }
      try {
        this.line = this.getTargetDataLine(info);
        this.line.addLineListener(this);
        this.line.open(this.format, this.inputDataSize * this.numChannels);
        this.line.start();
        this.stopped = false;
        this.closed = false;
        this.inputThread = new InputThread(this.numChannels, this.inputDataSize, this.frameSize);
        this.inputThread.start();
      } catch (Exception x) {
        System.err.println(x.getLocalizedMessage());
        return;
      }
    }
  }

  void start() {
    if (this.line == null) {
      throw new IllegalStateException("Cannot start() LXAudioInput before open()");
    }
    this.stopped = false;
    this.line.start();
    synchronized (this.inputThread) {
      this.inputThread.notify();
    }
  }

  void stop() {
    if (this.line == null) {
      throw new IllegalStateException("Cannot stop() LXAudioInput before open()");
    }
    this.stopped = true;
    this.line.stop();
  }

  void close() {
    if (this.line != null) {
      this.line.flush();
      stop();
      this.closed = true;
      this.line.close();
      this.line = null;
      synchronized (this.inputThread) {
        this.inputThread.notify();
      }
      try {
        this.inputThread.join();
      } catch (InterruptedException ix) {
        ix.printStackTrace();
      }
      this.inputThread = null;
    }
  }

  /*
   * Retrieves the default TargetDataLine that matches the defined format.
   * Override to retrieve a more specific TargetDataLine from the AudioSystem.
   *
   */
  protected TargetDataLine getTargetDataLine(DataLine.Info info)
    throws LineUnavailableException {
    return (TargetDataLine) AudioSystem.getLine(info);
  }

  @Override
  public void update(LineEvent event) {
    LineEvent.Type type = event.getType();
    if (type == LineEvent.Type.OPEN) {
    } else if (type == LineEvent.Type.START) {
    } else if (type == LineEvent.Type.STOP) {
      this.stopped = true;
    } else if (type == LineEvent.Type.CLOSE) {
      this.closed = true;
    }
  }
}
