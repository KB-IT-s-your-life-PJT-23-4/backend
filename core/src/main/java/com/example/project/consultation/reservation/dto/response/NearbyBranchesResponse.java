package com.example.project.consultation.reservation.dto.response;

import java.util.List;

public record NearbyBranchesResponse(
        List<OperatingBranchResponse> operatingBranches,
        List<NearbyBranchResponse> nearbyBranches
) {}