package com.attendance.service;

import com.attendance.dto.NightShiftConfigDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NightShiftConfigServiceTest {

    @Mock
    private com.attendance.mapper.PluginConfigMapper pluginConfigMapper;

    @InjectMocks
    private NightShiftConfigService nightShiftConfigService;

    @BeforeEach
    void setUp() {
        when(pluginConfigMapper.selectValue(NightShiftConfigService.CONFIG_KEY)).thenReturn(null);
    }

    @Test
    void getConfigForCountry_returnsGlobalWhenNoOverride() {
        NightShiftConfigDTO config = nightShiftConfigService.getConfigForCountry("FR");
        assertEquals("20:00", config.getStartTime());
        assertEquals("06:00", config.getEndTime());
    }

    @Test
    void getAdminConfig_returnsByCountryOverrides() {
        when(pluginConfigMapper.selectValue(NightShiftConfigService.CONFIG_KEY))
                .thenReturn("{\"startTime\":\"20:00\",\"endTime\":\"06:00\",\"crossMidnight\":true,"
                        + "\"useScheduleColumn\":true,\"byCountry\":{\"CN\":{\"startTime\":\"21:00\","
                        + "\"endTime\":\"05:00\",\"crossMidnight\":true,\"useScheduleColumn\":false}}}");

        NightShiftConfigDTO admin = nightShiftConfigService.getAdminConfig();
        assertEquals(1, admin.getByCountry().size());
        assertEquals("21:00", admin.getByCountry().get("CN").getStartTime());
        assertEquals("05:00", admin.getByCountry().get("CN").getEndTime());
        assertEquals(false, admin.getByCountry().get("CN").isUseScheduleColumn());
    }

    @Test
    void saveAndLoadCountryOverride() {
        NightShiftConfigDTO incoming = NightShiftConfigDTO.defaults();
        incoming.getByCountry().put("FR", frRules("22:00", "05:00"));
        nightShiftConfigService.saveConfig(incoming);

        verify(pluginConfigMapper).upsertValue(eq(NightShiftConfigService.CONFIG_KEY), anyString(), eq("json"), anyString());

        when(pluginConfigMapper.selectValue(NightShiftConfigService.CONFIG_KEY))
                .thenReturn("{\"startTime\":\"20:00\",\"endTime\":\"06:00\",\"crossMidnight\":true,"
                        + "\"useScheduleColumn\":true,\"byCountry\":{\"FR\":{\"startTime\":\"22:00\","
                        + "\"endTime\":\"05:00\",\"crossMidnight\":true,\"useScheduleColumn\":true}}}");

        NightShiftConfigDTO fr = nightShiftConfigService.getConfigForCountry("FR");
        assertEquals("22:00", fr.getStartTime());
        assertEquals("05:00", fr.getEndTime());

        NightShiftConfigDTO de = nightShiftConfigService.getConfigForCountry("DE");
        assertEquals("20:00", de.getStartTime());
    }

    private static NightShiftConfigDTO frRules(String start, String end) {
        NightShiftConfigDTO dto = new NightShiftConfigDTO();
        dto.setStartTime(start);
        dto.setEndTime(end);
        dto.setCrossMidnight(true);
        dto.setUseScheduleColumn(true);
        return dto;
    }
}
