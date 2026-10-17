package com.revia.security;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.revia.config.ReviaProperties;
import com.revia.error.ErreurApi;
import com.revia.error.ErrorCode;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limitation de débit (Bucket4j) : analyses par minute et par utilisateur,
 * tentatives de connexion par minute et par adresse IP (anti force brute).
 * Le quota de 50 revues par jour est contrôlé séparément, en base, par QuotaService.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ReviaProperties.Quotas quotas;
    private final ObjectMapper mapper;
    private final BucketCache analyses = new BucketCache();
    private final BucketCache connexions = new BucketCache();

    public RateLimitFilter(ReviaProperties props, ObjectMapper mapper) {
        this.quotas = props.quotas();
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req, @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        Bucket bucket = null;
        String uri = req.getRequestURI();
        if ("POST".equals(req.getMethod()) && uri.equals("/api/revues")) {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a != null && a.getPrincipal() instanceof UtilisateurConnecte u) {
                bucket = analyses.get("u" + u.id(), () -> nouveau(quotas.analysesParMinute()));
            }
        } else if ("POST".equals(req.getMethod()) && (uri.equals("/api/auth/login") || uri.equals("/api/auth/register"))) {
            bucket = connexions.get("ip" + req.getRemoteAddr(), () -> nouveau(quotas.connexionsParMinute()));
        }
        if (bucket != null) {
            ConsumptionProbe p = bucket.tryConsumeAndReturnRemaining(1);
            if (!p.isConsumed()) {
                long attente = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(p.getNanosToWaitForRefill()));
                res.setStatus(ErrorCode.DEBIT_LIMITE.status().value());
                res.setHeader("Retry-After", String.valueOf(attente));
                res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                res.setCharacterEncoding("UTF-8");
                mapper.writeValue(res.getOutputStream(), ErreurApi.de(ErrorCode.DEBIT_LIMITE,
                        "Trop de demandes en peu de temps. Réessayez dans " + attente + " s.", uri, null));
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private static Bucket nouveau(int parMinute) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(Math.max(1, parMinute)).refillGreedy(Math.max(1, parMinute), Duration.ofMinutes(1)).build())
                .build();
    }

    /** Petit cache LRU borné pour ne pas garder un seau par adresse IP indéfiniment. */
    private static final class BucketCache extends java.util.LinkedHashMap<String, Bucket> {
        private static final int MAX = 10_000;

        BucketCache() {
            super(256, 0.75f, true);
        }

        synchronized Bucket get(String cle, java.util.function.Supplier<Bucket> creer) {
            return computeIfAbsent(cle, k -> creer.get());
        }

        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, Bucket> eldest) {
            return size() > MAX;
        }
    }
}
