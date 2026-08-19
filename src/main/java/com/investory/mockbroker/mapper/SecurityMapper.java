package com.investory.mockbroker.mapper;

import com.investory.mockbroker.domain.Security;

import java.util.List;

/** securities는 메인 서비스가 소유·관리하는 테이블이라 이 앱에서는 읽기만 한다. */
public interface SecurityMapper {
    List<Security> findAllActive();
}
