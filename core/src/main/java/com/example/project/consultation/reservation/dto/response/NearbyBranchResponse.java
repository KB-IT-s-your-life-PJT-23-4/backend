package com.example.project.consultation.reservation.dto.response;

public record NearbyBranchResponse(
        String placeName,
        String addressName,
        String roadAddressName,
        String phone,
        double x,
        double y,
        String placeUrl,
        Long branchId,
        boolean ticketAvailable
) {}