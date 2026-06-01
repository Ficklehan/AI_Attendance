package com.attendance.mapper;

import com.attendance.entity.RecognitionPrompt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RecognitionPromptMapper {

    RecognitionPrompt selectByCountry(@Param("countryCode") String countryCode);

    List<RecognitionPrompt> selectAll();

    long countAll();

    int upsertSystemSeed(RecognitionPrompt prompt);

    int upsertForceSeed(RecognitionPrompt prompt);

    int upsertUserEdit(RecognitionPrompt prompt);

    List<String> selectAllCountryCodes();
}
