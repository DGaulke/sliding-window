package slidingwindow; 
import slidingwindow.Frame.FrameKind;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.atomic.*;
import java.net.*;
import java.io.*;
/**
 * The DataLink class receives packets from Socket, and sends them to their
 * destination through PhysicalLayer.  It delivers a reconstructed message 
 * in proper order using sliding window protocol, and returns it to the
 * other Socket.
 */
public class DataLink {
	static int dataTimeout = 10000; 
	final static int ACK_TIMEOUT = 50; //No data frame to piggyback on
	private PhysicalLayer physicalLayer; //Sends and receives frames
	private final Socket socket; //Sends and receives data packets
	private final Clock clock = new Clock(this); //Handles timeouts
	//Frames currently in window
	private final LinkedList<Frame> buffer = new LinkedList<Frame>();
	private int maxSeq;
	private AtomicInteger nextFrameToSend = new AtomicInteger(0);
	private AtomicInteger frameExpected = new AtomicInteger(0);
	private AtomicBoolean sentEOF = new AtomicBoolean(false);
	private volatile boolean allAcksReceived = false;
	private volatile boolean receivedEOF = false;
	//Resending all frames in buffer after timeout
	private volatile boolean retransmit = false; 
	//Frame numbers iterate past maxSeq back to 0
	private final IntUnaryOperator incrementer;	 

	/**
	 * Set the duration in which frames are resent if not acked by
	 * the receiver.
	 * @param int timeout
	 * 	The timeout in milliseconds
	 */
	public static void setTimeout(int timeout){
		if (timeout <= 2 * PhysicalLayer.SEND_DELAY)
			throw new IllegalArgumentException("Timeout too short");
		DataLink.dataTimeout = timeout;
	}

	//Server setup - doesn't know who client is yet	
	DataLink(Socket socket, int fromPort) throws IOException {
		this(socket);
		this.physicalLayer = PhysicalLayer.connectAndWait(this, fromPort);	
		//Resize if sender has different value
		this.maxSeq = PhysicalLayer.getWindowSize() - 1;
	}
	
	//Client setup - knows who server is
	DataLink(Socket socket, int fromPort, InetAddress toAddress, int toPort) 
			throws IOException {
		this(socket);
		this.physicalLayer = 
				PhysicalLayer.connect(this, fromPort, toAddress, toPort);
	}
	//Set up member vars
	private DataLink(Socket socket){
		this.socket = socket;
		this.maxSeq = PhysicalLayer.getWindowSize() - 1;
		//Increment frame numbers through maxSeq
	   	this.incrementer = new IntUnaryOperator(){
			@Override
			public int applyAsInt(int value){
				return (value + 1) % (maxSeq + 1);
			}
		}; 
	}

