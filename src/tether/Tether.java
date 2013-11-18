package tether;

import java.util.HashMap;
import java.util.Map;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import fi.sulautetut.android.tblueclient.TBlue;

/**
 * Tether Android Library
 * --------------------------------
 * This library provides an easy interface for communicating with
 * Tether controllers on an Android platform. The goal is to encourage 
 * development applications using the Tether.
 * 
 * @version 0.2
 * 
 * @author Hayk Martirosyan
 * @author Mishel Johns
 * @author Mark Stauber
 * @author Scott MacDonald
 */
public class Tether {
	
	// --------- MESSAGE ID CONSTANTS -----------------------------
	
	// Active communication established
	public static final int CONNECTED = 0;
	
	// Active communication closed, manually or timeout
	public static final int DISCONNECTED = 1;
	
	// New position data received
	public static final int POSITION_UPDATE = 2;
	
	// Button 1 event
	public static final int BUTTON_1 = 3;

	// Button 2 event
	public static final int BUTTON_2 = 4;
	
	// AOK confirmation from device
	public static final int AOK = 5;
	
	// ERROR message from device
	public static final int ERROR = 6;
	
	// --------- PRIVATE CONSTANTS --------------------------------
	
	// End command delimiter for communication
	private static final char END_COMMAND = '\n';
	
	// Token delimiter within commands
	private static final char DELIMITER = ' ';
	
	// --------- PRIVATE STATIC VARIABLES -------------------------
	
	// Tag for the log
	private static String TAG = "libtether";
	
	// This dictionary holds all tether objects created by makeTether
	private static Map<String, Tether> tethers = new HashMap<String, Tether>();
	
	// --------- PUBLIC STATIC METHODS ---------------------------
	
	/**
	 *  Creates a Tether object with the given address. Used instead
	 *  of a constructor to share Tether objects between activities.
	 */
	public static Tether makeTether(String addr) {
		Tether tether = new Tether(addr);
		Log.v(TAG, "Created new tether object: " + tether);
		tethers.put(addr, tether);
		return tether;
	}
	
	/**
	 *  Returns a Tether object given the address. Use this to access Tether
	 *  objects when you don't have a reference to it, but its address.
	 */
	public static Tether getTether(String addr) {
		return tethers.get(addr);
	}
	
	// --------- INSTANCE VARIABLES ----------------------------

	// Bluetooth thread that loops continuously
	private BTThread btt;
	
	// Bluetooth address
	private String address;
	
	// Handler that I send messages to
	private Handler handler;
	
	// Floating-point coordinates in centimeters
	private double X;
	private double Y;
	private double Z;
	
	// Coordinates as received, integers representing .1 mm
	private int rX = 0;
	private int rY = 0;
	private int rZ = 0;
	
	// --------- PUBLIC INSTANCE METHODS ------------------------
	
	/**
	 * Constructor, creates a Tether object from a given address.
	 */
	public Tether(String addr) {

		address = addr;
		
		X = 0.0;
		Y = 0.0;
		Z = 0.0;
		
		btt = null;
		handler = null;
	}
	
	/**
	 * Set the Handler that this Tether will send messages to. This is
	 * how an application receives data from a Tether.
	 */
	public void setHandler(Handler h) {
		handler = h;
	}
	
	/**
	 * Begin an active connection to this Tether, continuously attempting
	 * to connect. When succeeded, a Tether.CONNECTED Message will be sent.
	 */
	public void begin() {
		Log.v(TAG, "Tether start called!");
		if (btt == null) {
			btt = new BTThread(address);
			btt.start();
		}
	}
	
	/**
	 * Close a connection to this Tether. No attempts will be made to 
	 * communicate with it.
	 */
	public void end() {
		if (btt != null) {
			btt.on = false;
			btt = null;
		}
	}
	
	/**
	 * Returns true if this Tether object is active mode (begin method called).
	 */
	public boolean isActive() {
		return (!(btt == null));
	}
	
