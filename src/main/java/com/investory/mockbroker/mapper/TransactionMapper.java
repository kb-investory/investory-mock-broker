package com.investory.mockbroker.mapper;

import com.investory.mockbroker.domain.MockTransaction;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TransactionMapper {
    void insert(MockTransaction transaction);

    /** 거래일시 내림차순. 마이데이터 금투-003 정렬 규칙을 따른다. */
    List<MockTransaction> findByPeriod(@Param("profileCode") String profileCode,
                                       @Param("accountNum") String accountNum,
                                       @Param("fromDate") String fromDate,
                                       @Param("toDate") String toDate,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    int countByPeriod(@Param("profileCode") String profileCode, @Param("accountNum") String accountNum,
                      @Param("fromDate") String fromDate, @Param("toDate") String toDate);

    List<MockTransaction> findRecent(@Param("profileCode") String profileCode, @Param("accountNum") String accountNum,
                                     @Param("limit") int limit);

    int countByAccountAndDate(@Param("profileCode") String profileCode, @Param("accountNum") String accountNum,
                              @Param("dateStr") String dateStr);

    void deleteByProfileCode(@Param("profileCode") String profileCode);
}
