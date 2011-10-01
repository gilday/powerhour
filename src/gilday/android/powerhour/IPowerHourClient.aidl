// PowerHourService.aidl

// Include your fully-qualified package statement.
package gilday.android.powerhour;

// See the list above for which classes need
// import statements (hint--most of them)

// Declare the interface.
oneway interface IPowerHourClient {
    
    // Methods can take 0 or more parameters, and
    // return a value or void.
    
    void secondCompleted(in int seconds, in int songId);
}