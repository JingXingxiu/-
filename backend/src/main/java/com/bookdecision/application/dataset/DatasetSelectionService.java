package com.bookdecision.application.dataset;

import com.bookdecision.application.ApplicationErrorCode;
import com.bookdecision.application.ApplicationException;
import com.bookdecision.application.BusinessInputException;
import com.bookdecision.application.userdataset.StoredUserDataset;
import com.bookdecision.application.userdataset.UserDatasetErrorCode;
import com.bookdecision.application.userdataset.UserDatasetException;
import com.bookdecision.application.userdataset.UserDatasetService;
import com.bookdecision.domain.PlatformOffer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public final class DatasetSelectionService {

    private static final DatasetDisclaimer USER_DATA_NOTICE = new DatasetDisclaimer(
            "PRIVATE_USER_DATASET",
            "用户上传数据仅用于本次私有计算，默认保留30天；平台规则仍来自所选基础数据版本。"
    );

    private final DatasetProvider baseProvider;
    private final ObjectProvider<UserDatasetService> userDatasetServiceProvider;

    public DatasetSelectionService(
            DatasetProvider baseProvider,
            ObjectProvider<UserDatasetService> userDatasetServiceProvider
    ) {
        this.baseProvider = Objects.requireNonNull(baseProvider, "baseProvider must not be null");
        this.userDatasetServiceProvider = Objects.requireNonNull(
                userDatasetServiceProvider,
                "userDatasetServiceProvider must not be null"
        );
    }

    public ResolvedDataset resolve(
            String baseDatasetVersion,
            DatasetSelection selection,
            String accessToken
    ) {
        DatasetSelection actualSelection = selection == null
                ? DatasetSelection.systemOnly()
                : selection;
        validateSelection(actualSelection);
        DatasetSnapshot base = baseProvider.findByVersion(baseDatasetVersion)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.DATASET_NOT_FOUND,
                        Map.of("datasetVersion", baseDatasetVersion)
                ));
        if (actualSelection.dataMode() == DataMode.SYSTEM_ONLY) {
            return systemOnly(base);
        }

        UserDatasetService service = userDatasetServiceProvider.getIfAvailable();
        if (service == null) {
            throw new UserDatasetException(UserDatasetErrorCode.FEATURE_DISABLED);
        }
        StoredUserDataset user = service.requireAuthorized(actualSelection.uploadId(), accessToken);
        if (!base.version().equals(user.upload().baseDatasetVersion())) {
            throw new UserDatasetException(
                    UserDatasetErrorCode.DATASET_MISMATCH,
                    Map.of(
                            "requestedBaseDatasetVersion", base.version(),
                            "uploadBaseDatasetVersion", user.upload().baseDatasetVersion()
                    )
            );
        }
        return actualSelection.dataMode() == DataMode.USER_ONLY
                ? userOnly(base, user)
                : overlay(base, user);
    }

    private static ResolvedDataset systemOnly(DatasetSnapshot base) {
        Map<OfferKey, OfferDataOrigin> origins = new HashMap<>();
        base.offers().forEach(offer -> origins.put(
                new OfferKey(offer.isbn(), offer.platformId()),
                OfferDataOrigin.SYSTEM
        ));
        return new ResolvedDataset(base, DataMode.SYSTEM_ONLY, null, origins);
    }

    private static ResolvedDataset userOnly(DatasetSnapshot base, StoredUserDataset user) {
        List<CatalogBook> catalog = user.books().stream()
                .map(book -> new CatalogBook(book.isbn(), book.title()))
                .sorted(Comparator.comparing(CatalogBook::isbn))
                .toList();
        Map<OfferKey, PlatformOffer> offers = new LinkedHashMap<>();
        Map<OfferKey, OfferDataOrigin> origins = new HashMap<>();
        user.offers().forEach(offer -> {
            OfferKey key = new OfferKey(offer.isbn(), offer.platformId());
            offers.put(key, offer);
            origins.put(key, OfferDataOrigin.USER);
        });
        for (CatalogBook book : catalog) {
            base.platforms().forEach(platform -> {
                OfferKey key = new OfferKey(book.isbn(), platform.id());
                offers.putIfAbsent(key, PlatformOffer.unknown(book.isbn(), platform.id()));
                origins.putIfAbsent(key, OfferDataOrigin.USER);
            });
        }
        DatasetSnapshot snapshot = snapshot(
                base,
                catalog,
                stableOffers(offers),
                appendUserNotice(base.disclaimers())
        );
        return new ResolvedDataset(snapshot, DataMode.USER_ONLY, user.upload().id(), origins);
    }

    private static ResolvedDataset overlay(DatasetSnapshot base, StoredUserDataset user) {
        Map<String, CatalogBook> catalog = new LinkedHashMap<>();
        base.catalog().forEach(book -> catalog.put(book.isbn(), book));
        user.books().forEach(book -> catalog.put(book.isbn(), new CatalogBook(book.isbn(), book.title())));

        Map<OfferKey, PlatformOffer> offers = new LinkedHashMap<>();
        Map<OfferKey, OfferDataOrigin> origins = new HashMap<>();
        base.offers().forEach(offer -> {
            OfferKey key = new OfferKey(offer.isbn(), offer.platformId());
            offers.put(key, offer);
            origins.put(key, OfferDataOrigin.SYSTEM);
        });
        user.books().forEach(book -> base.platforms().forEach(platform -> {
            OfferKey key = new OfferKey(book.isbn(), platform.id());
            offers.putIfAbsent(key, PlatformOffer.unknown(book.isbn(), platform.id()));
            origins.putIfAbsent(key, OfferDataOrigin.USER);
        }));
        user.offers().forEach(offer -> {
            OfferKey key = new OfferKey(offer.isbn(), offer.platformId());
            offers.put(key, offer);
            origins.put(key, OfferDataOrigin.USER);
        });

        DatasetSnapshot snapshot = snapshot(
                base,
                catalog.values().stream().sorted(Comparator.comparing(CatalogBook::isbn)).toList(),
                stableOffers(offers),
                appendUserNotice(base.disclaimers())
        );
        return new ResolvedDataset(snapshot, DataMode.USER_OVERLAY, user.upload().id(), origins);
    }

    private static DatasetSnapshot snapshot(
            DatasetSnapshot base,
            List<CatalogBook> catalog,
            List<PlatformOffer> offers,
            List<DatasetDisclaimer> disclaimers
    ) {
        return new DatasetSnapshot(
                base.version(),
                SourceKind.MIXED,
                disclaimers,
                catalog,
                base.platforms(),
                offers,
                base.platformRuleSummaries()
        );
    }

    private static List<PlatformOffer> stableOffers(Map<OfferKey, PlatformOffer> offers) {
        return offers.values().stream()
                .sorted(Comparator.comparing(PlatformOffer::isbn).thenComparing(PlatformOffer::platformId))
                .toList();
    }

    private static List<DatasetDisclaimer> appendUserNotice(List<DatasetDisclaimer> base) {
        List<DatasetDisclaimer> result = new ArrayList<>(base);
        result.removeIf(disclaimer -> disclaimer.code().equals(USER_DATA_NOTICE.code()));
        result.add(USER_DATA_NOTICE);
        return List.copyOf(result);
    }

    private static void validateSelection(DatasetSelection selection) {
        UUID uploadId = selection.uploadId();
        if (selection.dataMode() == DataMode.SYSTEM_ONLY && uploadId != null) {
            throw new BusinessInputException(
                    "uploadId",
                    List.of("uploadId must be absent when dataMode is SYSTEM_ONLY")
            );
        }
        if (selection.dataMode() != DataMode.SYSTEM_ONLY && uploadId == null) {
            throw new BusinessInputException(
                    "uploadId",
                    List.of("uploadId is required when dataMode is USER_ONLY or USER_OVERLAY")
            );
        }
    }
}
