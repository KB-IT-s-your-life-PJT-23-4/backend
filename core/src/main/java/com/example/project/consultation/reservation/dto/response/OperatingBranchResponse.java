package com.example.project.consultation.reservation.dto.response;

public record OperatingBranchResponse(
        Long branchId,
        String branchName,
        String roadAddress,
        String jibunAddress,
        String placeUrl,
        double latitude,
        double longitude,
        long distanceMeters
) {}