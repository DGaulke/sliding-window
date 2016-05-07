package slidingwindow;
import java.util.*;
import java.io.*; 
import java.net.*; 
import java.util.concurrent.atomic.*;
/**
 * The Socket class is used for network communication using byte streams
 */
public class Socket {
	private static int packetSize = 1024;
	private int bytesRead = 0;
	private DataLink dataLink;
	private final List<Packet> inputQueue = new LinkedList<Packet>();
	private final byte[] outputBuffer = new byte[packetSize];
	private int outputIndex = 0;
	private byte[] inputBuffer = new byte[0];
	private int inputIndex = 0;
	private volatile boolean eof = false;
	private volatile boolean active = true;
	private final AtomicBoolean dataLinkFull = new AtomicBoolean(false);
	/**
	 * Set the packet size of data delivered over network
	 * @param int packetSize
	 *  The size of the packet in bytes
	 */	
	public static void setPacketSize(int packetSize){
		if (packetSize < 1 || 
				packetSize + Frame.HEADER_SIZE > PhysicalLayer.MAX_PACKET_SIZE)
			throw new IllegalArgumentException("Invalid packet size");

		Socket.packetSize = packetSize;
	}

	/**
	 * Create a new Socket object to connect to a ServerSocket
	 * @param String host
	 * 	The host machine to connect to
	 * @param int port
	 *  The port number to connect on
	 */
	//Client
	public Socket(String host, int toPort) throws IOException {
		InetAddress toAddress = InetAddress.getByName(host);

		int fromPort = Socket.getRandomPort();
		while (true)
			try { //Try to listen on random port until no BindException thrown
				this.dataLink = 
					new DataLink(this, fromPort, toAddress, toPort);
				return;
			} catch (BindException be){ /*ignore*/ }
	}
	//Server -- called by ServerSocket
	Socket(int fromPort) throws IOException {
		this.dataLink = new DataLink(this, fromPort);
	}
	/**
	 * Get byte stream to read from socket
	 * @return InputStream
	 * 	The input stream from the socket
	 */
	public InputStream getInputStream(){
		return new InputStream(){
			@Override
			/*
			 * Read byte from InputStream
			 * @return int 
			 * 	The byte returned from the input stream as int
			 * 	Returns -1 when stream ends
			 */
			public int read(){
				if (inputIndex == Socket.this.inputBuffer.length)
					queuePacket(); //Load new packet into buffer
				if (!Socket.this.active)
					return -1; //Socket has been closed
				++bytesRead;
				return inputBuffer[inputIndex++] & 0xffff; //byte to int
			}
		};
	}
	/**
	 * Get byte stream to write to socket
	 * @return OutputStream
	 *  The output stream from the socket
	 */
	public OutputStream getOutputStream(){
		return new OutputStream(){
			@Override
			/**
			 * Write byte to InputStream
			 * @param int b
			 * 	The byte to be written as an int
			 */
			public void write(int b){
				outputBuffer[outputIndex++]  = (byte)(b); //int to byte
				if (outputIndex >= outputBuffer.length)
					toDataLink(); //Deliver full buffer to data link layer
			}
			@Override
			/**
			 * Flush remaining bytes in buffer
			 */
			public void flush(){
				if (outputIndex > 0)
					toDataLink(); //Deliver flushed buffer to data link layer
			}
		};
	}
	/** 
	 * Flush the stream and close the socket
	 */
	public void close() throws IOException {
		this.getOutputStream().flush(); //Send remaining bytes in output buffer
		this.active = false;
		this.dataLink.end(); //Pass message down to physical layer
	}
	//Socket has received EOF message 
	void eof() {
		this.eof = true;
		synchronized(this.inputQueue){
			this.inputQueue.notifyAll();
		}
	}
	//Send bytes in buffer to data link layer
	private void toDataLink(){
		synchronized(this){
			while (this.dataLinkFull.get())
				try { //Data link cannot currently accept any more data
					this.wait();
				} catch (InterruptedException ie){
					ie.printStackTrace();
				}
		}
		byte[] payload = Arrays.copyOfRange(this.outputBuffer, 0, outputIndex);
		Packet p = new Packet(payload);
		this.dataLink.networkLayerReady(p); //Packet to data link
		this.outputIndex = 0; //Reset index
	}
	//Packet received from data link layer
	void fromDataLink(Packet p){
		synchronized(this.inputQueue){
			this.inputQueue.add(p);
			this.inputQueue.notifyAll(); //Let reader know data is ready
		}
	}
	//Take packet from input queue and put in input buffer for reading
	private void queuePacket(){
		synchronized(this.inputQueue){
			while (inputQueue.size() == 0 && !this.eof){
				try {
					inputQueue.wait();
				} catch (InterruptedException ie){
					ie.printStackTrace();
				}
			}
			if (this.eof){
				this.active = false;
			} else { //Decode packet and set as input buffer
				Packet p = inputQueue.remove(0);
				this.inputBuffer = p.decode();
			}

			inputIndex = 0; //Reset index
		}
	}
	//Data link layer calls to disable when its window is full or
	//to enable when space is available	
	void disableNetworkLayer(boolean disabled){
		//If sending was disabled and becomes enabled, wake up sender
		if (this.dataLinkFull.getAndSet(disabled) && !disabled)
			synchronized(this){
				this.notifyAll();
			}
	}
	//Find port for client socket to listen on
	private static int getRandomPort() {
		return new Random().nextInt(16384) + 49152; // 49152-65535	
	}
}
