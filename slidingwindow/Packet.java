package slidingwindow;
import java.net.*;
import java.nio.*;
import java.util.*;
/**
 * The Packet class is nothing more than an abstraction of
 * a byte array payload
 */
public class Packet {
	private byte[] payload;

	//Create a new Packet from the given byte array
	Packet(byte[] payload){
		this.payload = payload;
	}

	//Return the Packet as a byte array
	byte[] decode(){
		return this.payload;
	}
	//Get the length of the payload
	int length(){
		return payload.length;
	}
}
