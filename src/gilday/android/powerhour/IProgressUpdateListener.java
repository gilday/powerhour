package gilday.android.powerhour;

public interface IProgressUpdateListener {
	
	void onProgressUpdate(int currentMinute);
	
	/**
	 * Fired when the Power Hour's progress has been paused either by the user 
	 * or by the system for loss of focus (phone call, other music player, youtube fun times, etc)
	 */
	void onProgressPaused();
	
	/**
	 * Fired when the Power Hour's resumes
	 */
	void onProgressResumed();
}
