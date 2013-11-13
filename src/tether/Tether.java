package tether;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import android.util.Log;
import fi.sulautetut.android.tblueclient.TBlue;

public class Tether {
	
	private static Map<String, Tether> tethers = new HashMap<String, Tether>();
	
	private BTThread btt;
	private String TAG = "libtether";
	
	private String address;
	private TetherCallbacks tetherCallbacks;
	
	private double X;
	private double Y;
	private double Z;
	
	private int sX = 0;
	private int sY = 0;
	private int sZ = 0;
	
	/* Creates a Tether object with the given address and callbacks. */
	public static Tether makeTether(String addr, TetherCallbacks tCallbacks) {
		Tether tether = new Tether(addr, tCallbacks);
		Log.v("libtether", "Creating new tether object: " + tether);
		tethers.put(addr, tether);
		return tether;
	}
	
	/* Returns a Tether object given the address. */
	public static Tether getTether(String addr) {
		return tethers.get(addr);
	}
	
	public Tether(String addr, TetherCallbacks tCallbacks) {
		
		X = 0.0;
		Y = 0.0;
		Z = 0.0;
		
		tetherCallbacks = tCallbacks;
		address = addr;
		
		btt = null;
	}
	
	public double X() {
		return X;
	}
	
	public double Y() {
		return Y;
	}
	
	public double Z() {
		return Z;
	}
	
	public String toString() {
		return "<Tether: " + address + ">";
	}
	
	public void sendCommand(String command) {
		btt.pending_out_command = command;
	}
	
	public void start() {
		Log.v(TAG, "Tether start called!");
		if (btt == null) {
			btt = new BTThread(address);
			btt.start();
		}
	}
	
	public void stop() {
		if (btt != null) {
			btt.on = false;
			btt = null;
		}
	}
	
	public interface TetherCallbacks {
		public void connected();
		public void disconnected();
		public void positionUpdate(double X, double Y, double Z);
	}
	
	private class BTThread extends Thread {
		
		private TBlue bluetooth;
		private boolean on;
		private boolean connected;
		
		private String pending_out_command;
		
		private char END_COMMAND = '\n';
		private char DELIMITER = ' ';
		private long TIMEOUT = 3000;
		
		private String rx_buffer;
		
		private long last_command_time;
		
		public BTThread(String addr) {
			bluetooth = new TBlue(address);
			connected = false;
			on = true;
			pending_out_command = "";
			rx_buffer = "";
			last_command_time = System.currentTimeMillis();
		}
		
