package com.wxn.chatbot.bot.client;

import com.wxn.chatbot.bot.config.BotConfiguration;
import com.wxn.chatbot.bot.dto.BotDtos;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class BotClient {

    private final BotConfiguration config;
    private final RestClient client;

    @Autowired
    public BotClient(BotConfiguration config) {
        this.config = config;
        // O uvicorn recusa o upgrade para HTTP/2 que o cliente do JDK tenta por padrão em http://.
        var http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(config.timeout());
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    BotClient(BotConfiguration config, RestClient client) {
        this.config = config;
        this.client = client;
    }

    public BotDtos.ReplyResponse reply(BotDtos.ReplyRequest request) {
        try {
            var response = client
                .post()
                .uri(config.replyUri())
                .header("X-Api-Key", config.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(BotDtos.ReplyResponse.class);
            if (response == null) {
                throw new BotUnavailableException("BOT_EMPTY_RESPONSE");
            }
            return response;
        } catch (RestClientResponseException error) {
            throw new BotUnavailableException("BOT_HTTP_" + error.getStatusCode().value());
        } catch (RestClientException error) {
            throw new BotUnavailableException(timedOut(error) ? "BOT_TIMEOUT" : "BOT_UNAVAILABLE");
        }
    }

    private static boolean timedOut(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            // Sem conexão é indisponibilidade, não lentidão do bot.
            if (cause instanceof HttpConnectTimeoutException) {
                return false;
            }
            if (cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
