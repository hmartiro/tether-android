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
	
	// Button 1 pressed
	public static final int BUTTON_1_PRESSED = 3;
	
	// Button 1 released
	public static final int BUTTON_1_RELEASED = 4;
	
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
	 * Start an active connection to this Tether, continuously attempting
	 * to connect. When succeeded, a Tether.CONNECTED Message will be sent.
	 */
	public void start() {
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
	public void stop() {
		if (btt != null) {
			btt.on = false;
			btt = null;
		}
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
		
		// End command delimiter for communication
		private char END_COMMAND = '\n';
		
		// Token delimiter within commands
		private char DELIMITER = ' ';
		
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
			
			// All this stuff is failed attempts to get byte decoding working
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
			
			// Split tokens by delimiter
			String[] tokens = command.split(String.valueOf(DELIMITER));
			if(tokens.length != 3) {
				Log.e(TAG, "Unknown command, throwing away!");
				return;
			}
			
			// Parse into integers as .1 mm increments
			rX = Integer.parseInt(tokens[0]);
			rY = Integer.parseInt(tokens[1]);
			rZ = Integer.parseInt(tokens[2]);
			
			// Convert to centimeter doubles
			X = (double)rX / 100.0;
			Y = (double)rY / 100.0;
			Z = (double)rZ / 100.0;
			
			// Send out a notification
			sendPositionUpdateMessage();
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
					bluetooth.write(pending_out_command);
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