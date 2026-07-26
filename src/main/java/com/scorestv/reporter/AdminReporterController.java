package com.scorestv.reporter;

import com.scorestv.common.PageResponse;
import com.scorestv.reporter.ReporterDtos.AdminApplicationView;
import com.scorestv.reporter.ReporterDtos.ReviewRequest;
import com.scorestv.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel "Muhabir Başvuruları" — inceleme/onay/red. Onay, manuel ligi
 * oluşturup muhabiri atar. EDITOR + ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin/reporter")
@PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
public class AdminReporterController {

    private final ReporterService service;

    public AdminReporterController(ReporterService service) {
        this.service = service;
    }

    @GetMapping("/applications")
    public PageResponse<AdminApplicationView> applications(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.adminApplications(status, pageable);
    }

    /** Onayla → manuel lig + atama oluşur. */
    @PostMapping("/applications/{id}/approve")
    public AdminApplicationView approve(@PathVariable Long id,
                                        @Valid @RequestBody(required = false) ReviewRequest req,
                                        @AuthenticationPrincipal CurrentUser currentUser) {
        return service.approve(id, req, currentUser.id());
    }

    @PostMapping("/applications/{id}/reject")
    public AdminApplicationView reject(@PathVariable Long id,
                                       @Valid @RequestBody(required = false) ReviewRequest req,
                                       @AuthenticationPrincipal CurrentUser currentUser) {
        return service.reject(id, req, currentUser.id());
    }
}
