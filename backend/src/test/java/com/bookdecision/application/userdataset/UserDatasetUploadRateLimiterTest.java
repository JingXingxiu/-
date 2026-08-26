package com.bookdecision.application.userdataset;

import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserDatasetUploadRateLimiterTest {

    @Test
    void isolatesRemoteAddressesAndResetsAtTheNextFixedWindow() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(10_000L, 10_001L, 10_002L, 10_003L, 60_000L);
        UserDatasetUploadRateLimiter limiter = new UserDatasetUploadRateLimiter(properties(2, 60), clock);

        limiter.acquire("192.0.2.1");
        limiter.acquire("192.0.2.1");

        assertThatThrownBy(() -> limiter.acquire("192.0.2.1"))
                .isInstanceOfSatisfying(UserDatasetException.class, exception -> {
                    assertThat(exception.errorCode())
                            .isEqualTo(UserDatasetErrorCode.UPLOAD_RATE_LIMIT_EXCEEDED);
                    assertThat(exception.context()).containsEntry("retryAfterSeconds", 50L);
                });
        assertThatCode(() -> limiter.acquire("192.0.2.2")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.acquire("192.0.2.1")).doesNotThrowAnyException();
    }

    @Test
    void rejectsANewRemoteAddressWhenTheTrackedClientMapIsFull() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(10_000L);
        UserDatasetUploadRateLimiter limiter = new UserDatasetUploadRateLimiter(
                properties(10, 60), clock, 2
        );

        limiter.acquire("192.0.2.1");
        limiter.acquire("192.0.2.2");

        assertThatThrownBy(() -> limiter.acquire("192.0.2.3"))
                .isInstanceOfSatisfying(UserDatasetException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(UserDatasetErrorCode.UPLOAD_RATE_LIMIT_EXCEEDED));
        assertThatCode(() -> limiter.acquire("192.0.2.1")).doesNotThrowAnyException();
    }

    private static UserDatasetProperties properties(int maxUploads, long windowSeconds) {
        return new UserDatasetProperties(
                true,
                30,
                3_600_000,
                1_048_576,
                100,
                500,
                new UserDatasetProperties.UploadRateLimit(maxUploads, windowSeconds),
                new UserDatasetProperties.StorageQuota(100, 52_428_800),
                new UserDatasetProperties.Minio("http://localhost:9000", "test", "test", "test")
        );
    }
}
