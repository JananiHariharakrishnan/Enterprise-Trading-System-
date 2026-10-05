package com.tradingsystem.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelSerializationTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void user_serializesAndDeserializes() throws Exception {
        User u = new User();
        u.setAccountId(42L);
        u.setFirstName("Jane");

        String json = om.writeValueAsString(u);
        User round = om.readValue(json, User.class);
        assertEquals(u.getFirstName(), round.getFirstName());
    }

    @Test
    void orderResult_staticFactories_work() throws Exception {
        OrderResult s = OrderResult.success();
        OrderResult f = OrderResult.failure("E1");
        String js = om.writeValueAsString(s);
        // OrderResult has no default constructor; ensure serialization contains expected fields
        assert(js.contains("\"success\":true"));
        String jf = om.writeValueAsString(f);
        assert(jf.contains("E1"));
    }
}
