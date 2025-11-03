package com.netflix.nebula.archrules.core;

import com.tngtech.archunit.lang.ConditionEvent;

import java.util.Collections;
import java.util.List;

/**
 * Custom event for representing a failure of allowEmptyShould in a non-exceptional way
 */
public class NoClassesMatchedEvent implements ConditionEvent {
    public static final String NO_MATCH_MESSAGE = "no classes matched the required condition";

    @Override
    public boolean isViolation() {
        return true;
    }

    @Override
    public ConditionEvent invert() {
        return this;
    }

    @Override
    public List<String> getDescriptionLines() {
        return List.of(NO_MATCH_MESSAGE);
    }

    @Override
    public void handleWith(Handler handler) {
        handler.handle(Collections.emptyList(), NO_MATCH_MESSAGE);
    }
}