		private void commandReceived(String command) {
			Log.v(TAG, "Full command received: " + command + ", length: " + command.length());
			
			last_command_time = System.currentTimeMillis();
			
			/*
			byte[] bytes;
			try {
				bytes = command.getBytes("US-ASCII");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}
			
			char command_name = (char)bytes[0];
			Log.v(TAG, "Command name: " + command_name);
			
			// Check for data command
			if(command_name == 'D') {
				
				if (bytes.length != 8) {
					Log.e(TAG, "Got data command with length " + bytes.length + ", not 8!");
					return;
				}
				
				ByteBuffer bX = ByteBuffer.allocate(2);
				bX.order(ByteOrder.LITTLE_ENDIAN);
				bX.put(bytes[1]);
				bX.put(bytes[2]);
				sX = bX.getShort(0);
				
				ByteBuffer bY = ByteBuffer.allocate(2);
				bY.order(ByteOrder.LITTLE_ENDIAN);
				bY.put(bytes[3]);
				bY.put(bytes[4]);
				sY = bY.getShort(0);
				
				ByteBuffer bZ = ByteBuffer.allocate(2);
				bZ.order(ByteOrder.LITTLE_ENDIAN);
				bZ.put(bytes[5]);
				bZ.put(bytes[6]);
				sZ = bZ.getShort(0);

				short highX = (short) (bytes[1] & 0xFF);
				short lowX = (short) (bytes[2] & 0xFF);
				sX = ((highX & 0xFF));// << 8) | (lowX & 0xFF);
				
				short highY = (short) (bytes[3] & 0xFF);
				short lowY = (short) (bytes[4] & 0xFF);
				sY = ((highY & 0xFF));// << 8) | (lowY & 0xFF);
				
				short highZ = (short) (bytes[5] & 0xFF);
				short lowZ = (short) (bytes[6] & 0xFF);
				sZ = ((highZ & 0xFF));// << 8) | (lowZ & 0xFF);
				
				//sX = (short)( ((bytes[1] & 0xFF) << 8) | (bytes[2] & 0xFF) );
				//sY = (short)( ((bytes[3] & 0xFF) << 8) | (bytes[4] & 0xFF) );
				//sZ = (short)( ((bytes[5] & 0xFF) << 8) | (bytes[6] & 0xFF) );
				//Log.v(TAG, "value of z: " + sZ);
				//Log.v(TAG, "X VALUE BITSTRING: " + Integer.toBinaryString(bytes[1] & 0xFF) + "." + Integer.toBinaryString(bytes[2] & 0xFF));
				Log.v(TAG, "Int values: " + sX + " " + sY + " " + sZ);
				X = (double)sX / 100.;
				Y = (double)sY / 100.;
				Z = (double)sZ / 100.;
				
				tetherCallbacks.positionUpdate(X, Y, Z);
			}
			*/
			
			String[] tokens = command.split(String.valueOf(DELIMITER));
			if(tokens.length != 3) {
				Log.e(TAG, "Unknown command, throwing away!");
				return;
			}
			
			sX = Integer.parseInt(tokens[0]);
			sY = Integer.parseInt(tokens[1]);
			sZ = Integer.parseInt(tokens[2]);
			
			X = (double)sX / 100.0;
			Y = (double)sY / 100.0;
			Z = (double)sZ / 100.0;
			tetherCallbacks.positionUpdate(X, Y, Z);
		}
		
		private boolean parseCommand() {
			
			int end_command_index = rx_buffer.indexOf(END_COMMAND);
			
			// No end character
			if (end_command_index == -1)
				return false;
			
			String command = rx_buffer.substring(0, end_command_index);
			rx_buffer = rx_buffer.substring(end_command_index+1);
			
			commandReceived(command);
			return true;
		}
		
		public void run() {
			
			while (true) {
				if(!on) {
					if (connected) {
						Log.v(TAG, "Tether stopped by user, closing.");
						connected = false;
						pending_out_command = "";
						rx_buffer = "";
						tetherCallbacks.disconnected();
						bluetooth.close();
					}
					Log.v(TAG, "Tether thread stopping.");
					return;
				}
				
				if (connected == false) {
					Log.v(TAG, "Attempting to connect...");
					
					if (bluetooth.streaming())
						bluetooth.close();
					
					if (bluetooth.connect()) {
						Log.v(TAG, "Connected to tether!");
						connected = true;
						tetherCallbacks.connected();
						last_command_time = System.currentTimeMillis();
					} else {
						Log.e(TAG, "Failed to connect to tether!");
						continue;
					}
				}
				
				long time_diff = System.currentTimeMillis() - last_command_time;
				if((time_diff > TIMEOUT) || (bluetooth.streaming() == false)) {

					Log.v(TAG, "time diff: " + time_diff);
					Log.e(TAG, "Lost bluetooth connection!");
					connected = false;
					tetherCallbacks.disconnected();
					continue;
				}
				
				if (pending_out_command.length() > 0) {
					bluetooth.write(pending_out_command);
					pending_out_command = "";
				}
				
				String received = bluetooth.read();
				if (received.length() > 0) {
					rx_buffer = rx_buffer.concat(received);
					
					boolean commandRemaining = true;
					while (commandRemaining)
						commandRemaining = parseCommand();
				}
			}
		}
	}
}