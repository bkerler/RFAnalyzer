package com.mantz_it.rfanalyzer;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * <h1>RF Analyzer - File Source of IQ samples</h1>
 *
 * Module:      FileIQSource.java
 * Description: Simple source of IQ sampling by reading from IQ files generated by the
 *              HackRF. Just for testing.
 *
 * @author Dennis Mantz
 *
 * Copyright (C) 2014 Dennis Mantz
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
public class FileIQSource implements IQSourceInterface {
	private Callback callback = null;
	private boolean repeat = false;
	private int sampleRate = 0;
	private long frequency = 0;
	private int packetSize = 0;
	private byte[] buffer = null;
	private File file = null;
	private String filename = null;
	private BufferedInputStream bufferedInputStream = null;
	private static final String LOGTAG = "FileIQSource";

	public FileIQSource(String filename, int sampleRate, long frequency, int packetSize, boolean repeat) {
		this.filename = filename;
		this.file = new File(filename);
		this.repeat = repeat;
		this.sampleRate = sampleRate;
		this.frequency = frequency;
		this.packetSize = packetSize;
		this.buffer = new byte[packetSize];
	}

	private void reportError(String msg) {
		if(callback != null)
			callback.onIQSourceError(this,msg);
		else
			Log.e(LOGTAG,"Callback is null when reporting Error (" + msg + ")");
	}

	@Override
	public boolean open(Context context, Callback callback) {
		this.callback = callback;
		// open the file
		try {
			this.bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
			callback.onIQSourceReady(this);
			return true;
		}catch (IOException e) {
			Log.e(LOGTAG, "open: Error while opening file: " + e.getMessage());
			reportError("Error while opening file: " + e.getMessage());
			return false;
		}
	}

	@Override
	public boolean isOpen() {
		if(bufferedInputStream == null)
			return false;
		try {
			if(bufferedInputStream.available() > 0)
				return true;
		} catch (IOException e) {

		}
		return false;
	}

	@Override
	public boolean close() {
		// close the file
		try {
			if(bufferedInputStream != null)
				bufferedInputStream.close();
			return true;
		} catch (IOException e) {
			Log.e(LOGTAG, "stopSampling: Error while closing file: " + e.getMessage());
			reportError("Unexpected error while closing file: " + e.getMessage());
			return false;
		}
	}

	@Override
	public String getName() {
		return "IQ-File: " + file.getName();
	}

	/**
	 * @return the file name of the file
	 */
	public String getFilename() {
		return filename;
	}

	/**
	 * @return true if repeat is enabled; false if not
	 */
	public boolean isRepeat() {
		return repeat;
	}

	@Override
	public int getSampleRate() {
		return sampleRate;
	}

	@Override
	public void setSampleRate(int sampleRate) {
		Log.e(LOGTAG,"Setting the sample rate is not supported on a file source");
		reportError("Setting the sample rate is not supported on a file source");
	}

	@Override
	public long getFrequency() {
		return frequency;
	}

	@Override
	public void setFrequency(long frequency) {
		Log.e(LOGTAG,"Setting the frequency is not supported on a file source");
		reportError("Setting the frequency is not supported on a file source");
	}

	@Override
	public long getMaxFrequency() {
		return frequency;
	}

	@Override
	public long getMinFrequency() {
		return frequency;
	}

	@Override
	public int getMaxSampleRate() {
		return sampleRate;
	}

	@Override
	public int getMinSampleRate() {
		return sampleRate;
	}

	@Override
	public int getNextHigherOptimalSampleRate(int sampleRate) {
		return this.sampleRate;
	}

	@Override
	public int getNextLowerOptimalSampleRate(int sampleRate) {
		return this.sampleRate;
	}

	@Override
	public int getPacketSize() {
		return packetSize;
	}

	@Override
	public byte[] getPacket(int timeout) {
		if(bufferedInputStream == null)
			return null;

		try {
			// Simulate sample rate of real hardware:
			int sleepTime = Math.min((int)(packetSize/(float)sampleRate * 1000), timeout);
			Thread.sleep(sleepTime);

			// Read the samples.
			if(bufferedInputStream.read(buffer, 0 , buffer.length) != buffer.length) {
				if (repeat) {
					// rewind and try again:
					Log.i(LOGTAG,"getPacket: End of File. Rewind!");
					bufferedInputStream.close();
					this.bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
					if (bufferedInputStream.read(buffer, 0, buffer.length) != buffer.length)
						return null;
					else
						return buffer;
				} else {
					Log.i(LOGTAG, "getPacket: End of File");
					reportError("End of File");
					return null;
				}
			}
		} catch (IOException e) {
			Log.e(LOGTAG,"getPacket: Error while reading from file: " + e.getMessage());
			reportError("Unexpected error while reading file: " + e.getMessage());
			return null;
		} catch (InterruptedException e) {
			Log.w(LOGTAG, "getPacket: Interrupted while sleeping!");
			return null;
		}

		return buffer;
	}

	@Override
	public void returnPacket(byte[] buffer) {
		// do nothing
	}

	@Override
	public void startSampling() {
		// nothing to do here...
	}

	@Override
	public void stopSampling() {
		// nothing to do here...
	}

	@Override
	public int fillPacketIntoSamplePacket(byte[] packet, SamplePacket samplePacket, int startIndex) {
		/**
		 * The HackRF delivers samples in the following format:
		 * The bytes are interleaved, 8-bit, signed IQ samples (in-phase
		 *  component first, followed by the quadrature component):
		 *
		 *  [--------- first sample ----------]   [-------- second sample --------]
		 *         I                  Q                  I                Q ...
		 *  receivedBytes[0]   receivedBytes[1]   receivedBytes[2]       ...
		 */
		int count = 0;
		double[] re = samplePacket.re();
		double[] im = samplePacket.im();
		for (int i = 0; i < packet.length; i+=2) {
			re[startIndex+count] = packet[i] / 128.0;
			im[startIndex+count] = packet[i+1] / 128.0;
			count++;
			if(startIndex+count >= samplePacket.size())
				break;
		}
		return count;
	}
}
