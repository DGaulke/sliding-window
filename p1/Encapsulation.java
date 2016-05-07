//David Gaulke
//ICS 460 - Project 1 - Encapsulation
import java.util.*;
/**
* The Encapsulation class demonstrates the use of passing data from
* one method to another, simulating network layers.
*/
public class Encapsulation {
	/**
	* Program entry point
	*/
	public static void main(String[] args){
		Scanner scanner = new Scanner(System.in);
		System.out.println("Please enter a string:");
		String input = scanner.nextLine();
		Encapsulation.acceptInput(input);			
	}
	/**
	* Accept String data to be passed
	*/
	public static void acceptInput(String input){
		Encapsulation.applicationLayer(input);
	}
	private static void applicationLayer(String input){
		String output = "Application: " + input;
		System.out.println(output);
		Encapsulation.presentationLayer(output);
	}
	private static void presentationLayer(String input){
		String output = "Presentation: " + input;
		System.out.println(output);
		Encapsulation.sessionLayer(output);
	}
	private static void sessionLayer(String input){
		String output = "Session: " + input;
		System.out.println(output);
		Encapsulation.transportLayer(output);
	}
	private static void transportLayer(String input){
		String output = "Transport: " + input;
		System.out.println(output);
		Encapsulation.networkLayer(output);
	}
	private static void networkLayer(String input){
		String output = "Network: " + input;
		System.out.println(output);
		Encapsulation.dataLinkLayer(output);
	}
	private static void dataLinkLayer(String input){
		String output = "Data Link: " + input;
		System.out.println(output);
		Encapsulation.physicalLayer(output);
	}
	private static void physicalLayer(String input){
		String output = "Physical: " + input;
		System.out.println(output);
	}
}
