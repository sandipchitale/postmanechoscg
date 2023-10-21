package sandipchitale.postmanechoscg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.PredicateSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.server.reactive.ServerHttpRequest;

import static org.springframework.cloud.gateway.support.RouteMetadataUtils.CONNECT_TIMEOUT_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

@SpringBootApplication
public class PostmanechoscgApplication {
    public static void main(String[] args) {
        SpringApplication.run(PostmanechoscgApplication.class, args);
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
                        .metadata(CONNECT_TIMEOUT_ATTR, 1000)
                        .uri("https://postman-echo.com")
                )
                .build();
    }

}
