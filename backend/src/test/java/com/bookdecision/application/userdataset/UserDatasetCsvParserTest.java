package com.bookdecision.application.userdataset;

import com.bookdecision.domain.OfferStatus;
import com.bookdecision.domain.RepeatPolicy;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserDatasetCsvParserTest {

    private static final Set<String> PLATFORMS = Set.of(
            "platform-a", "platform-b", "platform-c", "platform-d", "platform-e"
    );
    private final UserDatasetCsvParser parser = new UserDatasetCsvParser();

    @Test
    void acceptsChineseV2AndMapsNumericRepeatCodesToDomainValues() {
        ParsedUserDataset result = parser.parse(v2Bytes(), PLATFORMS, properties());

        assertThat(result.schemaVersion()).isEqualTo("用户报价-v2");
        assertThat(result.rowCount()).isEqualTo(10);
        assertThat(result.books()).hasSize(2)
                .anySatisfy(book -> assertThat(book.title()).isEqualTo("思考，快与慢"));
        assertThat(result.offers()).hasSize(10)
                .anySatisfy(offer -> {
                    assertThat(offer.platformId()).isEqualTo("platform-a");
                    assertThat(offer.status()).isEqualTo(OfferStatus.ACCEPTED);
                    assertThat(offer.unitPriceCents()).isEqualTo(1738);
                    assertThat(offer.repeatPolicy()).isEqualTo(RepeatPolicy.UP_TO_INVENTORY);
                })
                .anySatisfy(offer -> {
                    assertThat(offer.platformId()).isEqualTo("platform-d");
                    assertThat(offer.status()).isEqualTo(OfferStatus.UNKNOWN);
                    assertThat(offer.repeatPolicy()).isEqualTo(RepeatPolicy.INHERIT_PLATFORM);
                });
    }

    @Test
    void keepsAcceptingPreviousChineseRepeatLabels() {
        byte[] csv = """
                数据格式版本,ISBN,书名,数量,平台,回收状态,单本价格（元）,重复书限制
                用户报价-v2,9787111544937,深入理解计算机系统,2,平台A,回收,17.38,可按填写数量回收
                用户报价-v2,9787111544937,深入理解计算机系统,2,平台B,回收,16.92,可按填写数量回收
                用户报价-v2,9787111544937,深入理解计算机系统,2,平台C,回收,4.80,每单同书限一本
                用户报价-v2,9787111544937,深入理解计算机系统,2,平台D,未知,,遵循平台默认
                用户报价-v2,9787111544937,深入理解计算机系统,2,平台E,不回收,,遵循平台默认
                """.getBytes(StandardCharsets.UTF_8);

        ParsedUserDataset result = parser.parse(csv, PLATFORMS, properties());

        assertThat(result.offers())
                .extracting(offer -> offer.repeatPolicy())
                .contains(RepeatPolicy.UP_TO_INVENTORY,
                        RepeatPolicy.ONE_PER_ORDER,
                        RepeatPolicy.INHERIT_PLATFORM);
    }

    @Test
    void keepsAcceptingLegacyEnglishV1AndPreservesItsActualVersion() {
        ParsedUserDataset result = parser.parse(legacyV1Bytes(), PLATFORMS, properties());

        assertThat(result.schemaVersion()).isEqualTo("user-offer-v1");
        assertThat(result.rowCount()).isEqualTo(5);
        assertThat(result.books()).singleElement()
                .satisfies(book -> assertThat(book.title()).isEqualTo("深入理解计算机系统"));
    }

    @Test
    void acceptsStablePlatformIdsInChineseV2() {
        String csv = v2Text().replace("平台A", "platform-a");

        ParsedUserDataset result = parser.parse(csv.getBytes(StandardCharsets.UTF_8), PLATFORMS, properties());

        assertThat(result.offers()).anySatisfy(offer -> assertThat(offer.platformId()).isEqualTo("platform-a"));
    }

    @Test
    void acceptsAnOptionalUtf8Bom() {
        byte[] original = v2Bytes();
        byte[] withBom = new byte[original.length + 3];
        withBom[0] = (byte) 0xef;
        withBom[1] = (byte) 0xbb;
        withBom[2] = (byte) 0xbf;
        System.arraycopy(original, 0, withBom, 3, original.length);

        assertThat(parser.parse(withBom, PLATFORMS, properties()).rowCount()).isEqualTo(10);
    }

    @Test
    void rejectsControlCharactersWithoutEchoingTheRawRow() {
        String csv = v2Text().replace("深入理解计算机系统", "深入\u0000理解计算机系统");

        assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8), PLATFORMS, properties()))
                .isInstanceOf(UserDatasetException.class)
                .hasMessageContaining("CSV")
                .satisfies(exception -> assertThat(((UserDatasetException) exception).violations())
                        .containsExactly("第 1 行：书名不能包含控制字符"));
    }

    @Test
    void rejectsSpreadsheetFormulaInjectionInTitleAfterTrimming() {
        String csv = v2Text().replace("深入理解计算机系统", "   =HYPERLINK(bad)");

        assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8), PLATFORMS, properties()))
                .isInstanceOf(UserDatasetException.class)
                .satisfies(exception -> assertThat(((UserDatasetException) exception).violations())
                        .containsExactly("第 1 行：书名不能以 =、+、- 或 @ 开头"));
    }

    @Test
    void rejectsAnIncompleteIsbnPlatformMatrixWithUserFacingPlatformName() {
        String csv = v2Text();
        int lastLine = csv.lastIndexOf("用户报价-v2");
        byte[] incomplete = csv.substring(0, lastLine).getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(incomplete, PLATFORMS, properties()))
                .isInstanceOf(UserDatasetException.class)
                .satisfies(exception -> assertThat(((UserDatasetException) exception).violations().getFirst())
                        .contains("必须为每个平台各填写一行")
                        .contains("平台E"));
    }

    @Test
    void rejectsEnglishEnumValuesInsideChineseV2WithChineseGuidance() {
        String csv = v2Text().replaceFirst(",回收,17.38,", ",ACCEPTED,17.38,");

        assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8), PLATFORMS, properties()))
                .isInstanceOf(UserDatasetException.class)
                .satisfies(exception -> assertThat(((UserDatasetException) exception).violations())
                        .containsExactly("第 1 行：回收状态只支持：回收、不回收、未知"));
    }

    @Test
    void rejectsUnsupportedNumericRepeatCodeWithChineseGuidance() {
        String csv = v2Text().replaceFirst(",回收,17.38,1", ",回收,17.38,2");

        assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8), PLATFORMS, properties()))
                .isInstanceOf(UserDatasetException.class)
                .satisfies(exception -> assertThat(((UserDatasetException) exception).violations())
                        .containsExactly("第 1 行：重复书限制只支持：1（可按数量回收）、0（每个订单最多一本）或留空（沿用平台默认）"));
    }

    @Test
    void rejectsReorderedV2Header() {
        String csv = v2Text().replaceFirst("ISBN,书名", "书名,ISBN");

        assertThatThrownBy(() -> parser.parse(csv.getBytes(StandardCharsets.UTF_8), PLATFORMS, properties()))
                .isInstanceOf(UserDatasetException.class)
                .satisfies(exception -> assertThat(((UserDatasetException) exception).violations().getFirst())
                        .contains("表头不正确")
                        .contains(String.join(",", UserDatasetCsvParser.HEADERS)));
    }

    private static byte[] v2Bytes() {
        return v2Text().getBytes(StandardCharsets.UTF_8);
    }

    private static String v2Text() {
        return """
                数据格式版本,ISBN,书名,数量,平台,回收状态,单本价格（元）,重复书限制
                用户报价-v2,9787111544937,深入理解计算机系统,2,平台A,回收,17.38,1
                用户报价-v2,9787111544937,深入理解计算机系统,2,平台B,回收,16.92,1
                用户报价-v2,9787111544937,深入理解计算机系统,2,平台C,回收,4.80,0
                用户报价-v2,9787111544937,深入理解计算机系统,2,平台D,未知,,
                用户报价-v2,9787111544937,深入理解计算机系统,2,平台E,不回收,,
                用户报价-v2,9787521766912,"思考，快与慢",1,平台A,未知,,
                用户报价-v2,9787521766912,"思考，快与慢",1,平台B,回收,1.03,1
                用户报价-v2,9787521766912,"思考，快与慢",1,平台C,回收,19.10,0
                用户报价-v2,9787521766912,"思考，快与慢",1,平台D,不回收,,
                用户报价-v2,9787521766912,"思考，快与慢",1,平台E,回收,10.10,1
                """;
    }

    private static byte[] legacyV1Bytes() {
        return """
                schema_version,isbn,title,quantity,platform_id,status,unit_price_yuan,repeat_policy
                user-offer-v1,9787111544937,深入理解计算机系统,2,platform-a,ACCEPTED,17.38,UP_TO_INVENTORY
                user-offer-v1,9787111544937,深入理解计算机系统,2,platform-b,ACCEPTED,16.92,UP_TO_INVENTORY
                user-offer-v1,9787111544937,深入理解计算机系统,2,platform-c,ACCEPTED,4.80,ONE_PER_ORDER
                user-offer-v1,9787111544937,深入理解计算机系统,2,platform-d,UNKNOWN,,INHERIT_PLATFORM
                user-offer-v1,9787111544937,深入理解计算机系统,2,platform-e,REJECTED,,INHERIT_PLATFORM
                """.getBytes(StandardCharsets.UTF_8);
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
