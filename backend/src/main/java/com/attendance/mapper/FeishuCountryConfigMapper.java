package com.attendance.mapper;

import com.attendance.entity.FeishuCountryConfig;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FeishuCountryConfigMapper {

    FeishuCountryConfig selectByCountry(@Param("countryCode") String countryCode);

    List<String> selectAllCountryCodes();

    long countAll();

    int upsertSystemSeed(FeishuCountryConfig row);

    int upsertForceSeed(FeishuCountryConfig row);

    int upsertUserEdit(FeishuCountryConfig row);
}
