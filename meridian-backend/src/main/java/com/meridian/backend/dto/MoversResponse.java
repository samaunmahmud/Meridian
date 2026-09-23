package com.meridian.backend.dto;

import java.util.List;

// Biggest rises first in gainers, biggest falls first in losers. A ticker that has not moved is in neither.
public record MoversResponse(List<MoverResponse> gainers, List<MoverResponse> losers) {
}
