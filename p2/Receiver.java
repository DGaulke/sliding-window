import java.net.*;
public class Receiver {
	public final static int PORT = 9001;
	private final Responder responder;

	public Receiver() {
		responder = new Responder();
	}	


	private class Responder implements Runnable {
	
		public void run(){
		}
	}

}