	/**
	 * Returns true if currently connected to the device.
	 */
	public boolean isConnected() {
		if (!isActive()) return false;
		return btt.connected;
	}
	/**
	 * Send a command to the Tether. Returns true if succeeded
	 * and false if failed.
	 */
	public boolean sendCommand(String command) {
		if(btt != null) {
			btt.pending_out_command = command;
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Return the current X coordinate.
	 */
	public double X() { 
		return X; 
	}
	
	/**
	 * Return the current Y coordinate.
	 */
	public double Y() { 
		return Y; 
	}
	
	/**
	 * Return the current Z coordinate.
	 */
	public double Z() { 
		return Z; 
	}
	
	/**
	 * String representation of a Tether object.
	 */
	public String toString() {
		return "<Tether: " + address + ">";
	}
	
	// --------- PRIVATE INSTANCE METHODS ------------------------
	
	private void sendMessage(Message msg) {
		if(handler != null)
			handler.sendMessage(msg);
		else
			Log.w(TAG, "Tether generating message but no handler!");
	}
	
	private void sendConnectedMessage() {
		
		Message msg = new Message();
		msg.what = CONNECTED;
		sendMessage(msg);
	}
	
	private void sendDisconnectedMessage() {
		
		Message msg = new Message();
		msg.what = DISCONNECTED;
		sendMessage(msg);
	}
	
	private void sendPositionUpdateMessage() {
		
		Message msg = new Message();
		msg.what = POSITION_UPDATE;
		
		Bundle b = new Bundle();
		b.putDouble("X", X);
		b.putDouble("Y", Y);
		b.putDouble("Z", Z);
		
		msg.setData(b);
		sendMessage(msg);
	}
	
	private void gotPositionUpdate(String command) {
		
		String[] tokens = command.split(String.valueOf(DELIMITER));
		if (tokens.length != 4) {
			Log.e(TAG, "Incorrect number of arguments for POS command!");
			return;
		}
		
		// Parse into integers as .1 mm increments
		rX = Integer.parseInt(tokens[1]);
		rY = Integer.parseInt(tokens[2]);
		rZ = Integer.parseInt(tokens[3]);
		
		// Convert to centimeter doubles
		X = (double)rX / 100.0;
		Y = (double)rY / 100.0;
		Z = (double)rZ / 100.0;
		
		// Send out a notification
		sendPositionUpdateMessage();
	}
	
	private void gotButton1(String command) {
		
		String[] tokens = command.split(String.valueOf(DELIMITER));
		if (tokens.length != 2) {
			Log.e(TAG, "Incorrect number of arguments for BUTTON_1 command!");
			return;
		}
		
		boolean pressed = (Integer.parseInt(tokens[1]) > 0);
		
		Message msg = new Message();
		msg.what = BUTTON_1;
		
		Bundle b = new Bundle();
		b.putBoolean("PRESSED", pressed);
		
		msg.setData(b);
		sendMessage(msg);
	}

	private void gotButton2(String command) {
		
		String[] tokens = command.split(String.valueOf(DELIMITER));
		if (tokens.length != 2) {
			Log.e(TAG, "Incorrect number of arguments for BUTTON_1 command!");
			return;
		}
		
		boolean pressed = (Integer.parseInt(tokens[1]) > 0);
		
		Message msg = new Message();
		msg.what = BUTTON_2;
		
		Bundle b = new Bundle();
		b.putBoolean("PRESSED", pressed);
		
		msg.setData(b);
		sendMessage(msg);
	}
	
	private void gotAok(String command) {
		
		Message msg = new Message();
		msg.what = AOK;
		
		Bundle b = new Bundle();
		b.putString("INFO", command);
		
		msg.setData(b);
		sendMessage(msg);
	}
	
	private void gotError(String command) {
		
		Message msg = new Message();
		msg.what = ERROR;
		
		Bundle b = new Bundle();
		b.putString("INFO", command);
		
		msg.setData(b);
		sendMessage(msg);
	}
	
	// --------- BLUETOOTH FUNCTIONALITY ---------------------------
	
	/**
	 * This class implements a Thread which handles a bluetooth
	 * connection with a Tether device. It sends messages using
	 * the Tether object's Handler.
	 */
	private class BTThread extends Thread {
		
		// Class that handles low-level bluetooth
		private TBlue bluetooth;
		
		// Is the Tether in start mode?
		private boolean on;
		
		// Do I have an active connection (no timeout)?
		private boolean connected;
		
		// Next command to send to Tether device
		private String pending_out_command;

		// String buffer for received data
		private String rx_buffer;
		
		// Timeout in milliseconds to assume a lost connection
		private long TIMEOUT = 3000;
		
		// Last time I got any commands, used for timeout
		private long last_command_time;
		
		/**
		 * Create a Thread for this Tether but don't start running.
		 */
		private BTThread(String addr) {
			
			bluetooth = new TBlue(address);
			connected = false;
			on = true;
			pending_out_command = "";
			rx_buffer = "";
			last_command_time = System.currentTimeMillis();
		}
		
		/**
		 * Received a full command from the Tether, parse it and send Messages.
		 */
		private void commandReceived(String command) {
			
			Log.v(TAG, "Full command received: " + command + ", length: " + command.length());
			
			last_command_time = System.currentTimeMillis();
			
			// Split tokens by delimiter
			String[] tokens = command.split(String.valueOf(DELIMITER));
			if(tokens.length < 1) return;
			
			String command_name = tokens[0];
			
			if (command_name.equals("POS")) {
				gotPositionUpdate(command);
			} else if (command_name.equals("AOK")) {
				gotAok(command);
			} else if (command_name.equals("ERROR")) {
				gotError(command);
			} else if (command_name.equals("BTN_1")) {
				gotButton1(command);
			} else if (command_name.equals("BTN_2")) {
				gotButton2(command);
			} else {
				Log.e(TAG, "Unknown command, throwing away: " + command_name);
			}
		}
		
		/**
		 * Look for full commands from the received buffer.
		 */
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
		
		/**
		 * Thread loop, continuously looks for received data, sends pending
		 * data, and handles timeouts. Loop breaks and Thread exits only
		 * when the stop() function of a Tether instance is called.
		 */
		public void run() {
			
			while (true) {
				
				// Disconnect and shut down the thread if in stop mode
				if(!on) {
					if (connected) {
						Log.v(TAG, "Tether stopped by user, closing.");
						connected = false;
						pending_out_command = "";
						rx_buffer = "";
						sendDisconnectedMessage();
						bluetooth.close();
					}
					Log.v(TAG, "Tether thread stopping.");
					return;
				}
				
				// Try to connect if not connected (BLOCKING CODE)
				if (connected == false) {
					Log.v(TAG, "Attempting to connect...");
					
					if (bluetooth.streaming())
						bluetooth.close();
					
					if (bluetooth.connect()) {
						Log.v(TAG, "Connected to tether!");
						connected = true;
						sendConnectedMessage();
						last_command_time = System.currentTimeMillis();
					} else {
						Log.e(TAG, "Failed to connect to tether!");
						continue;
					}
				}
				
				// Handle timeout
				long time_diff = System.currentTimeMillis() - last_command_time;
				if((time_diff > TIMEOUT) || (bluetooth.streaming() == false)) {

					Log.v(TAG, "time diff: " + time_diff);
					Log.e(TAG, "Lost bluetooth connection!");
					connected = false;
					sendDisconnectedMessage();
					continue;
				}
				
				// Send out pending commands to the device
				if (pending_out_command.length() > 0) {
					bluetooth.write(pending_out_command + END_COMMAND);
					pending_out_command = "";
				}
				
				// Put received commands into the buffer and parse
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