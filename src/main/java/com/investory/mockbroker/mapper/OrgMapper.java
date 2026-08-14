package com.investory.mockbroker.mapper;

import com.investory.mockbroker.domain.MockOrg;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OrgMapper {
    List<MockOrg> findAll();
    MockOrg findByOrgCode(@Param("orgCode") String orgCode);
    void insert(MockOrg org);
}
