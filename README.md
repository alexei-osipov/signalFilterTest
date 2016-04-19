# Signal Filter Test Assignment

External system generates signals at random intervals. You need to implement Java filter interface that will reject a signal if there weeer more than N signals in the last 100 seconds.

Filter must implement the following interface:
```java	
	interface Filter {
		boolean isSignalAllowed();
	}
```
 
The following pseudo-code illustrates how you filter may be used:
```java
	Filter frequencyFilter = new YourFilter(N, 100); 
	while (true) {
		Signal signal = waitForSignal();
		if (frequencyFilter.isSignalAllowed()) {
		   transmit (signal);
		}
	}
```

Implementation must be efficient and thread-safe. Parameter N can be a immutable for lifetime of the filter.
