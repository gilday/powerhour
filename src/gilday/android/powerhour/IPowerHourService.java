package gilday.android.powerhour;


public interface IPowerHourService {

	void start();
	
	/**
	 * Sends a command to end the power hour service and kill off its resources.
	 * Should drop reference to power hour service object after this call
	 */
	void stop();
	
	/**
	 * Pause command will change the power hour service's playing state to paused, will 
	 * end update notification, stop the music, and will pause the timer counting progress
	 */
	void pause();
	
	/**
	 * Skip command will skip the current song and load the next without affecting power hour 
	 * progress. Returns the song ID of the loaded song. Returns -1 if there are no more songs 
	 * to load
	 * @return
	 */
	int skip();
	
	/**
	 * Returns PLAYING, PAUSED to indicate state
	 * @return
	 */
	int getPlayingState();
	
	/**
	 * @return number of seconds that have passed
	 */
	int getProgress();
}
