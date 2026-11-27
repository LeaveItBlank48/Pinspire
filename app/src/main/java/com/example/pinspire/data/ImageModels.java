package com.example.pinspire.data;

import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

/** Turns a stored image uri into a Glide model, adding a User-Agent for web images. */
public final class ImageModels {
    private ImageModels() {}

    public static Object of(String uri) {
        if (uri != null && uri.startsWith("https://")) {
            return new GlideUrl(uri, new LazyHeaders.Builder().addHeader("User-Agent", CommonsClient.USER_AGENT).build());
        }
        return uri;
    }
}
