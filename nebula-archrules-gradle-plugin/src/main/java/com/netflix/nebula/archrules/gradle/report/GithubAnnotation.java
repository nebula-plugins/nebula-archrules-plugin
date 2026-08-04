package com.netflix.nebula.archrules.gradle.report;

import java.io.Serializable;

public record GithubAnnotation(String path, String annotation_level, String title, String message, String raw_details, int start_line) implements Serializable {
}
