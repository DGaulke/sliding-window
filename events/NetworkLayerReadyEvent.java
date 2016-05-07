package slidingwindow;
@SuppressWarnings("serial") public class NetworkLayerReadyEvent extends WindowEvent {
	private Packet packet;
	public NetworkLayerReadyEvent(Object source, Packet packet){
		super(source);	
		this.packet = packet;
	}
	public Packet getPacket(){
		return this.packet;
	}
}
