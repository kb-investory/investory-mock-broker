package com.investory.mockbroker.mapper;

import com.investory.mockbroker.domain.MockScenarioTemplate;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TemplateMapper {
    /** templateId가 이미 있으면 갱신, 없으면 새로 만든다 (관제 콘솔에서 같은 커스텀 템플릿을 다시 저장하는 경우 대비). */
    void upsert(MockScenarioTemplate template);
    List<MockScenarioTemplate> findAll();
    MockScenarioTemplate findById(@Param("templateId") String templateId);
    /** 영향받은 행 수를 반환한다 — 0이면 애초에 DB에 없던(=번들 파일이거나 존재하지 않는) templateId라는 뜻. */
    int delete(@Param("templateId") String templateId);
}
