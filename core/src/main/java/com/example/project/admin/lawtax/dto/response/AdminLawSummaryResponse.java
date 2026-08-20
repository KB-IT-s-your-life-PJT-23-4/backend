package com.example.project.admin.lawtax.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
@ApiModel(description = "법령 필터용 요약 정보")
public class AdminLawSummaryResponse {

    @ApiModelProperty(value = "법령ID", example = "001561")
    private final String lawCode;

    @ApiModelProperty(value = "법령명", example = "상속세및증여세법")
    private final String lawName;

    @ApiModelProperty(value = "법종구분", example = "법률")
    private final String lawType;
}
