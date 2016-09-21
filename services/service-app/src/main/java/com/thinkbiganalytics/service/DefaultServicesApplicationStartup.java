package com.thinkbiganalytics.service;

import com.thinkbiganalytics.app.ServicesApplicationStartup;
import com.thinkbiganalytics.app.ServicesApplicationStartupListener;

import org.joda.time.DateTime;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * App startup listener that will execute each subscriber in a different thread Created by sr186054 on 9/21/16.
 */
public class DefaultServicesApplicationStartup implements ServicesApplicationStartup, ApplicationListener<ContextRefreshedEvent> {

    private DateTime startTime = null;
    private List<ServicesApplicationStartupListener> startupListeners = new ArrayList<>();

    int maxThreads = 10;

    ExecutorService executorService =
        new ThreadPoolExecutor(
            maxThreads, // core thread pool size
            maxThreads, // maximum thread pool size
            10, // time to wait before resizing pool
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(maxThreads, true),
            new ThreadPoolExecutor.CallerRunsPolicy());

    public void subscribe(ServicesApplicationStartupListener o) {
        startupListeners.add(o);
    }

    @Override
    public void onApplicationEvent(final ContextRefreshedEvent event) {
        if (startTime == null) {
            startTime = new DateTime();
            for (ServicesApplicationStartupListener startupListener : startupListeners) {
                executorService.submit(new StartupTask(startTime, startupListener));
            }
        }
    }

    private class StartupTask implements Runnable {

        ServicesApplicationStartupListener listener;
        DateTime startTime;

        public StartupTask(DateTime startTime, ServicesApplicationStartupListener listener) {
            this.startTime = startTime;
            this.listener = listener;
        }

        public void run() {
            listener.onStartup(startTime);
        }
    }
}