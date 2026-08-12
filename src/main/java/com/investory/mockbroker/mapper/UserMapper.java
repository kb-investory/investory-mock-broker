package com.investory.mockbroker.mapper;

import com.investory.mockbroker.domain.MockUser;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {
    void insert(MockUser user);
    MockUser findByLoginId(@Param("loginId") String loginId);
    MockUser findByProfileCode(@Param("profileCode") String profileCode);
    void applyScenario(@Param("profileCode") String profileCode, @Param("orgCode") String orgCode,
                       @Param("orgName") String orgName, @Param("scenarioJson") String scenarioJson);
}
