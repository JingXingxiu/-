package com.bookdecision.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DecisionApiIntegrationTest {

    private static final String DATASET_VERSION = "mixed-demo-v1";
    private static final String POLICY_VERSION = "max-books-money-platforms-orders-v1";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesVersionedMixedSourceCatalogAndSuggestedInventory() throws Exception {
        mockMvc.perform(get("/api/v1/demo/catalog")
                        .param("datasetVersion", DATASET_VERSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetVersion").value(DATASET_VERSION))
                .andExpect(jsonPath("$.objectivePolicyVersion").value(POLICY_VERSION))
                .andExpect(jsonPath("$.engineVersion").value("cp-sat-lexicographic-v1"))
                .andExpect(jsonPath("$.sourceKind").value("MIXED"))
                .andExpect(jsonPath("$.platformDisplayMode").value("REAL"))
                .andExpect(jsonPath("$.disclaimers", hasSize(4)))
                .andExpect(jsonPath("$.books", hasSize(11)))
                .andExpect(jsonPath("$.platforms", hasSize(5)))
                .andExpect(jsonPath("$.platforms[0].platformDisplayName").value("小谷吖"))
                .andExpect(jsonPath("$.platforms[0].rejectionConditions").value(containsString("来源非法")))
                .andExpect(jsonPath("$.platforms[0].repeatPolicyDescription").value(containsString("有限限制")))
                .andExpect(jsonPath("$.platforms[0].collectedAt").value("2026-08-09"))
                .andExpect(jsonPath("$.platforms[0].sourceDescription").value("人工采样的小程序规则历史快照"))
                .andExpect(jsonPath("$.platforms[0].sourceReference")
                        .value("#小程序://小谷吖/ESxo7yFO2r5UPpE"))
                .andExpect(jsonPath("$.suggestedInventory", hasSize(11)));
    }

    @Test
    void solvesTheDocumentedCrossPlatformDemoScenario() throws Exception {
        mockMvc.perform(post("/api/v1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allCatalogBooksRequest(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetVersion").value(DATASET_VERSION))
                .andExpect(jsonPath("$.objectivePolicyVersion").value(POLICY_VERSION))
                .andExpect(jsonPath("$.solveStatus").value("OPTIMAL"))
                .andExpect(jsonPath("$.input").value(11))
                .andExpect(jsonPath("$.sold").value(11))
                .andExpect(jsonPath("$.unsold").value(0))
                .andExpect(jsonPath("$.estimatedAmountCents").value(6659))
                .andExpect(jsonPath("$.platformCount").value(2))
                .andExpect(jsonPath("$.orderCount").value(2))
                .andExpect(jsonPath("$.orders", hasSize(2)))
                .andExpect(jsonPath("$.requestFingerprint", matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.dataWarnings[0]").value("OFFER_DATA_INCOMPLETE"));
    }

    @Test
    void supportsTheMaximumAmountPolicyThroughTheExistingDecisionEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetVersion": "mixed-demo-v1",
                                  "objectivePolicyVersion": "max-money-books-platforms-orders-v1",
                                  "inventory": [
                                    {"isbn": "9787521766912", "quantity": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectivePolicyVersion")
                        .value("max-money-books-platforms-orders-v1"))
                .andExpect(jsonPath("$.solveStatus").value("OPTIMAL"));
    }

    @Test
    void stillRejectsAnUnknownObjectivePolicy() throws Exception {
        mockMvc.perform(post("/api/v1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetVersion": "mixed-demo-v1",
                                  "objectivePolicyVersion": "unknown-policy-v1",
                                  "inventory": [
                                    {"isbn": "9787521766912", "quantity": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_INPUT_REJECTED"));
    }

    @Test
    void keepsAValidUnknownIsbnAsAVisiblePartialResult() throws Exception {
        mockMvc.perform(post("/api/v1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allCatalogBooksRequest(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input").value(12))
                .andExpect(jsonPath("$.sold").value(11))
                .andExpect(jsonPath("$.unsold").value(1))
                .andExpect(jsonPath("$.unallocated[0].isbn").value("9780000000002"))
                .andExpect(jsonPath("$.unallocated[0].reason").value("ISBN_NOT_IN_DATASET"));
    }

    @Test
    void acceptsAValid979IsbnThatIsNotInTheDataset() throws Exception {
        mockMvc.perform(post("/api/v1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetVersion": "mixed-demo-v1",
                                  "objectivePolicyVersion": "max-books-money-platforms-orders-v1",
                                  "inventory": [
                                    {"isbn": "9790000000001", "quantity": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sold").value(0))
                .andExpect(jsonPath("$.unallocated[0].isbn").value("9790000000001"))
                .andExpect(jsonPath("$.unallocated[0].reason").value("ISBN_NOT_IN_DATASET"));
    }

    @Test
    void rejectsAValidEan13WithoutAnIsbnPrefix() throws Exception {
        mockMvc.perform(post("/api/v1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetVersion": "mixed-demo-v1",
                                  "objectivePolicyVersion": "max-books-money-platforms-orders-v1",
                                  "inventory": [
                                    {"isbn": "4006381333931", "quantity": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_INPUT_REJECTED"))
                .andExpect(jsonPath("$.errors[0].field").value("inventory"));
    }

    @Test
    void reportsSemanticInventoryErrorsAsProblemDetail() throws Exception {
        String request = """
                {
                  "datasetVersion": "mixed-demo-v1",
                  "objectivePolicyVersion": "max-books-money-platforms-orders-v1",
                  "inventory": [
                    {"isbn": "9787020002207", "quantity": 1},
                    {"isbn": "9787020002207", "quantity": 1}
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_INPUT_REJECTED"))
                .andExpect(jsonPath("$.traceId", matchesPattern("[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.errors[0].field").value("inventory"));
    }

    @Test
    void reportsUnknownDatasetWithoutFallingBackToLatest() throws Exception {
        mockMvc.perform(get("/api/v1/demo/catalog")
                        .param("datasetVersion", "missing-version"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DATASET_NOT_FOUND"))
                .andExpect(jsonPath("$.datasetVersion").value("missing-version"));
    }

    @Test
    void exposesHealthAndGeneratedOpenApi() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/v1/decisions']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/decisions'].post.description",
                        containsString("max-money-books-platforms-orders-v1")))
                .andExpect(jsonPath("$.paths['/api/v1/decision-options'].post.summary",
                        containsString("maximum-amount")))
                .andExpect(jsonPath("$.paths['/api/v1/user-datasets/template.csv'].get.summary",
                        containsString("Chinese v2")))
                .andExpect(jsonPath("$.paths['/api/v1/user-datasets/uploads'].post.summary",
                        containsString("legacy English v1")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/user-datasets/uploads'].post.responses['413']"
                ).exists());
    }

    @Test
    void preservesFrameworkHttpErrorSemanticsInsteadOfReturningGeneric500() throws Exception {
        mockMvc.perform(get("/api/v1/route-that-does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("ROUTE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/decisions"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));

        mockMvc.perform(post("/api/v1/decisions")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));

        mockMvc.perform(get("/api/v1/demo/catalog")
                        .param("datasetVersion", DATASET_VERSION)
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.errorCode").value("NOT_ACCEPTABLE"));
    }

    @Test
    void returnsDistinctExplainableDecisionOptions() throws Exception {
        mockMvc.perform(post("/api/v1/decision-options")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(allCatalogBooksOptionsRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plans[0].kind").value("RECOMMENDED"))
                .andExpect(jsonPath("$.plans[0].decision.sold").value(11))
                .andExpect(jsonPath("$.plans[0].decision.solveStatus").value("OPTIMAL"))
                .andExpect(jsonPath("$.plans[0].decision.orders[0].platformCode").isNotEmpty())
                .andExpect(jsonPath("$.plans[0].decision.orders[0].platformDisplayName").isNotEmpty())
                .andExpect(jsonPath("$.plans[*].kind", hasItem("BEST_SINGLE_PLATFORM")));
    }

    private static String allCatalogBooksRequest(boolean includeUnknown) {
        String unknown = includeUnknown
                ? ",{\"isbn\":\"9780000000002\",\"quantity\":1}"
                : "";
        return """
                {
                  "datasetVersion": "mixed-demo-v1",
                  "objectivePolicyVersion": "max-books-money-platforms-orders-v1",
                  "inventory": [
                    {"isbn":"9787020002207","quantity":1},
                    {"isbn":"9787111544937","quantity":1},
                    {"isbn":"9787508647357","quantity":1},
                    {"isbn":"9787534352362","quantity":1},
                    {"isbn":"9787303147533","quantity":1},
                    {"isbn":"9787521333299","quantity":1},
                    {"isbn":"9787040396638","quantity":1},
                    {"isbn":"9787115621375","quantity":1},
                    {"isbn":"9787040599008","quantity":1},
                    {"isbn":"9787040599015","quantity":1},
                    {"isbn":"9787521766912","quantity":1}%s
                  ]
                }
                """.formatted(unknown);
    }

    private static String allCatalogBooksOptionsRequest() {
        return allCatalogBooksRequest(false)
                .replace("\"objectivePolicyVersion\": \"max-books-money-platforms-orders-v1\",", "");
    }
}
