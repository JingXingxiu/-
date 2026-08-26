package com.bookdecision.application.dataset;

import com.bookdecision.application.userdataset.StoredUserDataset;
import com.bookdecision.application.userdataset.UserDatasetBook;
import com.bookdecision.application.userdataset.UserDatasetService;
import com.bookdecision.application.userdataset.UserDatasetUpload;
import com.bookdecision.domain.OfferStatus;
import com.bookdecision.domain.OrderThreshold;
import com.bookdecision.domain.PlatformOffer;
import com.bookdecision.domain.PlatformRule;
import com.bookdecision.domain.RepeatPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatasetSelectionServiceTest {

    private static final String FIRST = "9787020002207";
    private static final String SECOND = "9787111544937";

    @Test
    void overlayUsesExplicitUnknownAsAnOverrideAndKeepsUntouchedSystemRows() {
        DatasetSnapshot base = base();
        UUID uploadId = UUID.randomUUID();
        StoredUserDataset user = stored(uploadId, List.of(
                PlatformOffer.unknown(FIRST, "platform-a"),
                PlatformOffer.accepted(FIRST, "platform-b", 999, RepeatPolicy.UP_TO_INVENTORY)
        ));
        DatasetSelectionService service = service(base, user);

        ResolvedDataset result = service.resolve(
                "base-v1",
                new DatasetSelection(DataMode.USER_OVERLAY, uploadId),
                "secret"
        );

        PlatformOffer explicitUnknown = offer(result.snapshot(), FIRST, "platform-a");
        PlatformOffer untouched = offer(result.snapshot(), SECOND, "platform-a");
        assertThat(explicitUnknown.status()).isEqualTo(OfferStatus.UNKNOWN);
        assertThat(result.offerOrigin(FIRST, "platform-a")).isEqualTo(OfferDataOrigin.USER);
        assertThat(untouched.status()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(result.offerOrigin(SECOND, "platform-a")).isEqualTo(OfferDataOrigin.SYSTEM);
    }

    @Test
    void userOnlyKeepsBasePlatformRulesButExcludesBaseOnlyBooks() {
        DatasetSnapshot base = base();
        UUID uploadId = UUID.randomUUID();
        StoredUserDataset user = stored(uploadId, List.of(
                PlatformOffer.unknown(FIRST, "platform-a"),
                PlatformOffer.accepted(FIRST, "platform-b", 999, RepeatPolicy.UP_TO_INVENTORY)
        ));
        DatasetSelectionService service = service(base, user);

        ResolvedDataset result = service.resolve(
                "base-v1",
                new DatasetSelection(DataMode.USER_ONLY, uploadId),
                "secret"
        );

        assertThat(result.snapshot().catalog()).extracting(CatalogBook::isbn).containsExactly(FIRST);
        assertThat(result.snapshot().platforms()).isEqualTo(base.platforms());
        assertThat(result.snapshot().offers()).hasSize(2);
        assertThat(result.snapshot().sourceKind()).isEqualTo(SourceKind.MIXED);
    }

    private static DatasetSelectionService service(DatasetSnapshot base, StoredUserDataset user) {
        DatasetProvider provider = version -> "base-v1".equals(version) ? Optional.of(base) : Optional.empty();
        UserDatasetService userService = mock(UserDatasetService.class);
        when(userService.requireAuthorized(user.upload().id(), "secret")).thenReturn(user);
        @SuppressWarnings("unchecked")
        ObjectProvider<UserDatasetService> objectProvider = mock(ObjectProvider.class);
        when(objectProvider.getIfAvailable()).thenReturn(userService);
        return new DatasetSelectionService(provider, objectProvider);
    }

    private static StoredUserDataset stored(UUID uploadId, List<PlatformOffer> offers) {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        UserDatasetUpload upload = new UserDatasetUpload(
                uploadId,
                "base-v1",
                "0".repeat(64),
                "source.csv",
                "private-uploads/" + uploadId + "/source.csv",
                "1".repeat(64),
                100,
                "user-offer-v1",
                2,
                1,
                false,
                now,
                now.plusSeconds(3600)
        );
        return new StoredUserDataset(
                upload,
                List.of(new UserDatasetBook(FIRST, "用户标题", 1)),
                offers
        );
    }

    private static DatasetSnapshot base() {
        List<CatalogBook> books = List.of(
                new CatalogBook(FIRST, "系统标题一"),
                new CatalogBook(SECOND, "系统标题二")
        );
        List<PlatformRule> platforms = List.of(
                new PlatformRule(
                        "platform-a", "平台A", new OrderThreshold.AmountAtLeast(1),
                        OptionalInt.empty(), RepeatPolicy.UP_TO_INVENTORY
                ),
                new PlatformRule(
                        "platform-b", "平台B", new OrderThreshold.AmountAtLeast(1),
                        OptionalInt.empty(), RepeatPolicy.UP_TO_INVENTORY
                )
        );
        List<PlatformOffer> offers = List.of(
                PlatformOffer.accepted(FIRST, "platform-a", 100, RepeatPolicy.UP_TO_INVENTORY),
                PlatformOffer.accepted(FIRST, "platform-b", 200, RepeatPolicy.UP_TO_INVENTORY),
                PlatformOffer.accepted(SECOND, "platform-a", 300, RepeatPolicy.UP_TO_INVENTORY),
                PlatformOffer.rejected(SECOND, "platform-b")
        );
        return new DatasetSnapshot(
                "base-v1",
                SourceKind.SYNTHETIC,
                List.of(new DatasetDisclaimer("NOTICE", "测试数据")),
                books,
                platforms,
                offers,
                Map.of("platform-a", "规则A", "platform-b", "规则B")
        );
    }

    private static PlatformOffer offer(DatasetSnapshot snapshot, String isbn, String platformId) {
        return snapshot.offers().stream()
                .filter(value -> value.isbn().equals(isbn) && value.platformId().equals(platformId))
                .findFirst()
                .orElseThrow();
    }
}
