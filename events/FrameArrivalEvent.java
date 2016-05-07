package slidingwindow;
@SuppressWarnings("serial")
public class FrameArrivalEvent extends WindowEvent {
	private Frame frame;
	public FrameArrivalEvent(Object source, Frame frame){
		super(source);	
		this.frame = frame;
	}
	public Frame getFrame(){
		return frame;
	}
}
