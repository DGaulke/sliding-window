public class SlidingWindow {
	private final static int DEFAULT_WINDOW_SIZE = 20;
	private final static int DEFAULT_TIMEOUT = 2000; 
	private final Sender sender;
	private final Receiver receiver;
	private final int windowSize;
	private final int timeout;

	public static void main(String[] args){
	
	}
	
	public SlidingWindow(){
		sender = new Sender();
		receiver = new Receiver();
		windowSize = SlidingWindow.DEFAULT_WINDOW_SIZE;
		timeout = SlidingWindow.DEFAULT_TIMEOUT;
		
	}

	
	static byte[] intToBytes(int i){
		byte[] output = new byte[4];
		output[0] = (byte)(i >> 24);
		output[1] = (byte)(i >> 16);
		output[2] = (byte)(i >> 8);
		output[3] = (byte)(i);
		return output;

	}

	static short IPChecksum(byte[] data){
		short output = (short)0;
		return output;
	}

}
