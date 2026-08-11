package com.example.project.consultation.reservation.service;

import com.example.project.consultation.reservation.dto.response.KakaoKeywordSearchResponse;
import com.example.project.consultation.reservation.domain.BranchVO;
import com.example.project.consultation.reservation.dto.response.NearbyBranchResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BranchMatchService {

    public List<NearbyBranchResponse> merge(
            KakaoKeywordSearchResponse kakaoResponse,
            List<BranchVO> branches
    ) {
        return kakaoResponse.documents().stream()
                .map(doc -> {
                    Long branchId = findMatchingBranchId(doc.placeName(), branches);
                    return new NearbyBranchResponse(
                            doc.placeName(),
                            doc.addressName(),
                            doc.roadAddressName(),
                            doc.phone(),
                            Double.parseDouble(doc.x()),
                            Double.parseDouble(doc.y()),
                            doc.placeUrl(),
                            branchId,
                            branchId != null
                    );
                })
                .toList();
    }

    private Long findMatchingBranchId(String placeName, List<BranchVO> branches) {
        String normalizedPlace = normalize(placeName);

        return branches.stream()
                .filter(branch -> normalizedPlace.contains(normalize(branch.getBranchName())))
                .map(BranchVO::getBranchId)
                .findFirst()
                .orElse(null);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }
}