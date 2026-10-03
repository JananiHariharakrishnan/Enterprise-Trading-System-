package com.tradingsystem.mapper.typehandler;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UuidTypeHandlerTest {

    @Test
    void toUuid_handlesNullAndUuidAndString() throws Exception {
        Method m = UuidTypeHandler.class.getDeclaredMethod("toUuid", Object.class);
        m.setAccessible(true);
        assertNull(m.invoke(null, new Object[]{null}));
    }

    @Test
    void toUuid_uuidInstance_returnsSame() throws Exception {
        UUID id = UUID.randomUUID();
        Method m = UuidTypeHandler.class.getDeclaredMethod("toUuid", Object.class);
        m.setAccessible(true);
        Object result = m.invoke(null, id);
        assertEquals(id, result);
    }

    @Test
    void toUuid_stringParses() throws Exception {
        UUID id = UUID.randomUUID();
        Method m = UuidTypeHandler.class.getDeclaredMethod("toUuid", Object.class);
        m.setAccessible(true);
        Object result = m.invoke(null, id.toString());
        assertEquals(id, result);
    }

    @Test
    void setNonNullParameter_usesSetObject() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        UuidTypeHandler handler = new UuidTypeHandler();
        UUID id = UUID.randomUUID();
        handler.setNonNullParameter(ps, 1, id, null);
        verify(ps).setObject(1, id);
    }

    @Test
    void getNullableResult_fromResultSet_byName() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        UUID id = UUID.randomUUID();
        when(rs.getObject("col")).thenReturn(id.toString());
        UuidTypeHandler handler = new UuidTypeHandler();
        assertEquals(id, handler.getNullableResult(rs, "col"));
    }

    @Test
    void getNullableResult_fromCallableStatement() throws Exception {
        CallableStatement cs = mock(CallableStatement.class);
        UUID id = UUID.randomUUID();
        when(cs.getObject(1)).thenReturn(id);
        UuidTypeHandler handler = new UuidTypeHandler();
        assertEquals(id, handler.getNullableResult(cs, 1));
    }
}
