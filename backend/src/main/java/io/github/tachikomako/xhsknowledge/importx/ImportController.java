package io.github.tachikomako.xhsknowledge.importx;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/xiaohongshu")
    public ImportResponse importXiaohongshu(@Valid @RequestBody XiaohongshuImportRequest request) {
        return importService.importItems(request);
    }
}
