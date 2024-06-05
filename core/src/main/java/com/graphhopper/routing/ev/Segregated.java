package com.graphhopper.routing.ev;

import com.graphhopper.util.Helper;

/**
 * Defines in cycleways and footways.
 * If both have their own lane, tag segregated=yes. If they share one lane, tag segregated=no.
 * If not tagged it will be {@link #NO}
 */
public enum Segregated {
    YES, NO;

    public static final String KEY = "segregated";

    public static EnumEncodedValue<Segregated> create() {
        return new EnumEncodedValue<>(KEY, Segregated.class);
    }

    @Override
    public String toString() {
        return Helper.toLowerCase(super.toString());
    }

    public static Segregated find(String name) {
        if (Helper.isEmpty(name))
            return NO;
        try {
            return Segregated.valueOf(Helper.toUpperCase(name));
        } catch (IllegalArgumentException ex) {
            return NO;
        }
    }
}
