package com.example.project.reminder.controller;

import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.example.project.common.logging.ApiLog;
import com.example.project.common.web.CurrentUser;
import com.example.project.reminder.dto.request.ReminderReadRequest;
import com.example.project.reminder.dto.response.ReminderResponse;
import com.example.project.reminder.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@ApiLog
@Log4j2
@RestController
@RequestMapping("/api/rm")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    /**
     * 알림함 목록. 증여(`/api/gm`)·수증자(`/api/fm`)와 달리 별도 네임스페이스를 쓴다.
     * 리마인더는 두 리소스를 가로질러 한 목록으로 합치는 화면이라 어느 한쪽 밑에 넣을 자리가 없다.
     *
     * <p>노출 시점은 마일스톤(기한 D-90/30/7/당일)으로 서버가 정한다.
     * 클라이언트가 창을 넓히는 파라미터를 두면 화면마다 알림이 달라져 기준이 흐려진다.
     */
    @GetMapping
    public ApiResponse<List<ReminderResponse>> getReminders(
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        List<ReminderResponse> data = reminderService.selectReminders(CurrentUser.id(principal));

        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), data);
    }

    /**
     * 읽음 처리. 리마인더는 저장하지 않아 고유 id 가 없으므로,
     * 목록이 내려준 (giftId, type) 한 쌍으로 대상을 지목한다.
     *
     * <p>같은 알림을 여러 번 눌러도 행은 하나이고 읽은 시각만 갱신된다.
     */
    @PostMapping("/read")
    public ApiResponse<Void> markRead(
            @RequestBody ReminderReadRequest reminderReadRequest,
            @AuthenticationPrincipal String principal,
            HttpServletRequest request
    ) {
        reminderService.markRead(CurrentUser.id(principal), reminderReadRequest);

        return ApiResponse.success(ResponseCode.SUCCESS, request.getRequestURI(), null);
    }
}
