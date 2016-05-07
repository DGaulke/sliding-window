package slidingwindow;
@SuppressWarnings("serial")
public abstract class TimeoutEvent extends WindowEvent {
	public TimeoutEvent(Object source){
		super(source);	
	}
	public abstract int getTimeoutDuration();
	
}
