package planespotter.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import planespotter.controller.Controller;
import planespotter.throwables.InvalidDataException;
import planespotter.throwables.OutOfRangeException;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @name Scheduler
 * @author jml04
 * @version 1.1
 *
 * @description
 * Scheduler class contains all thread pool executors and is responsible for threading.
 * It is able to execute tasks once and in period,
 * hold it static if you need only one instance, else use multiple instances
 * which can be started and stopped parallel.
 * @see Controller
 * @see ThreadMaker
 * @see ThreadPoolExecutor
 * @see ScheduledExecutorService
 */
public class Scheduler {

    /**
     * Thread priority constants:
     *  LOW_PRIO is 2,
     *  MID_PRIO is 5,
     *  HIGH_PRIO is 9
     */
    public static final byte LOW_PRIO, MID_PRIO, HIGH_PRIO;

    // thread maker (thread factory)
    private static final ThreadMaker threadMaker;

    // handler for rejected executions
    private static final RejectedExecutionHandler rejectedExecutionHandler;

    // initializing static fields
    static {
        LOW_PRIO = 1;
        MID_PRIO = 5;
        HIGH_PRIO = 9;
        threadMaker = new ThreadMaker();
        rejectedExecutionHandler = (r, exe) -> System.out.println("Task " + r.toString() + " rejected from Scheduler!");
    }

    // ThreadPoolExecutor for (parallel) execution of different threads
    private final ThreadPoolExecutor exe;

    // ScheduledExecutorService for scheduled execution at a fixed rate
    private final ScheduledExecutorService scheduled_exe;

    /**
     * constructor, creates a new Scheduler with a max size
     * of 20 and a thread-keep-alive-time of 4 seconds
     */
    public Scheduler() {
        this(20, 4L);
    }

    /**
     * second Scheduler constructor with specific
     * pool size and thread-keep-alive-time
     *
     * @param maxPoolSize is the max. ThreadPool-size
     * @param keepAliveSeconds is the keep-alive-time in seconds
     */
    public Scheduler(int maxPoolSize, long keepAliveSeconds) {
        this.exe = new ThreadPoolExecutor(0, maxPoolSize, keepAliveSeconds,
                                          TimeUnit.SECONDS, new SynchronousQueue<>(), threadMaker, rejectedExecutionHandler);
        this.scheduled_exe = new ScheduledThreadPoolExecutor(0, threadMaker, rejectedExecutionHandler);
    }

    /**
     * lets the current Thread sleep for a certain amount of seconds
     *
     * @param seconds are the seconds to wait
     * @return always false
     */
    public static boolean sleepSec(long seconds) {
        return sleep(seconds * 1000);
    }

