# Purpose
This repo is used to recreate a bug which is causing an infinite loop
in the shaded io.micrometer.shaded.io.netty.util.Recycler when the logging is 
in DEBUG mode.

# Reproducing the Issue
## Build
```
mvn clean install
```

## Reproducing the issue
```
java -DMICRO_LOGLEVEL=debug -Ddemo.custom.registry=true -Dmanagement.metrics.export.statsd.enabled=false -jar target/demo-0.0.1-SNAPSHOT.jar
```

When executed the following output will loop forever.
```
BUGLOG time: 1605795400720 thread:main tid: 1 loop: 0 max: 4096 len: 0
BUGLOG time: 1605795400720 thread:main tid: 1 loop: 0 max: 4096 len: 0
BUGLOG time: 1605795400720 thread:main tid: 1 loop: 0 max: 4096 len: 0
...
```

The output is coming from a patched version of the Recycler.java that was created after
decompiling the class file.  To prove the patched file is not the problem itself, you can rename this file and rerun and you will observe that the
java process never initializes and 8080 is unavailable.  I created the file to trace where the looping
was occurring.

If you do that it will hang with output as seen below.
```
[udp-nio-1] 19 Nov 2020 14:50:52,226+0000 DEBUG io.micrometer.shaded.reactor.netty.resources.NewConnectionProvider [{}]: [id: 0xe252e00d, L:/127.0.0.1:58280 - R:localhost/127.0.0.1:8125] onStateChange([configured], ChannelOperations{SimpleConnection{channel=[id: 0xe252e00d, L:/127.0.0.1:58280 - R:localhost/127.0.0.1:8125]}})
[udp-nio-1] 19 Nov 2020 14:50:52,227+0000 DEBUG io.micrometer.shaded.reactor.netty.udp.UdpClient [{}]: [id: 0xe252e00d, L:/127.0.0.1:58280 - R:localhost/127.0.0.1:8125] Handler is being applied: io.micrometer.statsd.StatsdMeterRegistry$$Lambda$426/999230073@44e0daa9
```

In the end what you observe is that the Recycler.java constructor is firing before
the Recycler.java static block is completing.  There is a critical line that sets INITIAL_CAPACITY which is not getting executed prior to the constructor firing.  This issue only occurs when the logger.debug() statements get called.

I patched many other files and see that is seems related to the log42j metrics that are being collected by micrometer.

If you run this app in INFO level then everything initializes perfectly.

# Patched Recycler

Once we discovered  the connection to logging, we narrowed things down to the Recycler.java class.
We patched many files that are invoked as a result of the logger.debug() and
that took us into log4j2 micrometer metrics and ultimately back into NettyOutbound.  There
the trail ran cold again, however, I did learn enough to recreate the problem in this app.

# Observations

- Recycler.java constructor is being executed in the main thread prior to the second static block completes.  I throught that
was impossible in java, however, that can happen if there is a circular static reference.  Not sure how else that may happen.
- The static block and the constructor of Recycler are being invoked on the same main thread.
- The app initializes properly if the log level is INFO or higher.
- If the log level is debug or lower, then the application enters an infinite loop at line 490 of the patched **Recycler.java** file because **this.elements.length** is zero and therefore newCapacity is zero.
```
            do {
                sysout("loop: %d max: %d len: %d", newCapacity , maxCapacity, this.elements.length);
                newCapacity <<= 1;
            } while (newCapacity < expectedCapacity && newCapacity < maxCapacity);

```
- The looping starts prior the execution of line 107 of the same file. It appears in the static initializer and appears after 
the logging statements.
```
        INITIAL_CAPACITY = Math.min(DEFAULT_MAX_CAPACITY_PER_THREAD, 256);
```
- Our sysout() messages show a ~30 ms pause for the first log statement to occur when the problem is reproduced.  That pause
is occurring somewhere in the NettyOutbound class.
- We also notice that this only occurs when we create a custom StatsD MeterRegistry.  If we let springboot
create the registry we do not see the issue.
- The debug output from the static block never makes it into the log file although other logs that
appear before and after do which is why we added the **sysout()** calls.
- If we disable our custom registry (ie run without the -Ddemo.custom.registry) there is no issue
- If we disable spring-boots autoconfig with -Dmanagement.metrics.export.statsd.enabled=false there is no issue
- If both beans are create then we actually notice there are 3 MeterRegistry beans. One of
which is io.micrometer.core.instrument.composite.CompositeMeterRegistry. In this configuration the issue is manifest.
