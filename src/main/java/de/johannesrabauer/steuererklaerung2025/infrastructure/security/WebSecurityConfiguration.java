package de.johannesrabauer.steuererklaerung2025.infrastructure.security;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebSecurityConfiguration implements WebMvcConfigurer {

    private final PassphraseGuardInterceptor passphraseGuardInterceptor;

    public WebSecurityConfiguration(PassphraseGuardInterceptor passphraseGuardInterceptor) {
        this.passphraseGuardInterceptor = passphraseGuardInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(passphraseGuardInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/error");
    }

    @Bean
    ServletListenerRegistrationBean<HttpSessionListener> passphraseLockingSessionListener(PassphraseService passphraseService) {
        HttpSessionListener listener = new HttpSessionListener() {
            @Override
            public void sessionDestroyed(HttpSessionEvent event) {
                if (Boolean.TRUE.equals(event.getSession().getAttribute(PassphraseSession.UNLOCKED_ATTRIBUTE))) {
                    passphraseService.lock();
                }
            }
        };
        return new ServletListenerRegistrationBean<>(listener);
    }
}
