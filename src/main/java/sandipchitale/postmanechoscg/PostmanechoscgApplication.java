package sandipchitale.postmanechoscg;

import io.netty.handler.timeout.ReadTimeoutException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.PredicateSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

@SpringBootApplication
public class PostmanechoscgApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostmanechoscgApplication.class, args);
    }

    /**
     * This converts ReadTimeoutException into HttpStatus.GATEWAY_TIMEOUT response.
     *
     */
    @Component
    @Order(-2)
    public static class GlobalErrorWebExceptionHandler extends AbstractErrorWebExceptionHandler {
        public GlobalErrorWebExceptionHandler(ErrorAttributes g,
                                              ApplicationContext applicationContext,
                                              ServerCodecConfigurer serverCodecConfigurer) {
            super(g, new WebProperties.Resources(), applicationContext);
            super.setMessageWriters(serverCodecConfigurer.getWriters());
            super.setMessageReaders(serverCodecConfigurer.getReaders());
        }

        @Override
        protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
            return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
        }

        private Mono<ServerResponse> renderErrorResponse (ServerRequest request) {
            Throwable throwable = getError(request);
            if (throwable instanceof ReadTimeoutException) {
                Map<String, Object> errorPropertiesMap = getErrorAttributes(request, ErrorAttributeOptions.defaults());
                errorPropertiesMap.put("status", HttpStatus.GATEWAY_TIMEOUT.value());
                errorPropertiesMap.put("error", HttpStatus.GATEWAY_TIMEOUT.getReasonPhrase());
                return ServerResponse.status(HttpStatus.GATEWAY_TIMEOUT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(errorPropertiesMap));
            }
            return Mono.error(throwable);
        }
    }


    /**
     * This customizes per-request timeout on the HttpClient.
     *
     */
    @Component
    public class PerRequestTimeoutNettyRoutingFilter extends NettyRoutingFilter {
        private static final String X_TIMEOUT_MILLIS = "X-TIMEOUT-MILLIS";

        public PerRequestTimeoutNettyRoutingFilter(HttpClient httpClient,
                                                   ObjectProvider<List<HttpHeadersFilter>> headersFiltersProvider,
                                                   HttpClientProperties properties) {
            super(httpClient, headersFiltersProvider, properties);
        }

        @Override
        protected HttpClient getHttpClient(Route route, ServerWebExchange exchange) {
            ServerHttpRequest request = exchange.getRequest();
            if (request.getHeaders().getFirst(X_TIMEOUT_MILLIS) != null) {
                // TODO: Cache
                return HttpClient.create().responseTimeout(
                        Duration.ofMillis(Long.parseLong(request.getHeaders().getFirst(X_TIMEOUT_MILLIS))));
            }
            // Use the default client
            return super.getHttpClient(route, exchange);
        }

//        Is this needed ?
//        @Override
//        public int getOrder() {
//            return super.getOrder() - 1;
//        }
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder
                .routes()
                .route((PredicateSpec predicateSpec) -> predicateSpec
                        .path("/**")
                        .filters((GatewayFilterSpec filterSpec) ->
                                filterSpec.filter((exchange, chain) -> {
                                    ServerHttpRequest request = exchange.getRequest();
                                    request = request.mutate().path("/" + request.getMethod().name().toLowerCase()).build();
                                    exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, request.getURI());
                                    return chain.filter(exchange.mutate().request(request).build());
                                })

                        )
                        .uri("https://postman-echo.com")
                )
                .build();
    }

}
