package slidingwindow;
@SuppressWarnings("serial")
public class AckTimeoutEvent extends TimeoutEvent {
	public final static int TIMEOUT_DURATION = 200;
	public AckTimeoutEvent(Object source){
		super(source);	
	}
	public int getTimeoutDuration(){
		return DataTimeoutEvent.TIMEOUT_DURATION;
	}
}
