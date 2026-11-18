package com.agrosense.frontend.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiReply(String reply, @JsonInclude(JsonInclude.Include.NON_NULL) String transcript,
        @JsonInclude(JsonInclude.Include.NON_NULL) String source) {
}