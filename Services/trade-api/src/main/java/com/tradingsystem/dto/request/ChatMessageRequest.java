package com.tradingsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/v1/chat/message
 */
public class ChatMessageRequest {

    @NotBlank(message = "Message must not be blank")
    @Size(max = 2000, message = "Message must be 2000 characters or fewer")
    private String message;

    public ChatMessageRequest() {}

    public ChatMessageRequest(String message) {
        this.message = message;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
