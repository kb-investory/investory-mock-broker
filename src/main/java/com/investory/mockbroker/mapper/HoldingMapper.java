package com.investory.mockbroker.mapper;

import com.investory.mockbroker.domain.MockHolding;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface HoldingMapper {
    List<MockHolding> findByAccountNum(@Param("profileCode") String profileCode, @Param("accountNum") String accountNum);
    MockHolding findOne(@Param("profileCode") String profileCode, @Param("accountNum") String accountNum,
                        @Param("prodCode") String prodCode);
    void insert(MockHolding holding);
    void update(MockHolding holding);
    void delete(@Param("profileCode") String profileCode, @Param("accountNum") String accountNum,
                @Param("prodCode") String prodCode);
    void deleteByProfileCode(@Param("profileCode") String profileCode);
}
