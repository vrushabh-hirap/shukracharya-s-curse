package com.vrushabhhirap.shukracharyascurse.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Historical date formatting and astronomical/lunar calculation helpers
 * for displaying astrological manuscript annotations.
 */
public final class DateUtils {

    private DateUtils() {}

    public static final String[] LUNAR_MANSIONS_NAKSHATRAS = {
            "Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashirsha", "Ardra",
            "Punarvasu", "Pushya", "Ashlesha", "Magha", "Purva Phalguni", "Uttara Phalguni",
            "Hasta", "Chitra", "Svati", "Vishakha", "Anuradha", "Jyeshtha",
            "Mula", "Purva Ashadha", "Uttara Ashadha", "Shravana", "Dhanishta",
            "Shatabhisha", "Purva Bhadrapada", "Uttara Bhadrapada", "Revati"
    };

    /**
     * Approximates current Nakshatra based on epoch day cycle.
     */
    public static String getCurrentNakshatra(long epochMillis) {
        long days = epochMillis / (1000 * 60 * 60 * 24);
        int index = (int) (days % LUNAR_MANSIONS_NAKSHATRAS.length);
        if (index < 0) index += LUNAR_MANSIONS_NAKSHATRAS.length;
        return LUNAR_MANSIONS_NAKSHATRAS[index];
    }

    /**
     * Formats timestamp into an ISO-8601 UTC date string.
     */
    public static String toIso8601(long epochMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(epochMillis));
    }

    /**
     * Formats timestamp into standard human-readable display string.
     */
    public static String toDisplayDate(long epochMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(epochMillis));
    }

    /**
     * Computes elapsed human-readable duration from past timestamp.
     */
    public static String formatElapsed(long startMillis, long endMillis) {
        long delta = Math.max(0, endMillis - startMillis);
        long seconds = delta / 1000;
        if (seconds < 60) return seconds + "s ago";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        return days + "d ago";
    }
}
