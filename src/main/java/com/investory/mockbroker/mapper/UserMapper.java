package com.investory.mockbroker.mapper;

import com.investory.mockbroker.domain.MockUser;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {
    void insert(MockUser user);
    MockUser findByLoginId(@Param("loginId") String loginId);
    MockUser findByProfileCode(@Param("profileCode") String profileCode);
    void applyScenario(@Param("profileCode") String profileCode, @Param("orgCode") String orgCode,
                       @Param("orgName") String orgName, @Param("scenarioJson") String scenarioJson);
    /** 관제 콘솔의 유저 관리 페이지용 — 전체 유저 목록. */
    List<MockUser> findAll();
    void delete(@Param("profileCode") String profileCode);
}
