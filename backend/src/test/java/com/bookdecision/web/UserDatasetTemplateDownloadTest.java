package com.bookdecision.web;

import com.bookdecision.application.userdataset.UserDatasetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UserDatasetTemplateDownloadTest {

    @Test
    void addsUtf8BomToBothExcelFacingCsvDownloads() throws Exception {
        @SuppressWarnings("unchecked")
        ObjectProvider<UserDatasetService> provider = mock(ObjectProvider.class);
        UserDatasetController controller = new UserDatasetController(provider);

        ResponseEntity<byte[]> template = controller.template();
        ResponseEntity<byte[]> example = controller.example();

        assertThat(template.getBody()).startsWith((byte) 0xef, (byte) 0xbb, (byte) 0xbf);
        assertThat(example.getBody()).startsWith((byte) 0xef, (byte) 0xbb, (byte) 0xbf);
        assertThat(example.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("user-offer-example.csv");
        assertThat(new String(template.getBody(), StandardCharsets.UTF_8))
                .contains("数据格式版本,ISBN,书名,数量,平台,回收状态,单本价格（元）,重复书限制");
        assertThat(new String(example.getBody(), StandardCharsets.UTF_8))
                .contains("用户报价-v2")
                .contains(",回收,17.38,1")
                .contains(",回收,4.80,0")
                .contains(",未知,,")
                .doesNotContain("可按填写数量回收", "每单同书限一本", "遵循平台默认");
    }
}
