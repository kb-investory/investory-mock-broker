package com.investory.mockbroker.mapper;

import com.investory.mockbroker.domain.MockPrice;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PriceMapper {
    void insert(MockPrice price);
    List<MockPrice> findAll(@Param("profileCode") String profileCode);
    MockPrice findByProdCode(@Param("profileCode") String profileCode, @Param("prodCode") String prodCode);
    void updateCurrentPrice(@Param("profileCode") String profileCode, @Param("prodCode") String prodCode,
                            @Param("currentPrice") BigDecimal currentPrice);
    void deleteByProfileCode(@Param("profileCode") String profileCode);
}
