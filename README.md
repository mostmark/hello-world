# hello-world

A classic Hello World application for testing purposes, built with Quarkus. It's used for demonstrating JDK Flight Recorder (JFR) and Cryostat.

## Endpoints

The landing page at `/` links to all of them.

| Endpoint | What it does | JFR events to look for |
|---|---|---|
| `/api` | Returns a greeting with the current time | |
| `/api/hello/<name>` | Returns `Hello <name>!` | |
| `/api/leak` | Keeps 1 MB more in memory with every request, until you restart the app | `jdk.OldObjectSample` |
| `/api/garbage` | Creates 2 million short-lived strings per request | `jdk.ObjectAllocationSample`, `jdk.GarbageCollection` |
| `/api/contention` | Holds a shared lock for 100 ms, so concurrent requests wait in line | `quarkus.Rest`, `jdk.JavaMonitorEnter` |
| `/api/event` | Records a custom event | `com.test.HelloWorld` |

The `quarkus.Rest` event is recorded for every endpoint.

## Run the app

You need Java 21 and Maven.

```shell
mvn quarkus:dev
```

Then open <http://localhost:8080/>.

## Record with JFR

The app doesn't start a recording by itself. Build it and start it:

```shell
mvn package
java -Xmx256m -jar target/quarkus-app/quarkus-run.jar
```

In a second terminal in the same directory, start a recording:

```shell
jcmd quarkus-run.jar JFR.start name=demo settings=profile
```

### Record contention

Send 40 requests, 10 at a time, so they wait in line for the shared lock:

```shell
seq 1 40 | xargs -P 10 -I{} curl -s -o /dev/null localhost:8080/api/contention
```

Save and stop the recording, then print the events you're interested in:

```shell
jcmd quarkus-run.jar JFR.dump name=demo filename=demo-contention.jfr
jcmd quarkus-run.jar JFR.stop name=demo
jfr print --events jdk.JavaMonitorEnter demo-contention.jfr
```

In JDK Mission Control:

- **Java Application → Lock Instances:** the shared lock, a `java.lang.Object`, with how often and how long threads were blocked on it.
- **Java Application → Threads:** the `executor-thread-*` lanes show light orange bars while a thread waits for the lock, and blue bars while it holds the lock in `Thread.sleep()`.
- **Event Browser → Java Application → Java Monitor Blocked:** each wait, with its duration and the stack trace pointing to `contention()`.

### Record garbage collection

If you've run the leak demo, restart the app first. Then start a recording as above and send 1,000 requests, 4 at a time:

```shell
seq 1 1000 | xargs -P 4 -I{} curl -s -o /dev/null localhost:8080/api/garbage
```

This takes about 10 seconds. Each request creates 2 million strings that are thrown away right after, so the garbage collector runs many times a second.

Save and stop the recording, then print a summary of the GC pauses:

```shell
jcmd quarkus-run.jar JFR.dump name=demo filename=demo-garbage.jfr
jcmd quarkus-run.jar JFR.stop name=demo
jfr view gc-pauses demo-garbage.jfr
```

In JDK Mission Control:

- **Java Application → Memory:** the heap usage rises and drops in a steady sawtooth. In the allocation table, `byte[]` is at the top. Expand its stack trace to find `garbage()`.
- **JVM Internals → Garbage Collections:** hundreds of young collections, each pausing the app for well under a millisecond. The heap after each collection stays flat, unlike in the leak demo.
- **JVM Internals → GC Summary:** the number of collections and the total and average pause time.

### Record a memory leak

The leak stays in memory until you restart the app, so restart it before this demo. Then start a recording as above and send the leak requests one at a time, so the heap grows steadily:

```shell
for i in $(seq 1 150); do curl -s -o /dev/null localhost:8080/api/leak; sleep 0.2; done
```

This keeps about 150 MB in memory over 40 seconds. Don't send many more: the 256 MiB heap is full after about 248 requests.

Save the recording while the app is still running, because the leaked objects are captured when you dump. `path-to-gc-roots=true` adds what keeps each object in memory.

```shell
jcmd quarkus-run.jar JFR.dump name=demo path-to-gc-roots=true filename=demo-memory-leak.jfr
jcmd quarkus-run.jar JFR.stop name=demo
jfr print --events jdk.OldObjectSample demo-memory-leak.jfr
```

In JDK Mission Control:

- **Automated Analysis Results:** Heap Live Set Trend is red and names the static `LEAK` list.
- **JVM Internals → Garbage Collections:** the heap after each collection keeps growing.
- **Java Application → Memory → Live Objects:** expand the tree to see the leaked `byte[]` arrays, the `LEAK` list that holds them, and the line in `leak()` that allocated them.

## Build and push the container image

You need Podman, and to be logged in to Quay. The script builds the app and pushes a multi-arch image for `linux/amd64` and `linux/arm64` to `quay.io/$QUAY_USER/hello-world:latest`.

```shell
export QUAY_USER=your-quay-username
./build-push-container.sh
```

Then run the image:

```shell
podman run --rm -p 8080:8080 quay.io/$QUAY_USER/hello-world:latest
```

The image uses a 256 MiB heap and the G1 garbage collector, the same as the `java` command above. It doesn't start a recording by itself either.
