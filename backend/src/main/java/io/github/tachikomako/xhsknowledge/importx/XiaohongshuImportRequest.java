package io.github.tachikomako.xhsknowledge.importx;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public record XiaohongshuImportRequest(
        @NotBlank @Size(max = 128) String clientBatchId,
        @NotBlank @Pattern(regexp = "FAVORITES_PAGE|CURRENT_POST") String captureMode,
        @NotBlank @Size(max = 64) String extractorVersion,
        @NotEmpty @Size(max = 50) List<@Valid IncomingItem> items
) {
    public record IncomingItem(
            @Size(max = 128) String sourceItemId,
            @NotBlank @Size(max = 2048) String url,
            @NotBlank @Size(max = 500) String title,
            @Size(max = 200) String author,
            @Size(max = 100_000) String text,
            @Size(max = 2048) String coverUrl,
            @Size(max = 20) List<@Size(max = 2048) String> imageUrls,
            @Pattern(regexp = "FAVORITE|LIKED") String sourceRelation,
            @NotBlank @Pattern(regexp = "CARD|DETAIL") String captureLevel,
            OffsetDateTime capturedAt
    ) {
        public IncomingItem {
            imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        }
    }
}
