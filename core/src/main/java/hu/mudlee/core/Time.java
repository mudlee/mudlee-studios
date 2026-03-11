package hu.mudlee.core;

public class Time {
    public static long timeStarted = System.nanoTime();

    public static double getTime() {
        return (System.nanoTime() - timeStarted) * 1e-9;
    }
}
