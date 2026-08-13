package com.investory.mockbroker.mapper;

import com.investory.mockbroker.domain.MockConnection;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ConnectionMapper {
    void insert(MockConnection connection);
    MockConnection findByConnectionId(@Param("connectionId") String connectionId);
    MockConnection findByClientIdAndProfileCode(@Param("clientId") String clientId,
                                                 @Param("profileCode") String profileCode);
    List<MockConnection> findAll();
    void deleteByProfileCode(@Param("profileCode") String profileCode);
}