    /**
     * lets the current thread sleep for a certain amount of milliseconds
     *
     * @param millis are the millis to sleep
     * @return always false
     */
    public static boolean sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            System.err.println("Scheduler: sleep interrupted!");
        }
        return false;
    }

    /**
     * executed a thread with a certain name and period
     *
     * @param tName is the thread name
     * @param task is the Runnable to execute in period
     * @param initDelay is the src delay in seconds, must be 1 or higher
     * @param period is the period in seconds, must be 0 or higher
     */
    @NotNull
    public final ScheduledFuture<?> schedule(@NotNull Runnable task, @NotNull String tName, int initDelay, int period) {
        return schedule(() -> {
            Thread.currentThread().setName(tName);
            task.run();
        }, initDelay, period);
    }

    /**
     * executes a task in a specific period
     *
     * @param task is the Runnable to execute in period
     * @param initDelay is the src delay in milliseconds, must be 1 or higher
     * @param period is the period in milliseconds, must be 0 or higher
     */
    @NotNull
    public final ScheduledFuture<?> schedule(@NotNull Runnable task, int initDelay, int period) {
        if (initDelay < 0) {
            throw new IllegalArgumentException("init delay out of range! must be 0 or higher!");
        } if (period < 1) {
            throw new IllegalArgumentException("period out of range! must be 1 or higher!");
        }
        return scheduled_exe.scheduleAtFixedRate(task, initDelay, period, TimeUnit.MILLISECONDS);
    }

    /**
     * executes a single thread with custom name
     * executed tasks run with timeout
     *
     * @param target is the Runnable to execute
     * @param tName is the Thread-Name
     */
    @NotNull
    public final CompletableFuture<Void> exec(@NotNull Runnable target, @NotNull String tName) {
        return exec(target, tName, false, 5, true);
    }

    /**
     * executes a single task as a thread
     *  @param target is the Runnable to execute
     * @param tName is the thread name
     * @param daemon is the value if the thread is a daemon thread
     * @param prio is the priority from 1-10
     * @param withTimeout if the task should have a timeout
     * @return the running task as {@link CompletableFuture}
     */
    @NotNull
    public CompletableFuture<Void> exec(@NotNull Runnable target, @NotNull String tName, boolean daemon, int prio, boolean withTimeout) {
        if (prio < 1 || prio > 10) {
            throw new IllegalArgumentException("priority must be between 1 and 10!");
        }
        this.getThreadFactory().addThreadProperties(tName, daemon, prio);
        if (withTimeout) {
            AtomicReference<Thread> currentThread = new AtomicReference<>();
            return CompletableFuture.runAsync(target, exe)
                    .orTimeout(15, TimeUnit.SECONDS)
                    .exceptionally(e -> {
                        Controller.getInstance().handleException(e);
                        currentThread.set(Thread.currentThread());
                        interruptThread(currentThread.get());
                        // FIXME: 22.05.2022 interrupt läuft noch nicht
                        return null;
                    });
        } else {
            return CompletableFuture.runAsync(target, exe);
        }
    }

    /**
     *
     *
     * @param target
     * @param delayMillis
     * @return
     */
    @NotNull
    public CompletableFuture<Void> delayed(@NotNull Runnable target, int delayMillis) {
        return exec(() -> scheduled_exe.schedule(target, delayMillis, TimeUnit.MILLISECONDS), "Delayed Task", false, MID_PRIO, false);
    }

    /**
     * runs a task as a new Thread, but not with the ThreadPoolExecutor
     *
     * @param target is the target runnable to execute
     * @param tName is the thread name
     * @param daemon is the daemon flag
     * @param prio is the priority from 1-10
     */
    @NotNull
    public Thread runThread(@NotNull Runnable target, @NotNull String tName, boolean daemon, @Range(from = 1, to = 10) int prio) {
        Thread thread = new Thread(target);
        thread.setName(tName);
        thread.setPriority(prio);
        thread.setDaemon(daemon);
        thread.start();
        return thread;
    }

    /**
     * runs a short task ({@link Runnable}) as non-daemon thread with
     * the name 'Short-Task' and a priority of 5 (MID_PRIO)
     *
     * @param task is the {@link Runnable} that is executed as the task
     */
    @NotNull
    public Thread shortTask(@NotNull Runnable task) {
        return runThread(task, "Short-Task", false, MID_PRIO);
    }

    /**
     * awaits a single task of different types
     *
     * @param task is the task that should be awaited, must be an
     *             instance of {@link Thread} or {@link CompletableFuture}
     * @throws InterruptedException if the waiting is interrupted
     */
    public <T> void await(@NotNull T task)
            throws InterruptedException {

        if (task instanceof Thread thread) {
            thread.join();
        } else if (task instanceof CompletableFuture<?> future) {
            future.join();
        } else {
            throw new InvalidDataException("Task must be instance of Thread or CompletableFuture!");
        }
    }

    /**
     * tries to interrupt a certain thread
     *
     * @param target is the thread to interrupt
     */
    public synchronized boolean interruptThread(@NotNull Thread target) {
        if (target.isAlive()) {
            target.interrupt();
        }
        return target.isInterrupted();
    }

    /**
     * shuts down the Scheduler
     *
     * @return true if the shutdown was successfully
     */
    public synchronized boolean shutdown(final int timeout) {
        final TimeUnit sec = TimeUnit.SECONDS;
        try {
            return exe.awaitTermination(timeout, sec) && scheduled_exe.awaitTermination(timeout, sec);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * shuts down the Scheduler directly
     */
    public synchronized boolean shutdownNow() {
        this.exe.shutdownNow();
        this.scheduled_exe.shutdownNow();
        sleep(1000);
        return     (exe.isShutdown() || exe.isTerminated() || exe.isTerminating())
                && (scheduled_exe.isShutdown() || scheduled_exe.isTerminated());
    }

    /**
     * @return the thread maker which contains a method to add thread properties
     */
    public ThreadMaker getThreadFactory() {
        return threadMaker;
    }

    /**
     * @return active thread count from exe-ThreadPoolExecutor,
     *         not from the scheduled executor
     */
    public int active() {
        return exe.getActiveCount();
    }

    /**
     * @return completed task count from exe-ThreadPoolExecutor,
     *         not from the scheduled executor
     */
    public long completed() {
        return exe.getCompletedTaskCount();
    }

    /**
     * @return largest thread pool size from exe-ThreadPoolExecutor,
     *         not from the scheduled executor
     */
    public int largestPoolSize() {
        return exe.getLargestPoolSize();
    }

    /**
     * @name ThreadMaker
     * @author jml04
     * @version 1.0
     *
     * ThreadMaker is a custom ThreadFactory which is able to set
     * thread properties like name, daemon or priority
     */
    private static class ThreadMaker implements ThreadFactory {

        // thread name
        private volatile String name;

        // daemon thread?
        private volatile boolean daemon = false;

        // thread priority
        private volatile int priority = -1;

        /**
         * creates a new thread with custom properties
         * and a custom UncaughtExceptionHandler
         *
         * @param r is the thread target (the executed action)
         * @return new Thread with custom properties
         */
        @Override
        public Thread newThread(@NotNull Runnable r) {
            Thread thread = new Thread(r);
            setThreadProperties(thread);
            thread.setUncaughtExceptionHandler((t, e) -> { // t is the thread, e is the exception
                e.printStackTrace();
                Controller.getInstance().handleException(e);
            });
            return thread;
        }

        /**
         * adds thread properties parameters to the class fields
         *
         * @param name is the thread name
         * @param daemon should the thread be a daemon thread?
         * @param prio is the thread priority
         */
        public synchronized void addThreadProperties(@NotNull String name, boolean daemon, int prio) {
            if (prio < 1 || prio > 10) {
                throw new OutOfRangeException("Thread priority out of range! (1-10)");
            }
            this.name = name;
            this.daemon = daemon;
            this.priority = prio;
        }

        /**
         * sets the thread properties on a certain thread, if they are valid
         *
         * @param target is the target thread on which properties are set
         */
        private synchronized void setThreadProperties(@NotNull Thread target) {
            if (name != null) {
                target.setName(name);
            } if (priority != -1) {
                target.setPriority(priority);
            }
            target.setDaemon(daemon);
        }

    }
}
