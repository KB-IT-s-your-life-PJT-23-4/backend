package com.example.project.admin.auth.dto.response;

import com.example.project.common.api.Pagination;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class AdminAuthPageResponse {
    private List<AdminAuthResponse> admins;
    private Pagination pagination;
}
