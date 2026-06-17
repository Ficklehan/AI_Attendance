package com.attendance.service;

import com.attendance.config.PromptProperties;
import com.attendance.entity.FeishuCountryConfig;
import com.attendance.mapper.FeishuCountryConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeishuCountryConfigServiceTest {

    @Mock
    private FeishuCountryConfigMapper feishuCountryConfigMapper;

    @Mock
    private PromptProperties promptProperties;

    @InjectMocks
    private FeishuCountryConfigService service;

    @Test
    void getFieldMapping_prefersCountryRowOverDefault() {
        FeishuCountryConfig cn = new FeishuCountryConfig();
        cn.setCountryCode("CN");
        cn.setFieldMappingJson("[{\"aiField\":\"NO\",\"feishuField\":\"工号\",\"type\":\"string\",\"required\":true},"
                + "{\"aiField\":\"ARRIVEE_DATE\",\"feishuField\":\"ARRIVEE\",\"type\":\"date\",\"required\":true}]");
        when(feishuCountryConfigMapper.selectByCountry("CN")).thenReturn(cn);

        List<Map<String, Object>> mappings = service.getFieldMapping("CN");

        assertEquals(2, mappings.size());
        assertEquals("工号", mappings.get(0).get("feishuField"));
        assertEquals("ARRIVEE_DATE", mappings.get(1).get("aiField"));
        assertEquals("ARRIVEE", mappings.get(1).get("feishuField"));
        assertEquals("CN", service.resolveFieldMappingCountry("CN"));
    }

    @Test
    void getFieldMapping_fallsBackToDefaultWhenCountryMissing() {
        FeishuCountryConfig defaults = new FeishuCountryConfig();
        defaults.setCountryCode("default");
        defaults.setFieldMappingJson("[{\"aiField\":\"NO\",\"feishuField\":\"NO\",\"type\":\"string\",\"required\":true}]");
        when(feishuCountryConfigMapper.selectByCountry("CN")).thenReturn(null);
        when(feishuCountryConfigMapper.selectByCountry("default")).thenReturn(defaults);

        List<Map<String, Object>> mappings = service.getFieldMapping("CN");

        assertFalse(mappings.isEmpty());
        assertEquals("NO", mappings.get(0).get("feishuField"));
        assertEquals("default", service.resolveFieldMappingCountry("CN"));
    }
}
