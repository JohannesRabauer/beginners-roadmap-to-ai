package de.johannesrabauer.steuererklaerung2025.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PassphraseGuardInterceptor implements HandlerInterceptor {

    private final PassphraseService passphraseService;

    public PassphraseGuardInterceptor(PassphraseService passphraseService) {
        this.passphraseService = passphraseService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        PassphraseState state = passphraseService.getState();
        boolean sessionUnlocked = isSessionUnlocked(request.getSession(false));

        if (isPassphraseSetupPath(path)) {
            if (state == PassphraseState.UNINITIALIZED) {
                return true;
            }
            response.sendRedirect(state == PassphraseState.UNLOCKED && sessionUnlocked ? "/" : "/unlock");
            return false;
        }

        if (isUnlockPath(path)) {
            if (state == PassphraseState.UNINITIALIZED) {
                response.sendRedirect("/passphrase/setup");
                return false;
            }
            if (sessionUnlocked && state == PassphraseState.UNLOCKED) {
                response.sendRedirect("/");
                return false;
            }
            return true;
        }

        if (state == PassphraseState.UNINITIALIZED) {
            response.sendRedirect("/passphrase/setup");
            return false;
        }
        if (state != PassphraseState.UNLOCKED || !sessionUnlocked) {
            response.sendRedirect("/unlock");
            return false;
        }
        return true;
    }

    private boolean isPassphraseSetupPath(String path) {
        return path.equals("/passphrase/setup");
    }

    private boolean isUnlockPath(String path) {
        return path.equals("/unlock");
    }

    private boolean isSessionUnlocked(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(PassphraseSession.UNLOCKED_ATTRIBUTE));
    }
}
