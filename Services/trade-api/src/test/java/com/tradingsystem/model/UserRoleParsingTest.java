package com.tradingsystem.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserRoleParsingTest {

    @Test
    void parsesQuotedAndPlainPostgresArrayText() {
        User user = new User();
        user.setRolesString("{\"ADMIN\"}");
        assertEquals(List.of("ADMIN"), user.getRoles());

        user.setRolesString("{CUSTOMER,ADMIN}");
        assertEquals(List.of("CUSTOMER", "ADMIN"), user.getRoles());

        AdminUserRecord record = new AdminUserRecord();
        record.setRolesString("{\"CUSTOMER\",\"ADMIN\"}");
        assertEquals(List.of("CUSTOMER", "ADMIN"), record.getRoles());
    }
}
