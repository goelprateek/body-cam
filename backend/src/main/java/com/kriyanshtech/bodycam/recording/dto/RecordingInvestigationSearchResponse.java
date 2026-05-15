package com.kriyanshtech.bodycam.recording.dto;

import java.util.List;

public record RecordingInvestigationSearchResponse(
        String query,
        Integer totalMatches,
        List<RecordingInvestigationSearchHitResponse> hits
) {
}
