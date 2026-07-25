package io.github.tachikomako.xhsknowledge.source;

import io.github.tachikomako.xhsknowledge.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class XiaohongshuUrlNormalizer {

    private static final Pattern ITEM_PATH = Pattern.compile("/(?:explore|discovery/item)/([A-Za-z0-9_-]+)");
    private static final Pattern SAFE_ITEM_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    public NormalizedSource normalize(String rawUrl, String suppliedItemId) {
        try {
            URI input = new URI(rawUrl.trim());
            String host = input.getHost();
            if (host == null || !(host.equalsIgnoreCase("xiaohongshu.com")
                    || host.toLowerCase(Locale.ROOT).endsWith(".xiaohongshu.com"))) {
                throw invalidUrl("Only xiaohongshu.com URLs are accepted");
            }
            if (!"http".equalsIgnoreCase(input.getScheme()) && !"https".equalsIgnoreCase(input.getScheme())) {
                throw invalidUrl("URL must use HTTP or HTTPS");
            }

            String path = normalizePath(input.getPath());
            String itemId = normalizeItemId(suppliedItemId, path);
            URI canonical = new URI("https", null, host.toLowerCase(Locale.ROOT), -1, path, null, null);
            return new NormalizedSource(canonical.toString(), itemId);
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw invalidUrl("Invalid Xiaohongshu URL");
        }
    }

    private String normalizePath(String rawPath) {
        String path = rawPath == null || rawPath.isBlank() ? "/" : rawPath.replaceAll("/{2,}", "/");
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private String normalizeItemId(String suppliedItemId, String path) {
        if (suppliedItemId != null && !suppliedItemId.isBlank()) {
            String trimmed = suppliedItemId.trim();
            if (!SAFE_ITEM_ID.matcher(trimmed).matches()) {
                throw invalidUrl("Invalid source item ID");
            }
            return trimmed;
        }
        Matcher matcher = ITEM_PATH.matcher(path);
        return matcher.find() ? matcher.group(1) : null;
    }

    private ApiException invalidUrl(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SOURCE_URL", message);
    }

    public record NormalizedSource(String canonicalUrl, String sourceItemId) {
    }
}
