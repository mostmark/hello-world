package com.test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api")
public class HelloWorld {

    private static final List<byte[]> LEAK = new ArrayList<>();
    private static final Object LOCK = new Object();

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello World! The time is " + new Date();
    }

    @GET
    @Path("/hello/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    public String hello(@PathParam("name") String name) {
        return "Hello " + name + "!";
    }

    // JFR demo: memory that is never released (jdk.OldObjectSample)
    @GET
    @Path("/leak")
    @Produces(MediaType.TEXT_PLAIN)
    public String leak() {
        synchronized (LEAK) {
            for (int i = 0; i < 10; i++) {
                LEAK.add(new byte[100 * 1024]);
            }
            return "Leaked " + LEAK.size() / 10 + " MB";
        }
    }

    // JFR demo: heavy short-lived allocation (jdk.ObjectAllocationSample, jdk.GarbageCollection)
    @GET
    @Path("/garbage")
    @Produces(MediaType.TEXT_PLAIN)
    public String garbage() {
        int total = 0;
        for (int i = 0; i < 2_000_000; i++) {
            total += ("item-" + i).length();
        }
        return "Created " + total + " characters of garbage";
    }

    // JFR demo: requests blocked on a shared lock (quarkus.Rest, jdk.JavaMonitorEnter)
    @GET
    @Path("/contention")
    @Produces(MediaType.TEXT_PLAIN)
    public String contention() throws InterruptedException {
        synchronized (LOCK) {
            Thread.sleep(100);
        }
        return "Done";
    }

    // JFR demo: a custom application event (com.test.HelloWorld)
    @GET
    @Path("/event")
    @Produces(MediaType.TEXT_PLAIN)
    public String event() throws InterruptedException {
        HelloWorldEvent event = new HelloWorldEvent();
        event.begin();
        event.message = "Hello World!";
        Thread.sleep(42);
        event.commit();
        return "Hello Custom JFR Event!";
    }

    @Name("com.test.HelloWorld")
    @Label("HelloWorld")
    @Category("Hello World")
    static class HelloWorldEvent extends Event {
        @Label("Message")
        String message;
    }
}
