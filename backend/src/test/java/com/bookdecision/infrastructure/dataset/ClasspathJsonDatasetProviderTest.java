package com.bookdecision.infrastructure.dataset;

import com.bookdecision.application.dataset.DatasetSnapshot;
import com.bookdecision.application.dataset.SourceKind;
import com.bookdecision.domain.OfferStatus;
import com.bookdecision.domain.OrderThreshold;
import com.bookdecision.domain.RepeatPolicy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasspathJsonDatasetProviderTest {

    private static ClasspathJsonDatasetProvider provider;

    @BeforeAll
    static void loadDatasetOnce() {
        provider = new ClasspathJsonDatasetProvider(JsonMapper.builder().build());
    }

    @Test
    void loadsOnlyTheSupportedVersion() {
        assertThat(provider.findByVersion(ClasspathJsonDatasetProvider.SUPPORTED_VERSION)).isPresent();
        assertThat(provider.findByVersion("observed-private-v1")).isEmpty();
        assertThat(provider.findByVersion(null)).isEmpty();
    }

    @Test
    void mapsTheMixedSourceDatasetAndDisclaimers() {
        DatasetSnapshot snapshot = dataset();

        assertThat(snapshot.version()).isEqualTo("mixed-demo-v1");
        assertThat(snapshot.sourceKind()).isEqualTo(SourceKind.MIXED);
        assertThat(snapshot.disclaimers())
                .extracting(disclaimer -> disclaimer.code())
                .containsExactlyInAnyOrder(
                        "OBSERVED_CATALOG_AND_RULE_SHAPES",
                        "SYNTHETIC_OFFER_MATRIX",
                        "NOT_REAL_TIME_QUOTES",
                        "ESTIMATE_NOT_SETTLEMENT"
                );
        assertThat(snapshot.catalog()).hasSize(11);
        assertThat(snapshot.platforms()).hasSize(5);
        assertThat(snapshot.offers()).hasSize(55);
        assertThat(snapshot.platformRuleSummaries()).hasSize(5);
    }

    @Test
    void recursivelyMapsAnyOfAndAllOfThresholds() {
        OrderThreshold platformB = dataset().platformById().get("platform-b").threshold();
        assertThat(platformB).isInstanceOf(OrderThreshold.AnyOf.class);
        assertThat(((OrderThreshold.AnyOf) platformB).children())
                .containsExactly(
                        new OrderThreshold.AmountAtLeast(4800),
                        new OrderThreshold.BookCountAtLeast(8)
                );

        OrderThreshold platformE = dataset().platformById().get("platform-e").threshold();
        assertThat(platformE).isEqualTo(new OrderThreshold.AnyOf(List.of(
                new OrderThreshold.AmountAtLeast(3800),
                new OrderThreshold.AllOf(List.of(
                        new OrderThreshold.BookCountAtLeast(10),
                        new OrderThreshold.AveragePriceAtLeast(150)
                ))
        )));
    }

    @Test
    void strictlyMapsOfferStatusAndRepeatPolicies() {
        DatasetSnapshot snapshot = dataset();

        assertThat(snapshot.offers())
                .filteredOn(offer -> offer.platformId().equals("platform-a")
                        && offer.isbn().equals("9787040599008"))
                .singleElement()
                .satisfies(offer -> {
                    assertThat(offer.status()).isEqualTo(OfferStatus.ACCEPTED);
                    assertThat(offer.unitPriceCents()).isEqualTo(193);
                    assertThat(offer.repeatPolicy()).isEqualTo(RepeatPolicy.ONE_PER_ORDER);
                });
        assertThat(snapshot.offers())
                .filteredOn(offer -> offer.platformId().equals("platform-a")
                        && offer.isbn().equals("9787303147533"))
                .singleElement()
                .satisfies(offer -> {
                    assertThat(offer.status()).isEqualTo(OfferStatus.UNKNOWN);
                    assertThat(offer.unitPriceCents()).isZero();
                    assertThat(offer.repeatPolicy()).isEqualTo(RepeatPolicy.INHERIT_PLATFORM);
                });
        assertThat(snapshot.platformById().get("platform-c").defaultRepeatPolicy())
                .isEqualTo(RepeatPolicy.ONE_PER_ORDER);
    }

    @Test
    void exposesOnlyImmutableSnapshotCollections() {
        DatasetSnapshot snapshot = dataset();

        assertThatThrownBy(() -> snapshot.catalog().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.platformRuleSummaries().put("extra", "invalid"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static DatasetSnapshot dataset() {
        return provider.findByVersion(ClasspathJsonDatasetProvider.SUPPORTED_VERSION).orElseThrow();
    }
}
