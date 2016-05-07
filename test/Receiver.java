package slidingwindow;
import java.io.*;
import java.util.*;
public class Receiver {
	public final static int PORT = 49152; 
	private final static int BUFFER_SIZE = 1024; //Doesn't matter
	private final Socket socket;
//	private final ServerSocket server;
	/**
	 * Program entry point - creates an instance of Receiver which
	 * will listen on PORT for a Sender which will transfer a file	
	 * over socket connection
	 */
	public static void main(String[] args){
		try {
			if (args.length > 0) //Get optional arguments and apply settings
				parseArgs(args);

			//Prompt for network errors
			int pctToDrop = promptForPercent("Enter % of frames to drop: ");
			PhysicalLayer.setPctToDrop(pctToDrop);
			int pctToDamage = promptForPercent("Enter % of frames to damage: ");
			PhysicalLayer.setPctToDamage(pctToDamage);
			int pctToDelay = promptForPercent("Enter % of frames to delay: ");
			PhysicalLayer.setPctToDelay(pctToDelay);

			Receiver receiver = new Receiver();
			receiver.receiveFile();
		} catch (IllegalArgumentException iae){
			System.out.println("Options:\n\t" +
				"-p\tPacket size (bytes)\n\t-t\tTimeout(ms)");
		} catch (IOException e){
			e.printStackTrace();
		}
	}
	/**
	 * Create an instance of Receiver
	 */
	public Receiver() throws IOException {
		ServerSocket server = new ServerSocket(Receiver.PORT);
		this.socket = server.accept(); 
	}
	/**
	 * Receive a file from Sender through socket connection
	 */
	public void receiveFile() throws IOException {
		File outputFile = Receiver.getOutputFile();
		OutputStream outstream = new BufferedOutputStream(
				new FileOutputStream(outputFile));			

		BufferedInputStream instream =	
				new BufferedInputStream(this.socket.getInputStream());
		byte[] buffer = new byte[Receiver.BUFFER_SIZE];
		try { //Read from socket until EOF, write into file
			int sum = 0;
			for (int read = instream.read(buffer); read >= 0;
					read = instream.read(buffer)){
				sum += read;
				outstream.write(buffer, 0, read);
			}
			System.out.println("Receiver received " + sum + " bytes.");
			outstream.flush();
		} finally {  //Clean up
			outstream.close();
			instream.close();
			socket.close();
		}
	}
	//Parse arguments from user and update program settings
	private static void parseArgs(String[] args) throws IllegalArgumentException {
		if (args.length % 2 == 1)
			throw new IllegalArgumentException();

		for (int i = 0; i < (args.length - 1); i += 2){
			int val; 
			try { //Try to get value as number
				val = Integer.parseInt(args[i+1]);
			} catch (NumberFormatException nfe){
				throw new IllegalArgumentException();
			}
			//Apply to appropriate setting
			if (args[i].equals("-p"))
				Socket.setPacketSize(val);
			else if (args[i].equals("-t"))
				DataLink.setTimeout(val);
			else 
				throw new IllegalArgumentException();
		}

	}
	//Prompt user for a value between 0 and 100
	private static int promptForPercent(String prompt){
		Scanner scanner = new Scanner(System.in);
		System.out.print(prompt);
		while (true){
			try {
				int input = scanner.nextInt();
				if (input >= 0 && input <= 100)
					return input;
			} catch (InputMismatchException ime){
				//fall through
			}
			System.out.println("Valid values: 0-100");
		}
	}
	//Get an unused file handle
	private static File getOutputFile(){
		int i = 1;
		File output;
		String base = System.getProperty("user.dir") + 
			File.separator + "filecopy";
		
		do { //Try filecopy1, filecopy2, etc. until available
			output = new File(base + i++);
		} while (output.exists());

		return output;
	}

}
