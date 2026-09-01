package com.bookdecision.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OfferLookupApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void previewsKnownOffersAndKeepsAnUnknownCatalogIsbnVisible() throws Exception {
        String request = """
                {
                  "datasetVersion": "mixed-demo-v1",
                  "isbns": ["9787111544937", "9780000000002", "9787020002207"]
                }
                """;

        mockMvc.perform(post("/api/v1/books/offers:lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetVersion").value("mixed-demo-v1"))
                .andExpect(jsonPath("$.sourceKind").value("MIXED"))
                .andExpect(jsonPath("$.amountUnit").value("CNY_CENT"))
                .andExpect(jsonPath("$.disclaimers", hasSize(4)))
                .andExpect(jsonPath("$.books", hasSize(3)))
                .andExpect(jsonPath("$.books[0].isbn").value("9787111544937"))
                .andExpect(jsonPath("$.books[0].catalogStatus").value("FOUND"))
                .andExpect(jsonPath("$.books[0].offers", hasSize(5)))
                .andExpect(jsonPath("$.books[0].offers[0].platformCode").value("platform-a"))
                .andExpect(jsonPath("$.books[0].offers[0].platformDisplayName").value("小谷吖"))
                .andExpect(jsonPath("$.books[0].offers[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.books[0].offers[0].unitPriceCents").value(1663))
                .andExpect(jsonPath("$.books[0].offers[1].status").value("REJECTED"))
                .andExpect(jsonPath("$.books[0].offers[1].unitPriceCents").value(nullValue()))
                .andExpect(jsonPath("$.books[1].isbn").value("9780000000002"))
                .andExpect(jsonPath("$.books[1].title").value(nullValue()))
                .andExpect(jsonPath("$.books[1].catalogStatus").value("ISBN_NOT_IN_DATASET"))
                .andExpect(jsonPath("$.books[1].offers", hasSize(0)))
                .andExpect(jsonPath("$.books[2].isbn").value("9787020002207"));
    }

    @Test
    void rejectsAnInvalidIsbnChecksumAsSemanticInput() throws Exception {
        mockMvc.perform(post("/api/v1/books/offers:lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetVersion": "mixed-demo-v1",
                                  "isbns": ["9780000000001"]
                                }
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_INPUT_REJECTED"))
                .andExpect(jsonPath("$.errors[0].field").value("isbns"));
    }

    @Test
    void acceptsAValid979IsbnThatIsNotInTheDataset() throws Exception {
        mockMvc.perform(post("/api/v1/books/offers:lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetVersion": "mixed-demo-v1",
                                  "isbns": ["9790000000001"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].isbn").value("9790000000001"))
                .andExpect(jsonPath("$.books[0].catalogStatus").value("ISBN_NOT_IN_DATASET"));
    }

    @Test
    void rejectsAValidEan13WithoutAnIsbnPrefix() throws Exception {
        mockMvc.perform(post("/api/v1/books/offers:lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetVersion": "mixed-demo-v1",
                                  "isbns": ["4006381333931"]
                                }
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_INPUT_REJECTED"))
                .andExpect(jsonPath("$.errors[0].field").value("isbns"));
    }

    @Test
    void rejectsDuplicateIsbnsInsteadOfSilentlyChangingTheRequest() throws Exception {
        mockMvc.perform(post("/api/v1/books/offers:lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetVersion": "mixed-demo-v1",
                                  "isbns": ["9787020002207", "9787020002207"]
                                }
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value(
                        "ISBN values must be unique: 9787020002207"
                ));
    }

    @Test
    void doesNotFallBackWhenTheRequestedDatasetDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/v1/books/offers:lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetVersion": "missing-version",
                                  "isbns": ["9787020002207"]
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DATASET_NOT_FOUND"))
                .andExpect(jsonPath("$.datasetVersion").value("missing-version"));
    }

    @Test
    void publishesTheLookupContractInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/books/offers:lookup'].post").exists());
    }
}
