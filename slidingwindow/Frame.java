package slidingwindow;
import java.util.*;
import java.nio.*;
import java.net.*;
class Frame {
	enum FrameKind {DATA, EOF, ACK;};

	private final static int ACK_LENGTH = 8;
	final static int HEADER_SIZE = 12;
	private final static ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
	private FrameKind kind;
	private short chksum;
	private short len;
	private int ackno;
	private int seqno;
	private Packet packet;
	private boolean valid = true;

	private Frame(){} //Force use of static factory methods

	/**
	 * Decode a byte array and convert to a Frame object.
	 * @param byte[] data
	 * 	The data to decode
	 * @return Frame 
	 * 	The decoded frame
	 */
	static Frame decode(byte[] data){
		Frame f = new Frame();
		ByteBuffer bb;
		int size;
		int index = 0;

		//Get and set chksum
		size = Short.SIZE / Byte.SIZE;	
		bb = ByteBuffer.allocate(size).order(Frame.BYTE_ORDER);
		bb.put(Arrays.copyOfRange(data, index, index + size));
		bb.flip();
		f.chksum = bb.getShort();
		index += size;
		//Get and set len
		size = Short.SIZE / Byte.SIZE;	
		bb = ByteBuffer.allocate(size).order(Frame.BYTE_ORDER);
		bb.put(Arrays.copyOfRange(data, index, index + size));
		bb.flip();
		f.len = bb.getShort();
		index += size;
		//Get and set ackno
		size = Integer.SIZE / Byte.SIZE;
		bb = ByteBuffer.allocate(size).order(Frame.BYTE_ORDER);
		bb.put(Arrays.copyOfRange(data, index, index + size));
		bb.flip();
		f.ackno = bb.getInt();
		index += size;
		
		//Determine validity and frame type
		int len = f.getLength();
		if (len < Frame.ACK_LENGTH)
			f.valid = false; //Too short
		else if (len == Frame.ACK_LENGTH)
			f.kind = FrameKind.ACK;  //Ack length
		else if (len < Frame.HEADER_SIZE)
			f.valid = false; //Too long for ack, too short for data
		else {
			f.kind = (len == Frame.HEADER_SIZE) 
					? FrameKind.EOF 
					: FrameKind.DATA;
			//Get and set seqno
			size = Integer.SIZE / Byte.SIZE;
			bb = ByteBuffer.allocate(size).order(Frame.BYTE_ORDER);
			bb.put(Arrays.copyOfRange(data, index, index + size));
			bb.flip();
			f.seqno = bb.getInt(); 
			//Get and set packet
			byte[] payload = Arrays.copyOfRange(data, Frame.HEADER_SIZE, len);
			f.packet = new Packet(payload);
		}
		f.validateChecksum(); //Ensure data not corrupted
		return f;
	}

	//Convert a Frame to a byte array for transmission as Datagram packet
	byte[] encode() throws IllegalStateException {
		int intLen = this.getLength();
		byte[] output = new byte[intLen]; //Initialize array

		try {
			//Put all frame data in ByteBuffer
			ByteBuffer bb = ByteBuffer.allocate(intLen).
					order(Frame.BYTE_ORDER);
			bb.putShort(this.chksum);
			bb.putShort(this.len);
			bb.putInt(this.ackno);
			if (this.kind == FrameKind.DATA || this.kind == FrameKind.EOF){
				bb.putInt(this.seqno);
				bb.put(this.packet.decode());
			}
			//Get frame data from ByteBuffer into output array and return
			bb.flip();
			bb.get(output);
		} catch (BufferUnderflowException bue){
			throw new IllegalStateException(); //len corrupted
		} catch (BufferOverflowException boe){
			throw new IllegalStateException(); //len corrupted
		}
		return output;
	}
	//Create a new Ack frame with given ack number
	static Frame newAck(int ackno){
		Frame f = new Frame();
		f.kind = FrameKind.ACK;
		f.ackno = ackno;
		f.len = Frame.ACK_LENGTH;
		f.chksum = f.calcChecksum();
		return f;	
	}
	//Create a new Data frame
	static Frame newDataFrame(int ackno, int seqno, Packet packet){
		Frame f = new Frame();
		f.kind = FrameKind.DATA;
		f.ackno = ackno;
		f.seqno = seqno;
		f.packet = packet;
		f.len = (short)((Frame.HEADER_SIZE + packet.length()));
		f.chksum = f.calcChecksum();
		return f;
	}
	//Create a new EOF frame
	static Frame newEOFFrame(int ackno, int seqno){
		Frame f = new Frame();
		f.kind = FrameKind.EOF;
		f.ackno = ackno;
		f.seqno = seqno;
		f.len = Frame.HEADER_SIZE;
		byte[] packet = new byte[0]; //Empty data message
		f.packet = new Packet(packet);
		f.chksum = f.calcChecksum();
		return f;
	}

	FrameKind getKind(){
		return this.kind;
	}

	int getAckno(){
		return this.ackno;
	}

	int getSeqno(){
		return this.seqno;
	}

	int getLength(){
		return this.len & 0xffff; //Convert to unsigned value
	}

	Packet getPacket(){
		return this.packet;
	}
	
	boolean isValid(){
		return this.valid;
	}
	//Make sure checksum of received packet is 0
	private void validateChecksum(){
		this.valid = (calcChecksum() == 0);
	}
	//Calculate frame checksum to ensure data quality
	private short calcChecksum(){
		byte[] data;
		try {
			data = this.encode();
		} catch (IllegalStateException ise){
			return -1; //Frame corrupted
		}
		int[] values = new int[Integer.SIZE];

		//Accumulate all 16 bit values into int array
		int offset = 0;
		for (byte b : data){
			for (int i = 0; i < Byte.SIZE; i++){
				int val = (int)Math.pow(2,i);
				values[i + offset] += ((b & val) > 0 ? 1 : 0);
			}
			offset = Math.abs(-Byte.SIZE + offset);
		}
		//Propagate sums into higher bit slots
		boolean overflow;
		do {
			for (int i = 0; i < Short.SIZE; i++){
				values[i+1] += values[i] / 2;
				values[i] %= 2;
			}
			overflow = (values[Short.SIZE] > 0);
			if (overflow){ //Slot 15 carried into slot 16
				//Continue to propagate up to 31
				for (int i = Short.SIZE; i < Integer.SIZE; i++){
					values[i+1] += values[i] / 2;
					values[i] %= 2;
				}
				//Add upper 16 bits back into lower 16
				for (int i = Short.SIZE; i < Integer.SIZE; i++){
					values[i - Short.SIZE] += values[i];
					values[i] = 0;
				}
			}
		} while (overflow == true); //Do until no overflow

		//Negate 16 bit result and return
		short output = 0;
		for (int i = 0; i < Short.SIZE; i++)
			if (values[i] == 0)
				output |= (int)Math.pow(2, i);
		
		return output;
	}
}
