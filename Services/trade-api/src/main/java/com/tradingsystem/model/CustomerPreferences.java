package com.tradingsystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.OffsetDateTime;
import java.util.List;

public class CustomerPreferences {

    private Long accountId;
    private Long defaultAccountId;

    private String tradingDefaultOrderType;
    private boolean tradingSkipOrderConfirmation;
    private Integer tradingDefaultTradeQuantity;

    private List<String> notificationsTradeFills;
    private List<String> notificationsPriceAlerts;
    private boolean emailNotificationsEnabled;

    private String displayTheme;
    private String displayBaseCurrency;

    private OffsetDateTime updatedAt;

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public Long getDefaultAccountId() { return defaultAccountId; }
    public void setDefaultAccountId(Long defaultAccountId) { this.defaultAccountId = defaultAccountId; }

    @JsonIgnore
    public String getTradingDefaultOrderType() { return tradingDefaultOrderType; }
    @JsonIgnore
    public void setTradingDefaultOrderType(String tradingDefaultOrderType) { this.tradingDefaultOrderType = tradingDefaultOrderType; }

    @JsonIgnore
    public boolean isTradingSkipOrderConfirmation() { return tradingSkipOrderConfirmation; }
    @JsonIgnore
    public void setTradingSkipOrderConfirmation(boolean tradingSkipOrderConfirmation) { this.tradingSkipOrderConfirmation = tradingSkipOrderConfirmation; }

    @JsonIgnore
    public Integer getTradingDefaultTradeQuantity() { return tradingDefaultTradeQuantity; }
    @JsonIgnore
    public void setTradingDefaultTradeQuantity(Integer tradingDefaultTradeQuantity) { this.tradingDefaultTradeQuantity = tradingDefaultTradeQuantity; }

    @JsonIgnore
    public List<String> getNotificationsTradeFills() { return notificationsTradeFills; }
    @JsonIgnore
    public void setNotificationsTradeFills(List<String> notificationsTradeFills) { this.notificationsTradeFills = notificationsTradeFills; }

    @JsonIgnore
    public List<String> getNotificationsPriceAlerts() { return notificationsPriceAlerts; }
    @JsonIgnore
    public void setNotificationsPriceAlerts(List<String> notificationsPriceAlerts) { this.notificationsPriceAlerts = notificationsPriceAlerts; }

    @JsonIgnore
    public boolean isEmailNotificationsEnabled() { return emailNotificationsEnabled; }
    @JsonIgnore
    public void setEmailNotificationsEnabled(boolean emailNotificationsEnabled) { this.emailNotificationsEnabled = emailNotificationsEnabled; }

    @JsonIgnore
    public String getDisplayTheme() { return displayTheme; }
    @JsonIgnore
    public void setDisplayTheme(String displayTheme) { this.displayTheme = displayTheme; }

    @JsonIgnore
    public String getDisplayBaseCurrency() { return displayBaseCurrency; }
    @JsonIgnore
    public void setDisplayBaseCurrency(String displayBaseCurrency) { this.displayBaseCurrency = displayBaseCurrency; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public TradingPreferences getTrading() {
        TradingPreferences trading = new TradingPreferences();
        trading.setDefaultOrderType(tradingDefaultOrderType);
        trading.setSkipOrderConfirmation(tradingSkipOrderConfirmation);
        trading.setDefaultTradeQuantity(tradingDefaultTradeQuantity);
        return trading;
    }

    public void setTrading(TradingPreferences trading) {
        if (trading == null) {
            return;
        }
        this.tradingDefaultOrderType = trading.getDefaultOrderType();
        this.tradingSkipOrderConfirmation = trading.isSkipOrderConfirmation();
        this.tradingDefaultTradeQuantity = trading.getDefaultTradeQuantity();
    }

    public NotificationPreferences getNotifications() {
        NotificationPreferences notifications = new NotificationPreferences();
        notifications.setTradeFills(notificationsTradeFills);
        notifications.setPriceAlerts(notificationsPriceAlerts);
        notifications.setEmailEnabled(emailNotificationsEnabled);
        return notifications;
    }

    public void setNotifications(NotificationPreferences notifications) {
        if (notifications == null) {
            return;
        }
        this.notificationsTradeFills = notifications.getTradeFills();
        this.notificationsPriceAlerts = notifications.getPriceAlerts();
        this.emailNotificationsEnabled = notifications.isEmailEnabled();
    }

    public DisplayPreferences getDisplay() {
        DisplayPreferences display = new DisplayPreferences();
        display.setTheme(displayTheme);
        display.setBaseCurrency(displayBaseCurrency);
        return display;
    }

    public void setDisplay(DisplayPreferences display) {
        if (display == null) {
            return;
        }
        this.displayTheme = display.getTheme();
        this.displayBaseCurrency = display.getBaseCurrency();
    }

    public static class TradingPreferences {
        private String defaultOrderType;
        private boolean skipOrderConfirmation;
        private Integer defaultTradeQuantity;

        public String getDefaultOrderType() { return defaultOrderType; }
        public void setDefaultOrderType(String defaultOrderType) { this.defaultOrderType = defaultOrderType; }

        public boolean isSkipOrderConfirmation() { return skipOrderConfirmation; }
        public void setSkipOrderConfirmation(boolean skipOrderConfirmation) { this.skipOrderConfirmation = skipOrderConfirmation; }

        public Integer getDefaultTradeQuantity() { return defaultTradeQuantity; }
        public void setDefaultTradeQuantity(Integer defaultTradeQuantity) { this.defaultTradeQuantity = defaultTradeQuantity; }
    }

    public static class NotificationPreferences {
        private List<String> tradeFills;
        private List<String> priceAlerts;
        private boolean emailEnabled;

        public List<String> getTradeFills() { return tradeFills; }
        public void setTradeFills(List<String> tradeFills) { this.tradeFills = tradeFills; }

        public List<String> getPriceAlerts() { return priceAlerts; }
        public void setPriceAlerts(List<String> priceAlerts) { this.priceAlerts = priceAlerts; }

        public boolean isEmailEnabled() { return emailEnabled; }
        public void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }
    }

    public static class DisplayPreferences {
        private String theme;
        private String baseCurrency;

        public String getTheme() { return theme; }
        public void setTheme(String theme) { this.theme = theme; }

        public String getBaseCurrency() { return baseCurrency; }
        public void setBaseCurrency(String baseCurrency) { this.baseCurrency = baseCurrency; }
    }
}
