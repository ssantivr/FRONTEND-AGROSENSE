package com.agrosense.frontend.backend;

import java.time.Instant;

/**
 * What the pages know about the backend API without waiting for it. OFF: not configured, every operation uses
 * the database. UNKNOWN: not asked yet. UP: answering, operations go through it. WAKING: it did not answer and
 * is being woken up (free hosts stop idle services; the first answer can take a minute). DOWN: it did not
 * answer after waking it; operations use the database directly until it does.
 */
public record BackendStatus(State state, Instant checkedAt) {

    public enum State {
        OFF, UNKNOWN, UP, WAKING, DOWN
    }

    public boolean usesDatabase() {
        return state != State.UP;
    }
}
