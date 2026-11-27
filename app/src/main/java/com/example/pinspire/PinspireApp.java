package com.example.pinspire;

import android.app.Application;

import com.example.pinspire.data.AppExecutors;
import com.example.pinspire.data.PinRepository;
import com.example.pinspire.data.PixabayClient;

public class PinspireApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        PinRepository.get(this);
        AppExecutors.io(() -> PixabayClient.pruneCache(this));
    }
}
