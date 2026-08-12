package com.investory.mockbroker.mapper;

import com.investory.mockbroker.domain.MockClient;
import org.apache.ibatis.annotations.Param;

public interface ClientMapper {
    void insert(MockClient client);
    void update(MockClient client);
    MockClient findByClientId(@Param("clientId") String clientId);
}
