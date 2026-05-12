package com.kaito.autoupdate.core;

import java.util.List;

public record UpdateSummary(
    boolean hasAnyUpdate,
    List<String> updatesAvailable,
    List<String> updatedFiles
) {}

