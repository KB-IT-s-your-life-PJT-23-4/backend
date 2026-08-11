package com.example.project.consultation.reservation.service;

import com.example.project.consultation.reservation.client.KakaoLocalClient;
import com.example.project.consultation.reservation.dto.response.KakaoKeywordSearchResponse;
import com.example.project.consultation.reservation.domain.BranchVO;
import com.example.project.consultation.reservation.dto.response.NearbyBranchResponse;
import com.example.project.consultation.reservation.dto.response.OperatingBranchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BranchMatchService {

    private static final double EARTH_RADIUS_METERS = 6_371_000;
    private static final int DETAIL_SEARCH_RADIUS = 1000;

    private final KakaoLocalClient kakaoLocalClient;

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

    // 실거리 기준 가장 가까운 운영 지점 N곳을 찾고, 각각 카카오 상세 정보(주소/전화/링크)를 보강한다.
    public Mono<List<OperatingBranchResponse>> findNearestOperatingWithDetail(
            double userLng, double userLat, List<BranchVO> branches, int limit
    ) {
        List<BranchVO> nearest = branches.stream()
                .filter(branch -> branch.getLatitude() != null && branch.getLongitude() != null)
                .sorted(Comparator.comparingDouble(branch -> haversineMeters(
                        userLat, userLng,
                        branch.getLatitude().doubleValue(), branch.getLongitude().doubleValue()
                )))
                .limit(limit)
                .toList();

        List<Mono<OperatingBranchResponse>> enrichedMonoList = nearest.stream()
                .map(branch -> {
                    long distanceMeters = Math.round(haversineMeters(
                            userLat, userLng,
                            branch.getLatitude().doubleValue(), branch.getLongitude().doubleValue()
                    ));
                    return enrichOperatingBranch(branch, distanceMeters);
                })
                .toList();

        return Flux.merge(enrichedMonoList).collectList();
    }

    private Mono<OperatingBranchResponse> enrichOperatingBranch(BranchVO branch, long distanceMeters) {
        double branchX = branch.getLongitude().doubleValue();
        double branchY = branch.getLatitude().doubleValue();

        return kakaoLocalClient.searchKeyword("KB국민은행 " + branch.getBranchName(), branchX, branchY, DETAIL_SEARCH_RADIUS)
                .map(response -> response.documents().isEmpty() ? null : response.documents().get(0))
                .map(doc -> toOperatingBranchResponse(branch, distanceMeters, doc))
                .onErrorResume(e -> Mono.just(toOperatingBranchResponse(branch, distanceMeters, null)));
    }

    private OperatingBranchResponse toOperatingBranchResponse(
            BranchVO branch, long distanceMeters, KakaoKeywordSearchResponse.Document doc
    ) {
        return new OperatingBranchResponse(
                branch.getBranchId(),
                branch.getBranchName(),
                doc != null ? doc.roadAddressName() : branch.getAddress(),
                doc != null ? doc.addressName() : null,
                doc != null ? doc.placeUrl() : null,
                branch.getLatitude().doubleValue(),
                branch.getLongitude().doubleValue(),
                distanceMeters
        );
    }

    public List<NearbyBranchResponse> excludeAlreadyShown(List<NearbyBranchResponse> nearbyBranches, List<OperatingBranchResponse> operatingBranches) {
        Set<Long> shownBranchIds = operatingBranches.stream()
                .map(OperatingBranchResponse::branchId)
                .collect(Collectors.toSet());

        return nearbyBranches.stream()
                .filter(branch -> branch.branchId() == null || !shownBranchIds.contains(branch.branchId()))
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

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }
}