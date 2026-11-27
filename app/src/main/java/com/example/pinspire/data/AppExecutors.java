package com.example.pinspire.data;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Process-wide background pool. Lives as long as the app, so rotating the screen never kills work. */
public final class AppExecutors {
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppExecutors() {}

    public static void io(Runnable r) { IO.execute(r); }

    public static void main(Runnable r) { MAIN.post(r); }
}
