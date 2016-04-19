# signalFilterTest
Signal Filter Test Assignment

External system generates signals at random intervals.

You need to implement Java filter interface that will reject a signal if there weeer more than N signals in the last 100 seconds.

Details: Filter must implement the following interface:

interface Filter {
    boolean isSignalAllowed();
}

 
Method isSignalAllowed() will be called by system every time it generates a signal.
Method must return 'true' if current signal should be allowed, 'false' otherwise. 

The following pseudo-code illustrates how you filter may be used:

Filter frequencyFilter = new YourFilter(N, 100); 
while (true) {
    Signal signal = waitForSignal();
    if (frequencyFilter.isSignalAllowed()) {
       transmit (signal);
    }
}

Implementation must be efficient and thread-safe. Parameter N can be a immutable for lifetime of the filter.