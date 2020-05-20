package com.frolo.muse.logger;

import android.content.Context;

import java.util.Arrays;


// Factories
public final class EventLoggers {
    private EventLoggers() { }

    public static EventLogger createFlurry(Context context) {
        return new FlurryEventLogger(context);
    }

    public static EventLogger createFirebase(Context context) {
        return new FirebaseEventLogger(context);
    }

    public static EventLogger createMute() {
        return new MuteEventLogger();
    }

    public static EventLogger createDroid() {
        return new AndroidEventLogger();
    }

    public static EventLogger compose(EventLogger... loggers) {
        return new CompositeEventLogger(Arrays.asList(loggers));
    }
}
