/**
 * Copyright 2016- Mark C. Slee, Heron Arts LLC
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

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXModulatorComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameter;

public class LXAudioEngine extends LXModulatorComponent implements LXOscComponent {

  public BooleanParameter enabled =
    new BooleanParameter("Enabled", false)
    .setDescription("Sets whether the audio engine is active");

  /**
   * Audio input object
   */
  public LXAudioInput input = new LXAudioInput();

  public final LXAudioOutput output = new LXAudioOutput();

  public GraphicMeter meter = new GraphicMeter("Meter", this.input);

  public LXAudioEngine(LX lx) {
    super(lx, "Audio");
    addModulator(this.meter);
    addParameter("enabled", this.enabled);
  }

  public String getOscAddress() {
    return "/lx/audio";
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.enabled) {
      if (this.enabled.isOn()) {
        this.input.open();
        this.input.start();
      } else {
        this.input.stop();
      }
      this.meter.running.setValue(this.enabled.isOn());
    }
  }

  /**
   * Retrieves the audio input object at default sample rate of 44.1kHz
   *
   * @return Audio input object
   */
  public final LXAudioInput getInput() {
    return this.input;
  }

  /**
   * Change the audio input used by the audio engine.
   * Will also update the `meter` object.
   * Returns the LXAudioEngine for chaining.
   *
   * @return LXAudioEngine
   */
  public LXAudioEngine changeInput(LXAudioInput input) {
    if (this.input != null) {
      this.input.close();
    }
    if (this.meter != null) {
      removeModulator(this.meter);
    }
    this.input = input;
    this.meter = new GraphicMeter("Meter", this.input);
    addModulator(this.meter);
    return this;
  }

  @Override
  public void dispose() {
    this.input.close();
    super.dispose();
  }

  private static final String KEY_METER = "meter";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_METER, LXSerializable.Utils.toObject(lx, this.meter));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    if (obj.has(KEY_METER)) {
      this.meter.load(lx, obj.getAsJsonObject(KEY_METER));
    }
    super.load(lx, obj);
  }

}
