package io.github.tachikomako.xhsknowledge.media;

import io.github.tachikomako.xhsknowledge.common.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@RestController
public class MediaProxyController {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @GetMapping("/api/v1/media/proxy")
    public ResponseEntity<byte[]> proxy(@RequestParam String url) {
        URI uri = parseImageUri(url);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://www.xiaohongshu.com/")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "MEDIA_FETCH_FAILED", "图片加载失败");
            }
            String contentType = response.headers().firstValue("content-type").orElse("image/jpeg");
            if (!contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "MEDIA_NOT_IMAGE", "远程资源不是图片");
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS))
                    .body(response.body());
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MEDIA_FETCH_FAILED", "图片加载失败");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MEDIA_FETCH_INTERRUPTED", "图片加载中断");
        }
    }

    private URI parseImageUri(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_URL", "图片地址无效");
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || !isAllowedHost(host)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_MEDIA_HOST", "只允许代理小红书图片");
        }
        return uri;
    }

    private boolean isAllowedHost(String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".xhscdn.com") || normalized.endsWith(".xiaohongshu.com");
    }
}
