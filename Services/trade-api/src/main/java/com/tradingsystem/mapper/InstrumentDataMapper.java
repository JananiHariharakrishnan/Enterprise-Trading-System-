package com.tradingsystem.mapper;

import com.tradingsystem.model.Instrument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InstrumentDataMapper {

    List<Instrument> findTradableInstruments(
            @Param("query") String query,
            @Param("limit") int limit
    );
}