	//Send EOF notification and disconnect from physical layer after all
	//acknowledgements are received
	void end() throws IOException {
		Frame f;
		//If sentEOF is false, set it true and send EOF frame
		if (!this.sentEOF.getAndSet(true))
			sendEOF();
		synchronized(this.buffer){
			//EOF sender waits until all Acks received
			//EOF receiver must wait in case more frames arrive
			while(!this.allAcksReceived || this.receivedEOF)
				try {
					this.buffer.wait();
				} catch (InterruptedException ie){
					ie.printStackTrace();
				}
		}
		this.clock.end(); //Stop further timeout events
		this.physicalLayer.disconnect(); //Stop listening for frames
	}
	//Network Layer has packet to send
	void networkLayerReady(Packet p) {
		Frame f;
		synchronized(this.buffer){
			while (retransmit)
				try { //Wait while retransmitting frames from timeout
					this.buffer.wait();
				} catch (InterruptedException ie){
					ie.printStackTrace();
				}
			int ackno = getAckExpected();
			int frameno = nextFrameToSend.getAndUpdate(incrementer);
			f = Frame.newDataFrame(ackno, frameno, p);
			buffer.addLast(f);

			if (buffer.size() == maxSeq) //If buffer full
				//Disable network layer until acks received
				this.socket.disableNetworkLayer(true); 
			sendFrame(f);
		}
	}	
	//Send frame and set timers
	private void sendFrame(Frame f){
		System.out.printf("%n%80s%n", ">>> " + f.getKind() + 
				" FRAME " + f.getSeqno() + " SENT >>>");
		this.physicalLayer.fromDataLink(f);
		startTimer(f.getSeqno());
		stopAckTimer();
	}
	//Send EOF frame
	private void sendEOF() {
		Frame f;
		synchronized(this.buffer){
			while (retransmit)
				try { //Wait while retransmitting frames from timeout
					this.buffer.wait();
				} catch (InterruptedException ie){
					ie.printStackTrace();
				}
			int ackno = getAckExpected();
			int frameno = nextFrameToSend.getAndUpdate(incrementer);
			f = Frame.newEOFFrame(ackno, frameno);
			buffer.addLast(f);
		}
		sendFrame(f);
	}
	//Send Ack frame after Ack timeout
	private void sendAck() {
		int ackno = getAckExpected();
		Frame f = Frame.newAck(ackno);
		this.physicalLayer.fromDataLink(f);
		System.out.printf("%n%80s%n", ">>> ACK FRAME " 
				+ f.getAckno() + " SENT >>>");
	}
	//Frame received from physical layer
	void frameArrival(Frame f) {
		////// DO FOR ALL FRAMES /////////
		synchronized(this.buffer){
			while (buffer.size() > 0 && 
					DataLink.between(buffer.get(0).getSeqno(), 
						f.getAckno(), nextFrameToSend.get())){
				//Take acked frames out of buffer and stop timer
				Frame removed = buffer.remove();
				this.clock.stopTimer(removed.getSeqno());
			}
			//Release thread that called end() which was waiting for
			//receiver to ack its EOF message
			if (buffer.size() == 0 && this.sentEOF.get()){
				this.allAcksReceived = true;
				this.buffer.notifyAll();
			}

			//Buffer can hold more frames
			if (buffer.size() < maxSeq && !retransmit)
				this.socket.disableNetworkLayer(false);
		} 


		////// DO FOR ACK THEN STOP ///////
		if (f.getKind() == FrameKind.ACK){
			System.out.println("\n<<< ACK FRAME " + f.getAckno() + 
					" RECEIVED <<<");
			return;
		}

	    ////// DO FOR DATA AND EOF //////	
		System.out.printf("\n<<< %s FRAME %d RECEIVED: %d bytes <<<%n", 
				f.getKind(), f.getSeqno(), f.getLength());

		startAckTimer(); //Must send ack frame if cannot piggyback

		if (receivedEOF) //If received EOF, no more data expected
			return;

		//Stop if frame arrived out of sequence
		if (f.getSeqno() != frameExpected.get()){
			System.out.println("*** ERROR - Frame Out of Sequence ***");
			return;
		};

		System.out.println("*** OK - Frame Expected ***");
		frameExpected.getAndUpdate(incrementer);

		if (f.getKind() == FrameKind.DATA){
			this.socket.fromDataLink(f.getPacket()); //Send data up to socket
		} else if (f.getKind() == FrameKind.EOF){
			this.receivedEOF = true;
			this.sentEOF.set(true);
			this.socket.eof(); 
		}
		
	}	
	void damagedFrameArrival(){
		System.out.println("<<< DAMAGED FRAME RECEIVED <<<");
	}
	//Received timeout event from Clock
	void timeout(int seqno){
		if (seqno == -1)
			ackTimeout();
		else
			dataTimeout();
	}
	//If data timeout, resend all frames in buffer
	private void dataTimeout() {
		List<Frame> resend;
		synchronized(this.buffer){
			if (this.buffer.size() == 0)
				return; //Nothing to resend
			System.out.printf("%40s%n",
					"*** TIMEOUT OCCURRED - ack expected: " + 
					this.buffer.get(0).getSeqno() + " ***");
			retransmit = true; //Pause network layer
			this.socket.disableNetworkLayer(true);
			resend = new ArrayList<Frame>(this.buffer);
		}

		//Resend all
		for (Frame f : resend)
			sendFrame(f);
		
		//Unpause network layer
		retransmit = false;
		synchronized(this.buffer){
			if (this.buffer.size() < maxSeq)		
				socket.disableNetworkLayer(false);
				this.buffer.notifyAll();
		}	
	}	
	//Ack reeived frame
	private void ackTimeout() {
		sendAck();
	}	
	//Get number of last received frame
	private int getAckExpected(){
		return (frameExpected.get() + maxSeq) % (maxSeq + 1);
	}
	//Determine if b comes between a and c in frame sequences
	private static boolean between(int a, int b, int c){
		return (((a <= b) && (b < c)) || 
				((c < a) && (a <= b)) || 
				((b < c) && (c < a)));
	}
	//Start timer to make sure frame seqno is acked
	private void startTimer(int seqno){
		this.clock.startTimer(seqno, DataLink.dataTimeout);
	}
	//Make sure to send ack if no data frame to piggyback on
	private void startAckTimer(){
		this.clock.startTimer(-1, DataLink.ACK_TIMEOUT);
	}
	//Stop ack timer when piggyback ack sent
	private void stopAckTimer(){
		this.clock.stopTimer(-1);
	}
	
}
