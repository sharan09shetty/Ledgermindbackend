package com.ledgermind.ledgermindbackend.email.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum Category {
    FOOD,
    TRAVEL,
    ENTERTAINMENT,
    SHOPPING,
    BILLS,
    INVESTMENT,
    SALARY,
    GROCERIES,
    TRANSFER,
    HEALTH,
    OTHER;

    /**
     * Comma-separated list of all category names, e.g. "FOOD, TRAVEL, ...".
     * Use this anywhere a category list needs to be shown or sent to an LLM
     * prompt, instead of hardcoding the names - keeps every caller in sync
     * automatically when a category is added/removed here.
     */
    public static String namesCsv() {
        return Arrays.stream(values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }
}
