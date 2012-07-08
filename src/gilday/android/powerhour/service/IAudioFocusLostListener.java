package gilday.android.powerhour.service;

interface IAudioFocusLostListener {

	/**
	 * Called when the PowerHour SongPlayer has truly lost focus from the Audio system
	 * This callback should stop do the work of stopping the PowerHour
	 */
	void onAudioFocusLost();
}
