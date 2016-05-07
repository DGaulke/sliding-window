package slidingwindow;
import java.io.*;
/**
 * The ServerSocket class is used to receive incoming Socket connections.
 */
public class ServerSocket {
	private int port;
	/**
	 * Create a new ServerSocket to listen for incoming connnections
	 * @param int port
	 * 	The port number to listen on
	 */
	public ServerSocket(int port){
		this.port = port;
	}
	/**
	 * Listen for incoming connections
	 * @return Socket
	 * 	The socket which provides input and output streams for communication
	 */
	public Socket accept() throws IOException {
		return new Socket(this.port);
	}
}
