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

Send some requests. For example, to cause lock contention:

```shell
seq 1 40 | xargs -P 10 -I{} curl -s -o /dev/null localhost:8080/api/contention
```

Save and stop the recording, then print the events you're interested in:

```shell
jcmd quarkus-run.jar JFR.dump name=demo path-to-gc-roots=true filename=demo.jfr
jcmd quarkus-run.jar JFR.stop name=demo
jfr print --events jdk.JavaMonitorEnter demo.jfr
```

The leak stays in memory until you restart the app, so run that demo last or on its own.

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
