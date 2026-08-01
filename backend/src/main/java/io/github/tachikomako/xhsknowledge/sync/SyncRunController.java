package io.github.tachikomako.xhsknowledge.sync;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync-runs")
public class SyncRunController {

    private final SyncRunService syncRunService;

    public SyncRunController(SyncRunService syncRunService) {
        this.syncRunService = syncRunService;
    }

    @GetMapping("/latest")
    public SyncRunView latest() {
        return syncRunService.latest();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SyncRunView create(@Valid @RequestBody CreateSyncRunRequest request) {
        return syncRunService.create(request);
    }

    @PatchMapping("/{id}")
    public SyncRunView update(@PathVariable String id, @Valid @RequestBody UpdateSyncRunRequest request) {
        return syncRunService.update(id, request);
    }
}
