package tether;

import java.util.HashMap;
import java.util.Map;

import android.util.Log;
import fi.sulautetut.android.tblueclient.TBlue;

public class Tether {
	
	private static Map<String, Tether> tethers = new HashMap<String, Tether>();
	
	private static BTThread btt;
	private String TAG = "libtether";
	
	private String address;
	private TetherCallbacks tetherCallbacks;
	
	private double X;
	private double Y;
	private double Z;
	
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
			Log.v(TAG, "Full command received: " + command);
			
			last_command_time = System.currentTimeMillis();
			
			String[] tokens = command.split(String.valueOf(DELIMITER));
			if(tokens.length != 3) {
				Log.e(TAG, "Unknown command, throwing away!");
				return;
			}
			
			X = Double.parseDouble(tokens[0]);
			Y = Double.parseDouble(tokens[1]);
			Z = Double.parseDouble(tokens[2]);
			
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