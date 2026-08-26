package com.bookdecision.application;

import com.bookdecision.application.dataset.CatalogBook;
import com.bookdecision.application.dataset.DatasetDisclaimer;
import com.bookdecision.application.dataset.DatasetProvider;
import com.bookdecision.application.dataset.DatasetSelectionService;
import com.bookdecision.application.dataset.DatasetSnapshot;
import com.bookdecision.application.dataset.ResolvedDataset;
import com.bookdecision.domain.OfferStatus;
import com.bookdecision.domain.PlatformOffer;
import com.bookdecision.domain.PlatformRule;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.bookdecision.domain.AmountUnits.CNY_CENT;

/**
 * Looks up the current immutable dataset without invoking the optimization engine.
 *
 * <p>This use case deliberately depends on {@link DatasetProvider}, not on JSON or a database,
 * so the API contract remains unchanged when persistence is replaced.</p>
 */
@Service
public final class OfferLookupService {

    private final DatasetSelectionService datasetSelectionService;

    public OfferLookupService(DatasetSelectionService datasetSelectionService) {
        this.datasetSelectionService = Objects.requireNonNull(
                datasetSelectionService,
                "datasetSelectionService must not be null"
        );
    }

    public OfferLookupResult lookup(OfferLookupCommand command) {
        return lookup(command, null);
    }

    public OfferLookupResult lookup(OfferLookupCommand command, String uploadAccessToken) {
        Objects.requireNonNull(command, "command must not be null");
        validate(command);

        ResolvedDataset resolved = datasetSelectionService.resolve(
                command.datasetVersion(),
                command.datasetSelection(),
                uploadAccessToken
        );
        DatasetSnapshot dataset = resolved.snapshot();
        Map<String, CatalogBook> catalogByIsbn = dataset.catalogByIsbn();
        List<PlatformRule> stablePlatforms = dataset.platforms().stream()
                .sorted(Comparator.comparing(PlatformRule::id))
                .toList();
        Map<OfferKey, PlatformOffer> offerByKey = new HashMap<>();
        dataset.offers().forEach(offer -> offerByKey.put(
                new OfferKey(offer.isbn(), offer.platformId()),
                offer
        ));

        List<OfferLookupResult.Book> books = command.isbns().stream()
                .map(isbn -> mapBook(isbn, catalogByIsbn, stablePlatforms, offerByKey, resolved))
                .toList();
        return new OfferLookupResult(
                dataset.version(),
                resolved.dataMode(),
                resolved.uploadId(),
                dataset.sourceKind(),
                CNY_CENT,
                dataset.disclaimers().stream()
                        .sorted(Comparator.comparing(DatasetDisclaimer::code))
                        .toList(),
                books
        );
    }

    private static OfferLookupResult.Book mapBook(
            String isbn,
            Map<String, CatalogBook> catalogByIsbn,
            List<PlatformRule> platforms,
            Map<OfferKey, PlatformOffer> offerByKey,
            ResolvedDataset resolved
    ) {
        CatalogBook book = catalogByIsbn.get(isbn);
        if (book == null) {
            return new OfferLookupResult.Book(
                    isbn,
                    null,
                    OfferLookupResult.CatalogStatus.ISBN_NOT_IN_DATASET,
                    List.of()
            );
        }

        List<OfferLookupResult.Offer> offers = platforms.stream()
                .map(platform -> mapOffer(isbn, platform, offerByKey, resolved))
                .toList();
        return new OfferLookupResult.Book(
                isbn,
                book.title(),
                OfferLookupResult.CatalogStatus.FOUND,
                offers
        );
    }

    private static OfferLookupResult.Offer mapOffer(
            String isbn,
            PlatformRule platform,
            Map<OfferKey, PlatformOffer> offerByKey,
            ResolvedDataset resolved
    ) {
        PlatformOffer offer = offerByKey.get(new OfferKey(isbn, platform.id()));
        OfferStatus status = offer == null ? OfferStatus.UNKNOWN : offer.status();
        Long price = status == OfferStatus.ACCEPTED ? offer.unitPriceCents() : null;
        return new OfferLookupResult.Offer(
                platform.id(),
                platform.name(),
                status,
                price,
                resolved.offerOrigin(isbn, platform.id())
        );
    }

    private static void validate(OfferLookupCommand command) {
        List<String> violations = new ArrayList<>();
        if (command.isbns() == null || command.isbns().isEmpty()) {
            violations.add("ISBN list must not be empty");
        } else if (command.isbns().size() > InputLimits.MAX_LOOKUP_ISBN_COUNT) {
            violations.add(
                    "ISBN list must not contain more than " + InputLimits.MAX_LOOKUP_ISBN_COUNT + " values"
            );
        }

        Set<String> seen = new HashSet<>();
        if (command.isbns() != null) {
            for (String isbn : command.isbns()) {
                if (!Isbn13Validator.isValid(isbn)) {
                    violations.add("ISBN must be a valid ISBN-13: " + isbn);
                } else if (!seen.add(isbn)) {
                    violations.add("ISBN values must be unique: " + isbn);
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new BusinessInputException("isbns", violations);
        }
    }

    private record OfferKey(String isbn, String platformId) {
    }
}
