package slidingwindow;import java.io.*; import java.util.*;
import java.net.*;
/**
 * The PhysicalLayer class receives frames from DataLink and sends them to 
 * a recepient, and receives Datagram packets and hands them to DataLink.
 */
public class PhysicalLayer implements Runnable {
	//Slow down sending of frames (to monitor log output on screen)
	final static int SEND_DELAY = 2000;
	private static int windowSize = 8;
	static final int MAX_PACKET_SIZE = 65507; 
	private static final int TIMEOUT = 20;
	private static double pctToDrop = 0;
	private static double pctToDamage = 0;
	private static double pctToDelay = 0;
	private static final Random random = new Random();
	private final Thread thread = new Thread(this);
	private final DataLink dataLink;
	private final DatagramSocket datagramSocket;
	private InetAddress address;
	private int port;
	private volatile boolean active = true;
	private volatile boolean waitForConnection = false;

	/**
	 * Set the number of frames that can fit in the network medium
	 * @param int windowSize
	 * 	The number of frames in the window
	 */
	public static void setWindowSize(int windowSize){
		PhysicalLayer.windowSize = windowSize;
	}

	/**
	 * Get the size of the window
	 * @return int
	 * 	The number of frames in the sliding window
	 */
	public static int getWindowSize(){
		return PhysicalLayer.windowSize;
	}
	/**
	 * Set the percentage of frames to lose during transmission
	 * @param int percent
	 * 	The percentage value between 0-100
	 */
	public static void setPctToDrop(int percent){
		if (percent < 0 || percent > 100)
			throw new IllegalArgumentException("Illegal percent: " + percent);
		PhysicalLayer.pctToDrop = percent / 100.0;
	}
	/**
	 * Set the percentage of frames to damage during transmission
	 * @param int percent
	 * 	The percentage value between 0-100
	 */
	public static void setPctToDamage(int percent){
		if (percent < 0 || percent > 100)
			throw new IllegalArgumentException("Illegal percent: " + percent);
		PhysicalLayer.pctToDamage = percent / 100.0;
	}
	/**
	 * Set the percentage of frames to delay during transmission
	 * @param int percent
	 * 	The percentage value between 0-100
	 */
	public static void setPctToDelay(int percent){
		if (percent < 0 || percent > 100)
			throw new IllegalArgumentException("Illegal percent: " + percent);
		PhysicalLayer.pctToDelay = percent / 100.0;
	}

	//Server
	static PhysicalLayer connectAndWait(DataLink dataLink, int fromPort) 
			throws IOException {

		PhysicalLayer output = new PhysicalLayer(dataLink, fromPort);
		output.waitForConnection = true;
		synchronized(output){
			//Will be released by incoming packet
			while (output.waitForConnection){
				try { 
					output.wait();
				} catch (InterruptedException ie){
					ie.printStackTrace();
				}
			}
		} 
		return output;
	}
	//Client
	static PhysicalLayer connect(DataLink dataLink, int fromPort, 
			InetAddress toAddress, int toPort) throws IOException {

		PhysicalLayer output = new PhysicalLayer(dataLink, fromPort);
		output.address = toAddress;
		output.port = toPort;
		return output;
	}
	private PhysicalLayer(DataLink dataLink, int fromPort) throws IOException {
		this.dataLink = dataLink;
		this.datagramSocket = new DatagramSocket(fromPort);	
		datagramSocket.setSoTimeout(PhysicalLayer.TIMEOUT); //Don't block
		//Ensure port is available before starting thread
		//receive() will end after a single TIMEOUT or throw BindException 
		//if the port is already in use
		try {
			receive();
		} catch (BindException be){
			throw be;  //Port is in use - send back up to Socket
		} catch (SocketTimeoutException se){
			//Timeout occurred - port available
		}
		this.thread.start(); //Start listening for frames
	}
	//Set inactive and wait for thread to complete
	void disconnect() throws IOException {
		this.active = false;
		try {
			this.thread.join();
		} catch (InterruptedException ie){
			ie.printStackTrace();
		}
	}
	/**
	 * Receive frames over connection and deliver them to DataLink
	 */
	@Override	
	public void run(){
		while (this.active){
			try {
				Frame f = receive();
				if (f.isValid())
					dataLink.frameArrival(f);
				else 
					dataLink.damagedFrameArrival();
			} catch (SocketTimeoutException se){
				//Expected from Socket timeout - give chance to check 
				//if still active
			} catch (IOException ioe){
				ioe.printStackTrace();
			}
		}
	}
	//Receive Datagram packet and convert to a Frame
	private Frame receive() throws IOException {
		byte[] data = new byte[PhysicalLayer.MAX_PACKET_SIZE];
		DatagramPacket datagramPacket = new DatagramPacket(data, data.length);
		datagramSocket.receive(datagramPacket);
		Frame f = Frame.decode(datagramPacket.getData());
		if (this.waitForConnection){
			this.port = datagramPacket.getPort();
			this.address = datagramPacket.getAddress();
			PhysicalLayer.setWindowSize(f.getAckno() + 1);
			this.waitForConnection = false;
			synchronized(this){
				this.notifyAll();
			}
		}
		return f;
	}	
	//Receive Frame from DataLink, subject it to network errors	and send
	void fromDataLink(Frame f){
		
		if (dropFrame()){ //If drop frame, just stop
			System.out.printf("%80s%n","*** Frame Dropped ***");
			return;
		}

		byte[] data = f.encode();
		if (damageFrame()){ //Get frame as byte array and flip random bit
			System.out.printf("%80s%n","*** Data Corrupted ***");
			PhysicalLayer.damage(data);
		}

		final int delay;
		if (delayFrame()){ //Delay delivery for random duration up to timeout
			delay = PhysicalLayer.random.nextInt(2 * PhysicalLayer.SEND_DELAY);
			System.out.printf("%80s%n","*** Frame Delayed " + delay + " ms ***");
		} else
			delay = 0;

		try { //Send frames at time interval in order to monitor output
			Thread.sleep(PhysicalLayer.SEND_DELAY);
		} catch (InterruptedException ie){
			ie.printStackTrace();
			return;
		}

		DatagramPacket datagramPacket = new DatagramPacket(
				data, f.getLength(), address, port); 

		if (delay > 0){ //If frame is delayed, create thread responsible
			new Thread(){ //for sleeping the duration then sending frame
				@Override
				public void run(){
					try {
						Thread.sleep(delay);
						PhysicalLayer.this.datagramSocket.send(datagramPacket);
					} catch (Exception e){
						e.printStackTrace();
					}
				}
			}.start();
		} else
			try { //Deliver frame to recipient
				this.datagramSocket.send(datagramPacket);
			} catch (IOException ioe){
				ioe.printStackTrace();
			}
	}
	//Decide whether to drop frame
	private static boolean dropFrame(){
		return chance(PhysicalLayer.pctToDrop);
	}
	//Decide whether to damage frame data
	private static boolean damageFrame(){
		return chance(PhysicalLayer.pctToDamage);
	}
	//Decide whether to delay frame delivery
	private static boolean delayFrame(){
		return chance(PhysicalLayer.pctToDelay);
	}
	//Determine odds of event occurring
	private static boolean chance(double odds){
		return (random.nextDouble() < odds);
	}
	//Choose a random bit from a random byte and flip value
	private static void damage(byte[] data){
		int byteIndex = PhysicalLayer.random.nextInt(data.length);
		int bitIndex = PhysicalLayer.random.nextInt(Byte.SIZE);
		byte b = data[byteIndex];
		data[byteIndex] = (byte)(b ^ (1 << bitIndex));
	}
}
