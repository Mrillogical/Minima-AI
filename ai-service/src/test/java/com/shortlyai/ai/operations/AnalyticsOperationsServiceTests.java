package com.minima-AI.ai.operations;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AnalyticsOperationsServiceTests {

    private static final String API_PREFIX = "/api/v1";

    private AnalyticsOperationsService buildServiceWithServer(MockRestServiceServer[] serverOut) {

        RestClient.Builder builder = RestClient.builder().baseUrl("http://analytics-service");

        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        serverOut[0] = server;

        return new AnalyticsOperationsService(builder.build(), API_PREFIX);
    }

    @Test
    void getStats_returnsDeserializedStatsResult() {

        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        AnalyticsOperationsService service = buildServiceWithServer(holder);

        holder[0].expect(requestTo("http://analytics-service/api/v1/analytics/42"))
                .andExpect(method(GET))
                .andExpect(header("X-User-Id", "user-1"))
                .andRespond(withSuccess(
                        "{\"urlId\":42,\"totalClicks\":10,\"message\":\"ok\"}",
                        MediaType.APPLICATION_JSON
                ));

        AnalyticsOperationsService.StatsResult result = service.getStats(42L, "user-1");

        assertThat(result.urlId()).isEqualTo(42L);
        assertThat(result.totalClicks()).isEqualTo(10L);
        holder[0].verify();
    }

    @Test
    void getTopUrls_returnsDeserializedList() {

        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        AnalyticsOperationsService service = buildServiceWithServer(holder);

        holder[0].expect(requestTo("http://analytics-service/api/v1/analytics/top?limit=5"))
                .andExpect(method(GET))
                .andExpect(header("X-User-Id", "user-1"))
                .andRespond(withSuccess(
                        "[{\"urlId\":1,\"clickCount\":99},{\"urlId\":2,\"clickCount\":50}]",
                        MediaType.APPLICATION_JSON
                ));

        List<AnalyticsOperationsService.TopUrlResult> result = service.getTopUrls(5, "user-1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).urlId()).isEqualTo(1L);
        assertThat(result.get(0).clickCount()).isEqualTo(99L);
        holder[0].verify();
    }
}

