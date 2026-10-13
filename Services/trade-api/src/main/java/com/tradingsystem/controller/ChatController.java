package com.tradingsystem.controller;

import com.tradingsystem.ai.chat.ChatService;
import com.tradingsystem.dto.request.ChatMessageRequest;
import com.tradingsystem.dto.response.ChatMessageResponse;
import com.tradingsystem.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the AI chatbot.
 * POST /api/v1/chat/message
 *
 * Requires JWT authentication (handled by JwtAuthenticationFilter).
 * The authenticated user's account ID is extracted via CurrentUser.
 */
@RestController
@RequestMapping("/api/v1/chat")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class ChatController {

    private final ChatService chatService;
    private final CurrentUser currentUser;

    public ChatController(ChatService chatService, CurrentUser currentUser) {
        this.chatService = chatService;
        this.currentUser = currentUser;
    }

    /**
     * Accepts a user question and returns the LLM reply with related signals.
     */
    @PostMapping("/message")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @Valid @RequestBody ChatMessageRequest request
    ) {
        Long accountId = currentUser.getAccountId();
        ChatMessageResponse response = chatService.chat(accountId, request.getMessage());
        return ResponseEntity.ok(response);
    }
}
