package com.example.pinspire.ui.feed;

import java.util.Objects;

/** Describes what a feed shows. Immutable; compare with equals(). */
public final class FeedSpec {
    public enum Mode { FOR_YOU, EXPLORE, CATEGORY, SEARCH, SPARK, RELATED }

    public final Mode mode;
    /** Category label, search query, or related-pin query depending on mode. */
    public final String value;
    /** Pixabay color filter or "" for any color. */
    public final String color;
    /** Pin id to leave out (for related feeds). */
    public final String exclude;

    public FeedSpec(Mode mode, String value, String color, String exclude) {
        this.mode = mode;
        this.value = value == null ? "" : value;
        this.color = color == null ? "" : color;
        this.exclude = exclude == null ? "" : exclude;
    }

    public static FeedSpec forYou() { return new FeedSpec(Mode.FOR_YOU, "", "", ""); }

    public FeedSpec withColor(String c) { return new FeedSpec(mode, value, c, exclude); }

    @Override public boolean equals(Object o) {
        if (!(o instanceof FeedSpec)) return false;
        FeedSpec s = (FeedSpec) o;
        return mode == s.mode && value.equals(s.value) && color.equals(s.color) && exclude.equals(s.exclude);
    }

    @Override public int hashCode() { return Objects.hash(mode, value, color, exclude); }
}
