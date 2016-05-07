package slidingwindow;
@SuppressWarnings("serial")
public class DataTimeoutEvent extends TimeoutEvent {
	public final static int TIMEOUT_DURATION = 1000;
	public DataTimeoutEvent(Object source){
		super(source);	
	}
	public int getTimeoutDuration(){
		return DataTimeoutEvent.TIMEOUT_DURATION;
	}
}
