import java.net.*;
public class Packet {
	private final short cksum;
	private final short len;
	private final int ackno;
	private final int seqno;
	private final byte[] data;
	
	public Packet(byte[] data, int ackno, int seqno){
		this.data = data;
		this.len = (short)data.length;
		this.ackno = ackno;
		this.seqno = seqno;
		this.cksum = SlidingWindow.IPChecksum(data); 
	}

	public DatagramPacket toDatagram(){
		DatagramPacket output = new DatagramPacket();	
	}
	


	/*private byte[] intToBytes(int i){
		byte[] output = new byte[4];
		output[0] = (byte)(i >> 24);
		output[1] = (byte)(i >> 16);
		output[2] = (byte)(i >> 8);
		output[3] = (byte)(i);
		return output;

	}*/
}
