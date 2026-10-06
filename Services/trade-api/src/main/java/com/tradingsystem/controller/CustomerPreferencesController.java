package com.tradingsystem.controller;

import com.tradingsystem.model.CustomerPreferences;
import com.tradingsystem.security.CurrentUser;
import com.tradingsystem.service.CustomerPreferencesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class CustomerPreferencesController {

    private final CustomerPreferencesService service;
    private final CurrentUser currentUser;

    public CustomerPreferencesController(CustomerPreferencesService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/{accountId}/preferences")
    public ResponseEntity<CustomerPreferences> getPreferences(@PathVariable Long accountId) {
        if (!accountId.equals(currentUser.getAccountId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(service.getPreferences(accountId));
    }

    @PutMapping("/{accountId}/preferences")
    public ResponseEntity<CustomerPreferences> upsertPreferences(@PathVariable Long accountId, @RequestBody CustomerPreferences payload) {
        if (!accountId.equals(currentUser.getAccountId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(service.upsertPreferences(accountId, payload));
    }

    @GetMapping("/internal/{accountId}/notifications/resolve")
    public ResponseEntity<Object> resolve(@PathVariable Long accountId, @RequestParam String eventType, @RequestHeader(value = "Authorization", required = false) String auth) {
        // internal callers must present service token in Authorization header
        String token = (auth == null) ? "" : auth.replaceFirst("(?i)bearer\\s+", "").trim();
        String internalToken = System.getenv().getOrDefault("INTERNAL_SERVICE_TOKEN", "dev-internal-token");
        if (!internalToken.equals(token)) {
            return ResponseEntity.status(401).build();
        }

        List<String> channels = service.resolveChannels(accountId, eventType);
        return ResponseEntity.ok(new java.util.HashMap<String, Object>() {{
            put("accountId", accountId);
            put("eventType", eventType);
            put("channels", channels);
            put("source", "PREFERENCE");
        }});
    }
}
