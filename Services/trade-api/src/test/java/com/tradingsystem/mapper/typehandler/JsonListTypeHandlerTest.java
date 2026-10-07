package com.tradingsystem.mapper.typehandler;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonListTypeHandlerTest {

    private final JsonListTypeHandler handler = new JsonListTypeHandler();

    @Test
    void setNonNullParameter_writesJsonb() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 2, List.of("EMAIL", "IN_APP"), JdbcType.OTHER);

        org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(ps).setObject(eq(2), captor.capture());
        PGobject stored = (PGobject) captor.getValue();
        assertEquals("jsonb", stored.getType());
        assertEquals("[\"EMAIL\",\"IN_APP\"]", stored.getValue());
    }

    @Test
    void setNonNullParameter_emptyListAndSerializationFailure() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, List.of(), null);
        org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(ps).setObject(eq(1), captor.capture());
        assertEquals("[]", ((PGobject) captor.getValue()).getValue());

        List<String> exploding = new AbstractList<>() {
            @Override
            public String get(int index) {
                return "x";
            }

            @Override
            public int size() {
                return 1;
            }

            @Override
            public Iterator<String> iterator() {
                throw new IllegalStateException("cannot iterate");
            }
        };
        SQLException failure = assertThrows(SQLException.class,
                () -> handler.setNonNullParameter(ps, 3, exploding, JdbcType.OTHER));
        assertTrue(failure.getMessage().contains("Failed to convert"));
    }

    @Test
    void getNullableResult_readsPgObjectStringFallbackAndNull() throws Exception {
        ResultSet byName = mock(ResultSet.class);
        when(byName.getObject("channels")).thenReturn(pg("[\"EMAIL\"]"));
        assertEquals(List.of("EMAIL"), handler.getNullableResult(byName, "channels"));

        ResultSet byIndex = mock(ResultSet.class);
        when(byIndex.getObject(1)).thenReturn("[\"IN_APP\",\"SMS\"]");
        assertEquals(List.of("IN_APP", "SMS"), handler.getNullableResult(byIndex, 1));

        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getObject(4)).thenReturn(List.of("PUSH"));
        assertEquals(List.of("PUSH"), handler.getNullableResult(cs, 4));

        ResultSet nullColumn = mock(ResultSet.class);
        when(nullColumn.getObject("empty")).thenReturn(null);
        assertNull(handler.getNullableResult(nullColumn, "empty"));

        ResultSet jsonNull = mock(ResultSet.class);
        when(jsonNull.getObject(2)).thenReturn(pg("null"));
        assertNull(handler.getNullableResult(jsonNull, 2));

        CallableStatement nullCall = mock(CallableStatement.class);
        when(nullCall.getObject(1)).thenReturn(null);
        assertNull(handler.getNullableResult(nullCall, 1));
    }

    @Test
    void getNullableResult_invalidJson_throwsSqlException() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("bad")).thenReturn(pg("{"));
        SQLException fromPg = assertThrows(SQLException.class, () -> handler.getNullableResult(rs, "bad"));
        assertTrue(fromPg.getMessage().contains("Failed to parse"));

        ResultSet text = mock(ResultSet.class);
        when(text.getObject(1)).thenReturn("not-json");
        assertThrows(SQLException.class, () -> handler.getNullableResult(text, 1));

        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getObject(1)).thenReturn(Integer.valueOf(7));
        assertThrows(IllegalArgumentException.class, () -> handler.getNullableResult(cs, 1));
    }

    private static PGobject pg(String json) throws SQLException {
        PGobject object = new PGobject();
        object.setType("jsonb");
        object.setValue(json);
        return object;
    }
}
