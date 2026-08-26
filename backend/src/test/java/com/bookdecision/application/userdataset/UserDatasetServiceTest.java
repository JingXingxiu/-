package com.bookdecision.application.userdataset;

import com.bookdecision.application.dataset.DatasetProvider;
import com.bookdecision.application.dataset.DatasetSnapshot;
import com.bookdecision.domain.PlatformRule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDatasetServiceTest {

    @Test
    void storageQuotaFailureCompensatesTheAlreadyStoredMinioObject() {
        DatasetProvider provider = mock(DatasetProvider.class);
        DatasetSnapshot base = mock(DatasetSnapshot.class);
        PlatformRule platform = mock(PlatformRule.class);
        when(platform.id()).thenReturn("platform-a");
        when(base.version()).thenReturn("mixed-demo-v1");
        when(base.platforms()).thenReturn(List.of(platform));
        when(provider.findByVersion("mixed-demo-v1")).thenReturn(java.util.Optional.of(base));
        UserDatasetCsvParser parser = mock(UserDatasetCsvParser.class);
        ParsedUserDataset parsed = new ParsedUserDataset(
                "user-offer-v1",
                List.of(new UserDatasetBook("9787020002207", "红楼梦", 1)),
                List.of(),
                1
        );
        UserDatasetProperties properties = properties();
        byte[] bytes = "valid-placeholder".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(parser.parse(eq(bytes), anySet(), eq(properties))).thenReturn(parsed);
        UserDatasetRepository repository = mock(UserDatasetRepository.class);
        doThrow(new UserDatasetException(UserDatasetErrorCode.STORAGE_QUOTA_EXCEEDED))
                .when(repository).save(any(UserDatasetUpload.class), eq(parsed));
        UserUploadObjectStore objectStore = mock(UserUploadObjectStore.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC);
        UserDatasetService service = new UserDatasetService(
                provider,
                parser,
                repository,
                objectStore,
                properties,
                new UserDatasetUploadRateLimiter(properties, clock),
                clock
        );

        assertThatThrownBy(() -> service.upload(
                "192.0.2.10", "mixed-demo-v1", "offers.csv", bytes, false
        )).isInstanceOfSatisfying(UserDatasetException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(UserDatasetErrorCode.STORAGE_QUOTA_EXCEEDED));

        ArgumentCaptor<String> storedKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> deletedKey = ArgumentCaptor.forClass(String.class);
        verify(objectStore).put(storedKey.capture(), eq(bytes));
        verify(objectStore).delete(deletedKey.capture());
        assertThat(deletedKey.getValue()).isEqualTo(storedKey.getValue());
    }

    @Test
    void oneObjectStoreFailureDoesNotBlockLaterExpiredUploads() {
        DatasetProvider provider = mock(DatasetProvider.class);
        UserDatasetRepository repository = mock(UserDatasetRepository.class);
        UserUploadObjectStore objectStore = mock(UserUploadObjectStore.class);
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        UserDatasetUpload first = expiredUpload(UUID.randomUUID(), "private-uploads/first/source.csv", now);
        UserDatasetUpload second = expiredUpload(UUID.randomUUID(), "private-uploads/second/source.csv", now);
        when(repository.findExpired(now, 100)).thenReturn(List.of(first, second));
        doThrow(new UserDatasetException(UserDatasetErrorCode.STORAGE_UNAVAILABLE))
                .when(objectStore).delete(first.objectKey());
        when(repository.deleteById(second.id())).thenReturn(true);

        UserDatasetService service = new UserDatasetService(
                provider,
                new UserDatasetCsvParser(),
                repository,
                objectStore,
                properties(),
                new UserDatasetUploadRateLimiter(properties(), Clock.fixed(now, ZoneOffset.UTC)),
                Clock.fixed(now, ZoneOffset.UTC)
        );

        assertThat(service.cleanupExpired(100)).isEqualTo(1);
        verify(repository, never()).deleteById(first.id());
        verify(objectStore).delete(second.objectKey());
        verify(repository).deleteById(second.id());
    }

    private static UserDatasetUpload expiredUpload(UUID id, String key, Instant now) {
        return new UserDatasetUpload(
                id,
                "mixed-demo-v1",
                "0".repeat(64),
                "source.csv",
                key,
                "1".repeat(64),
                100,
                "user-offer-v1",
                5,
                1,
                true,
                now.minusSeconds(100),
                now.minusSeconds(1)
        );
    }

    private static UserDatasetProperties properties() {
        return new UserDatasetProperties(
                true,
                30,
                3_600_000,
                1_048_576,
                100,
                500,
                new UserDatasetProperties.UploadRateLimit(10, 60),
                new UserDatasetProperties.StorageQuota(100, 52_428_800),
                new UserDatasetProperties.Minio("http://localhost:9000", "test", "test", "test")
        );
    }
}
