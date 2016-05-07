package slidingwindow;
import java.util.*;
import java.io.*;
import slidingwindow.Frame.FrameKind;
/**
 * The Clock class manages timers for instances of DataLink and its
 * frame coordination with sliding window protocol.
 */
class Clock implements Runnable {
	private Thread thread;  //Runs Clock and waits down all timers
	//All Timers sorted by expiration
	private SortedSet<Timer> timers = new TreeSet<Timer>(); 
	//All Timers with seq number lookup
	private Map<Integer, Timer> timerMap = new HashMap<Integer, Timer>();
	private DataLink dataLink; //Notify when timers expire
	private volatile boolean active = true;
	private volatile long wakeup; //ms until next timer expires
	
	//Creates a new Clock which notifies dataLink when events occur
	Clock(DataLink dataLink){
		this.dataLink = dataLink;
	}
	/**
	 * Run a thread to countdown active timers and notify DataLink
	 * when events occur
	 */
	@Override
	public void run(){
		Timer next;
		synchronized(this.timers){
			if (this.timers.size() == 0)
				return; //Stop if no timers registered
			next = this.timers.first();
		}
		if (next.timeRemaining() > 0)
			try { 
				//Track thread wakeup time in case ack timer supercedes
				wakeup = next.timeRemaining() + System.currentTimeMillis();
				//Sleep until next timer expires	
				Thread.sleep(next.timeRemaining());
			} catch (InterruptedException ie){
				//Thread may be interrupted by a shorter timer
				//Restart method when that happens
				if (this.activeTimerCount() > 0)
					this.run();
				return; //Else dataLink has called end()
			}

		List<Timer> ready = new LinkedList<Timer>();
		synchronized(this.timers){
			Iterator<Timer> i = this.timers.iterator();
			while (i.hasNext()){ //Iterate through registered timers
				Timer t = i.next();
				if (t.expired()){
					i.remove(); //remove all expired ones
					if (!t.canceled) 
						ready.add(t); //Keep collection of non-canceled
					
				} else //Timers are sorted so no more are expired
					continue;
			}
		}
		
		//Notify DataLink of expired timers and remove from mapping
		for (Timer t : ready){
			this.dataLink.timeout(t.seqno);
			
			synchronized(this.timerMap){
				timerMap.remove(t.seqno);
			}
		}
		//If any timers remain, start again	
		if (activeTimerCount() > 0)
			this.run();
	}
	//Start a timer with the given sequence number and duration
	void startTimer(int seqno, int duration){
		stopTimer(seqno); //Stop timer if it's already running and start over

		long expiration = System.currentTimeMillis() + duration; 
		Timer t = new Timer(seqno, expiration);
		synchronized(this.timerMap){
			this.timerMap.put(seqno, t);
		}
		addTimer(t);
	}
	//Stop timer with the given sequence number
	void stopTimer(int seqno){
		Timer t;
		//Ok to remove from map
		synchronized(this.timerMap){
			t = this.timerMap.get(seqno);
			this.timerMap.remove(seqno);
		}
		//Clock thread using timers set so logically cancel timer
		if (t != null) 
			t.canceled = true;
	}
	//Register timer with Clock
	private void addTimer(Timer t){
		int timerCount;
		if (!this.active)
			return; //Clock has been ended
		synchronized(this.timers){
			this.timers.add(t);	 
		}

		if (this.activeTimerCount() == 1){
			this.thread = new Thread(this);
			this.thread.start(); //No other timers are active yet
		} else if (t.expiration < this.wakeup){
			this.thread.interrupt(); //New timer supercedes wakeup
		}
	}

	//DataLink is done
	void end(){
		this.active = false;
		try {
			this.thread.interrupt();
			this.thread.join();
		} catch (InterruptedException ie){
			ie.printStackTrace();
		}
	}
	//Data timers and ack timers - only one ack at a time
	class Timer implements Comparable<Timer> {
		private Integer seqno;
		private volatile long expiration;
		volatile boolean canceled = false;
		Timer(int seqno, long expiration){
			this.seqno = seqno;
			this.expiration = expiration;
		}
		long timeRemaining(){
			return expiration - System.currentTimeMillis();
		}
		boolean expired(){
			return timeRemaining() <= 0;
		}
		//Clock sorts timers by expiration so it can wake up for the next one
		@Override
		public int compareTo(Timer t){
			return Long.compare(this.expiration, t.expiration);
		}
	}
	//Get the number of active timers
	private int activeTimerCount(){
		synchronized(this.timerMap){
			return this.timerMap.size();
		}
	}	
}

