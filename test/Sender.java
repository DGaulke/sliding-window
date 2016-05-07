package slidingwindow;
import java.io.*;
import java.util.*;
import javax.swing.*;
public class Sender {
	private final static int BUFFER_SIZE = 1024;
	private final Socket socket;
	private final static File DEFAULT_DIR = 
		new File(System.getProperty("user.dir"));
	/**
	 * Program entry point - creates an instance of Sender which sets 
	 * simulated network parameters and transfers a file to a listening
	 * Receiver object on its port using sliding window protocol.
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
			
			Sender sender = new Sender();
			FileChooser fc = new FileChooser();
			File file = fc.getFile();
			fc.dispose();
			sender.send(file);
		} catch (IllegalArgumentException iae){
			System.out.println("Options:\n\t-w\tWindow size\n\t " +
				"-p\tPacket size (bytes)\n\t-t\tTimeout(ms)");
		} catch (IOException ioe){
			ioe.printStackTrace();
		}
	}
	/**
	 * Create an instane of Sender and begins connection process to Receiver
	 */
	public Sender() throws IOException {
		this.socket = new Socket("localhost", Receiver.PORT);
	}
	/**
	 * Send a file to Receiver using socket connection
	 */
	public void send(File file) throws IOException {
		BufferedInputStream instream = new BufferedInputStream(
				new FileInputStream(file));
		BufferedOutputStream outstream = new BufferedOutputStream(
				this.socket.getOutputStream());
		byte[] buffer = new byte[Sender.BUFFER_SIZE];
		try { 
			for (int read = instream.read(buffer); read >= 0; 
					read = instream.read(buffer)){
				outstream.write(buffer, 0, read);
			}
			outstream.flush();
		} finally {
			instream.close();
			outstream.close();
			this.socket.close();
		}
	}
	//Parse arguments from user and update program settings
	private static void parseArgs(String[] args) 
			throws IllegalArgumentException {
		if (args.length % 2 == 1)
			throw new IllegalArgumentException();

		for (int i = 0; i < (args.length - 1); i += 2){
			int val;
			try {
				val = Integer.parseInt(args[i+1]);
			} catch (NumberFormatException nfe){
				throw new IllegalArgumentException();
			}
			if (args[i].equals("-w"))
				PhysicalLayer.setWindowSize(val);
			else if (args[i].equals("-p"))
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
	@SuppressWarnings("serial")
	static class FileChooser extends JFrame {
		JFileChooser fileChooser;
		//Setup GUI
		FileChooser(){
			fileChooser = new JFileChooser();
			fileChooser.setCurrentDirectory(Sender.DEFAULT_DIR);
			fileChooser.setDialogTitle("Select a file to copy:");
			fileChooser.setVisible(true);
			this.getContentPane().add(fileChooser);
		}
		//Prompt for file
		File getFile(){
			File output = null;
			int result = fileChooser.showOpenDialog(this);
			if (result == JFileChooser.APPROVE_OPTION)
				output = fileChooser.getSelectedFile();
			return output;
		}
	}	
}
