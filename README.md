# Signal Filter Test Assignment

External system generates signals at random intervals. You need to implement Java filter that accepts at most N signals per minute.

Filter must implement the following interface:
```java	
	interface Filter {
		boolean isSignalAllowed();
	}
```
 
The following pseudo-code illustrates how your filter could be used:
```java
	Filter frequencyFilter = new YourFilter(N); 
	while (true) {
		Signal signal = waitForSignal();
		if (frequencyFilter.isSignalAllowed()) {
		   process (signal);
		}
	}
```

Implementation must be *efficient* and *thread-safe*. Parameter N can be a immutable for lifetime of the filter. Another example can be found [here](https://github.com/andymalakov/signalFilterTest/blob/master/src/test/java/test/filter/api/FilterTest.java).

If you have questions don't hesitate to ask.
