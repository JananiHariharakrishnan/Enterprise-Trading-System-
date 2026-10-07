package com.tradingsystem.dto.response;

import java.util.List;

/**
 * REST response for POST /api/v1/chat/message
 */
public class ChatMessageResponse {

    private String reply;
    private List<TradeSignalResponse> relatedSignals;

    public ChatMessageResponse() {}

    public ChatMessageResponse(String reply, List<TradeSignalResponse> relatedSignals) {
        this.reply = reply;
        this.relatedSignals = relatedSignals;
    }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public List<TradeSignalResponse> getRelatedSignals() { return relatedSignals; }
    public void setRelatedSignals(List<TradeSignalResponse> relatedSignals) { this.relatedSignals = relatedSignals; }
}
