package com.attendance.mapper;

import com.attendance.entity.PluginConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConfigMapper {

    PluginConfig selectConfigByKey(@Param("configKey") String configKey);

    List<PluginConfig> selectAllConfigs();

    int insertConfig(PluginConfig config);

    int updateConfigByKey(@Param("configKey") String configKey, @Param("configValue") String configValue);

    int deleteConfigByKey(@Param("configKey") String configKey);

    int existsByKey(@Param("configKey") String configKey);
}
