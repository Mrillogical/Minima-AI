package com.minima-AI.ai.operations;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UrlOperationsServiceTests {

    private static final String API_PREFIX = "/api/v1";

    private UrlOperationsService buildServiceWithServer(MockRestServiceServer[] serverOut) {

        RestClient.Builder builder = RestClient.builder().baseUrl("http://url-service");

        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        serverOut[0] = server;

        return new UrlOperationsService(builder.build(), API_PREFIX);
    }

    @Test
    void shorten_postsOriginalUrl_returnsDeserializedResult() {

        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        UrlOperationsService service = buildServiceWithServer(holder);

        holder[0].expect(requestTo("http://url-service/api/v1/urls"))
                .andExpect(method(POST))
                .andExpect(header("X-User-Id", "user-1"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com"))
                .andRespond(withSuccess(
                        "{\"id\":1,\"slug\":\"abc123\",\"shortUrl\":\"http://short.ly/abc123\",\"originalUrl\":\"https://example.com\",\"userId\":\"u\",\"isCustom\":false,\"clickCount\":0,\"expiresAt\":null,\"createdAt\":null}",
                        MediaType.APPLICATION_JSON
                ));

        UrlOperationsService.ShortenResult result = service.shorten("https://example.com", "user-1");

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.slug()).isEqualTo("abc123");
        assertThat(result.shortUrl()).isEqualTo("http://short.ly/abc123");
        holder[0].verify();
    }

    @Test
    void getDetails_returnsDeserializedDetails() {

        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        UrlOperationsService service = buildServiceWithServer(holder);

        holder[0].expect(requestTo("http://url-service/api/v1/urls/slug/abc123"))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{\"id\":1,\"slug\":\"abc123\",\"originalUrl\":\"https://example.com\",\"shortUrl\":\"http://short.ly/abc123\",\"clickCount\":5}",
                        MediaType.APPLICATION_JSON
                ));

        UrlOperationsService.UrlDetails details = service.getDetails("abc123", "user-1");

        assertThat(details.clickCount()).isEqualTo(5L);
        holder[0].verify();
    }

    @Test
    void getDetailsById_returnsDeserializedDetails() {

        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        UrlOperationsService service = buildServiceWithServer(holder);

        holder[0].expect(requestTo("http://url-service/api/v1/urls/id/7"))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{\"id\":7,\"slug\":\"xyz\",\"originalUrl\":\"https://x.com\",\"shortUrl\":\"http://short.ly/xyz\",\"clickCount\":3}",
                        MediaType.APPLICATION_JSON
                ));

        UrlOperationsService.UrlDetails details = service.getDetailsById(7L, "user-1");

        assertThat(details.id()).isEqualTo(7L);
        holder[0].verify();
    }

    @Test
    void delete_sendsDeleteRequest() {

        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        UrlOperationsService service = buildServiceWithServer(holder);

        holder[0].expect(requestTo("http://url-service/api/v1/urls/slug/abc123"))
                .andExpect(method(DELETE))
                .andExpect(header("X-User-Id", "user-1"))
                .andRespond(withSuccess());

        service.delete("abc123", "user-1");

        holder[0].verify();
    }

    @Test
    void getAllUrlsForUser_flattensPageContent() {

        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        UrlOperationsService service = buildServiceWithServer(holder);

        holder[0].expect(requestTo("http://url-service/api/v1/urls?size=1000"))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{\"content\":[{\"slug\":\"a\",\"originalUrl\":\"https://a.com\",\"shortUrl\":\"http://s.ly/a\",\"clickCount\":1}]}",
                        MediaType.APPLICATION_JSON
                ));

        List<com.minima-AI.ai.mcp.resources.UrlResources.UrlResource> result =
                service.getAllUrlsForUser("user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).slug()).isEqualTo("a");
        holder[0].verify();
    }
}

