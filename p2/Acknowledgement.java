import java.net.*;
import java.util.*;
public class Acknowledgement {
	private final short cksum;
	private final short len;
	private final int ackno;

	public Acknowledgement(short cksum, short len, short ackno){
		this.cksum = cksum;
		this.len = len;
		this.ackno = ackno;
	}
	
	public static boolean isValid(byte[] data){
		if (data.length != 8)
			return false;	

		short target = 0;
		target &= (data[0] << 8);
		target &= data[1];	

		short chksum = SlidingWindow.IPChecksum(Arrays.copyOfRange(data,2,6));
		return (chksum == ~target);
	}
}
