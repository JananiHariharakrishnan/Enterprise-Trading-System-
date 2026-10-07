package com.tradingsystem.mapper.typehandler;
import org.postgresql.util.PGobject;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.io.IOException;
import java.sql.*;
import java.util.List;

@MappedTypes(List.class)
@MappedJdbcTypes(value = JdbcType.OTHER, includeNullJdbcType = true)
public class JsonListTypeHandler extends BaseTypeHandler<List<String>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
        try {
            PGobject pgo = new PGobject();
            pgo.setType("jsonb");
            pgo.setValue(MAPPER.writeValueAsString(parameter));
            ps.setObject(i, pgo);
        } catch (IOException e) {
            throw new SQLException("Failed to convert List<String> to JSON", e);
        }
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object obj = rs.getObject(columnName);
        return toList(obj);
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object obj = rs.getObject(columnIndex);
        return toList(obj);
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object obj = cs.getObject(columnIndex);
        return toList(obj);
    }

    @SuppressWarnings("unchecked")
    private List<String> toList(Object value) throws SQLException {
        if (value == null) {
            return null;
        }

        try {
            if (value instanceof PGobject) {
                String json = ((PGobject) value).getValue();
                return MAPPER.readValue(json, new TypeReference<List<String>>() {});
            }
            if (value instanceof String) {
                return MAPPER.readValue((String) value, new TypeReference<List<String>>() {});
            }
            // Fallback: let Jackson try to convert the object
            return MAPPER.convertValue(value, new TypeReference<List<String>>() {});
        } catch (IOException e) {
            throw new SQLException("Failed to parse JSON column to List<String>", e);
        }
    }
}
