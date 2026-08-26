package com.bookdecision.infrastructure.userdataset;

import io.minio.messages.Filter;
import io.minio.messages.LifecycleConfiguration;
import io.minio.messages.Status;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MinioUserUploadObjectStoreTest {

    @Test
    void replacesOnlyItsOwnRetentionRuleAndPreservesUnrelatedRules() {
        LifecycleConfiguration.Rule unrelated = rule("unrelated-rule", "exports/", 90);
        LifecycleConfiguration.Rule staleProjectRule = rule(
                MinioUserUploadObjectStore.RETENTION_RULE_ID,
                MinioUserUploadObjectStore.PRIVATE_UPLOAD_PREFIX,
                7
        );

        LifecycleConfiguration merged = MinioUserUploadObjectStore.mergeLifecycle(
                new LifecycleConfiguration(List.of(unrelated, staleProjectRule)),
                30
        );

        assertThat(merged.rules()).hasSize(2);
        assertThat(merged.rules()).contains(unrelated);
        assertThat(merged.rules())
                .filteredOn(rule -> MinioUserUploadObjectStore.RETENTION_RULE_ID.equals(rule.id()))
                .singleElement()
                .satisfies(rule -> {
                    assertThat(rule.status()).isEqualTo(Status.ENABLED);
                    assertThat(rule.filter().prefix())
                            .isEqualTo(MinioUserUploadObjectStore.PRIVATE_UPLOAD_PREFIX);
                    assertThat(rule.expiration().days()).isEqualTo(30);
                });
    }

    private static LifecycleConfiguration.Rule rule(String id, String prefix, int days) {
        return new LifecycleConfiguration.Rule(
                Status.ENABLED,
                null,
                new LifecycleConfiguration.Expiration(
                        (ZonedDateTime) null,
                        days,
                        null,
                        null
                ),
                new Filter(prefix),
                id,
                null,
                null,
                null
        );
    }
}
