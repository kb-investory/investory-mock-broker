package com.investory.mockbroker.mapper;

import com.investory.mockbroker.domain.MockAccount;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface AccountMapper {
    void insert(MockAccount account);
    List<MockAccount> findByOrgCode(@Param("profileCode") String profileCode, @Param("orgCode") String orgCode);
    MockAccount findByAccountNum(@Param("profileCode") String profileCode, @Param("accountNum") String accountNum);
    void updateCashBalance(@Param("profileCode") String profileCode, @Param("accountNum") String accountNum,
                           @Param("cashBalance") BigDecimal cashBalance);
    void deleteByProfileCode(@Param("profileCode") String profileCode);
}
