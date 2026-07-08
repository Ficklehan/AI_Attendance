package com.attendance.dto.request;

import javax.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

public class RoleMembersUpdateRequest {

    @NotEmpty
    private List<String> userIds = new ArrayList<>();

    public List<String> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<String> userIds) {
        this.userIds = userIds != null ? userIds : new ArrayList<>();
    }
}
