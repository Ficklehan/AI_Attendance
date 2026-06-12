package com.attendance.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PluginConfigMapper {

    String selectValue(@Param("configKey") String configKey);

    int upsertValue(@Param("configKey") String configKey,
                    @Param("configValue") String configValue,
                    @Param("configType") String configType,
                    @Param("description") String description);
}
