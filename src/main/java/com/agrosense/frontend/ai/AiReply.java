package com.agrosense.frontend.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/** The assistant's answer; {@code transcript} is what it understood from a voice message. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiReply(String reply, @JsonInclude(JsonInclude.Include.NON_NULL) String transcript) {
}