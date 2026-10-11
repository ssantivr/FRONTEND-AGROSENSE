package com.agrosense.frontend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Lets browsers keep the static files. Without a Cache-Control of their own, Spring Security marks every
 * answer "no-store", so the font, the photographs, the icon font, Chart.js and Leaflet were downloaded
 * again on every page.
 *
 * <p>Stylesheets and scripts are revalidated on each use (a cheap 304) because their names do not change
 * between versions; see spring.web.resources.cache in application.properties.
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    /** WebJar paths carry the library version, so a file at a given path never changes. */
    private static final CacheControl VERSIONED = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();
    private static final CacheControl MEDIA = CacheControl.maxAge(Duration.ofDays(30)).cachePublic();

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/")
                .setCacheControl(VERSIONED);
        registry.addResourceHandler("/fonts/**")
                .addResourceLocations("classpath:/static/fonts/")
                .setCacheControl(MEDIA);
        registry.addResourceHandler("/img/**")
                .addResourceLocations("classpath:/static/img/")
                .setCacheControl(MEDIA);
    }
}
