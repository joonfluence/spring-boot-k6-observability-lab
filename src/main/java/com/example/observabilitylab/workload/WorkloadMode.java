package com.example.observabilitylab.workload;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum WorkloadMode {
    CPU("cpu"),
    IO("io"),
    DB("db");

    private final String value;

    WorkloadMode(String value) {
        this.value = value;
    }

    public static WorkloadMode from(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String value() {
        return value;
    }
}
