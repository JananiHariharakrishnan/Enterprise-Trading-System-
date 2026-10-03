package com.tradingsystem.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class User {
    private UUID id;
    private Long accountId;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNo;
    private String rolesString;  // This will be set by MyBatis from the DB
    private OffsetDateTime createdOn;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNo() {
        return phoneNo;
    }

    public void setPhoneNo(String phoneNo) {
        this.phoneNo = phoneNo;
    }

    /**
     * Get roles as a List<String>.
     * Parses the rolesString which comes as PostgreSQL array format.
     * Format examples: "{CUSTOMER}" or "{CUSTOMER,ADMIN}"
     */
    public List<String> getRoles() {
        if (rolesString == null || rolesString.isEmpty()) {
            return Arrays.asList("CUSTOMER");
        }
        
        // Remove the PostgreSQL array braces and split by comma
        String cleaned = rolesString.replaceAll("[{}]", "").trim();
        if (cleaned.isEmpty()) {
            return Arrays.asList("CUSTOMER");
        }
        
        return Arrays.asList(cleaned.split(","));
    }

    public void setRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            this.rolesString = "{CUSTOMER}";
        } else {
            this.rolesString = "{" + String.join(",", roles) + "}";
        }
    }

    /**
     * Internal method for MyBatis to set the raw roles string from DB
     */
    public String getRolesString() {
        return rolesString;
    }

    public void setRolesString(String rolesString) {
        this.rolesString = rolesString;
    }

    public OffsetDateTime getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(OffsetDateTime createdOn) {
        this.createdOn = createdOn;
    }
}
