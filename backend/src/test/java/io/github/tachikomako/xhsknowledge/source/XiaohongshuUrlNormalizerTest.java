package io.github.tachikomako.xhsknowledge.source;

import io.github.tachikomako.xhsknowledge.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XiaohongshuUrlNormalizerTest {

    private final XiaohongshuUrlNormalizer normalizer = new XiaohongshuUrlNormalizer();

    @Test
    void extractsItemIdAndRemovesTrackingQuery() {
        XiaohongshuUrlNormalizer.NormalizedSource result = normalizer.normalize(
                "https://www.xiaohongshu.com/explore/abc123?xsec_token=secret#comments",
                null
        );

        assertThat(result.sourceItemId()).isEqualTo("abc123");
        assertThat(result.canonicalUrl()).isEqualTo("https://www.xiaohongshu.com/explore/abc123");
    }

    @Test
    void rejectsNonXiaohongshuHost() {
        assertThatThrownBy(() -> normalizer.normalize("https://example.com/explore/abc123", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("xiaohongshu.com");
    }
}
