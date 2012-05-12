// PowerHourService.aidl

// Include your fully-qualified package statement.
package gilday.android.powerhour;

// See the list above for which classes need
// import statements (hint--most of them)

// Declare the interface.
interface IPowerHourService {
    
    // Methods can take 0 or more parameters, and
    // return a value or void.
    
    void stop();
    void pause();
    void start();
    int skip();
    int getPlayingState();
    int getProgress();

    // All non-Java primitive parameters (e.g., int, bool, etc) require
    // a directional tag indicating which way the data will go. Available
    // values are in, out, inout. (Primitives are in by default, and cannot be otherwise).
    // Limit the direction to what is truly needed, because marshalling parameters
    // is expensive.
}