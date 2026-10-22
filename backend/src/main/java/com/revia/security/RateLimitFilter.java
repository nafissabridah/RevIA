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
 * Limitation de débit (Bucket4j) : appels à l'IA (revues, assistant, outils) par minute et par utilisateur,
 * tentatives de connexion par minute et par adresse IP (anti force brute).
 * Le quota de 50 revues par jour est contrôlé séparément, en base, par QuotaService.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ReviaProperties.Quotas quotas;
    private final ObjectMapper mapper;
    private final BucketCache analyses = new BucketCache();
    private final BucketCache connexions = new BucketCache();
    private final BucketCache essais = new BucketCache();
    private final ReviaProperties.Essai essai;
    private final Bucket essaisDuJour;
    private final String enTeteIp;

    public RateLimitFilter(ReviaProperties props, ObjectMapper mapper) {
        this.quotas = props.quotas();
        this.mapper = mapper;
        this.essai = props.essai();
        this.enTeteIp = props.security().ipHeader().trim();
        this.essaisDuJour = Bucket.builder().addLimit(Bandwidth.builder().capacity(Math.max(1, essai.parJourGlobal()))
                .refillIntervally(Math.max(1, essai.parJourGlobal()), Duration.ofDays(1)).build()).build();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req, @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        Bucket bucket = null;
        String uri = req.getRequestURI();
        if ("POST".equals(req.getMethod()) && (uri.equals("/api/revues") || uri.startsWith("/api/assistant/"))) {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a != null && a.getPrincipal() instanceof UtilisateurConnecte u) {
                bucket = analyses.get("u" + u.id(), () -> nouveau(quotas.analysesParMinute()));
            }
        } else if ("POST".equals(req.getMethod()) && uri.equals("/api/essai")) {
            bucket = essais.get("ip" + ipClient(req), () -> nouveau(essai.parMinuteParIp()));
            if (bucket.getAvailableTokens() > 0 && !essaisDuJour.tryConsume(1)) {
                refuser(res, uri, 3600, "L'essai gratuit est très demandé aujourd'hui. Créez un compte gratuit pour continuer.");
                return;
            }
        } else if ("POST".equals(req.getMethod()) && (uri.equals("/api/auth/login") || uri.equals("/api/auth/register"))) {
            bucket = connexions.get("ip" + ipClient(req), () -> nouveau(quotas.connexionsParMinute()));
        }
        if (bucket != null) {
            ConsumptionProbe p = bucket.tryConsumeAndReturnRemaining(1);
            if (!p.isConsumed()) {
                long attente = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(p.getNanosToWaitForRefill()));
                refuser(res, uri, attente, "Trop de demandes en peu de temps. Réessayez dans " + attente + " s.");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    /**
     * IP du client : derrière Vercel, la connexion vient d'une IP de Vercel partagée par tous les visiteurs ;
     * sans {@code REVIA_IP_HEADER}, tout le monde partagerait le même seau (faux « trop de demandes »).
     * Premier élément de l'en-tête configuré, sinon adresse de la connexion.
     */
    String ipClient(HttpServletRequest req) {
        if (!enTeteIp.isEmpty()) {
            String v = req.getHeader(enTeteIp);
            if (v != null && !v.isBlank()) {
                return v.split(",", 2)[0].trim();
            }
        }
        return req.getRemoteAddr();
    }

    private void refuser(HttpServletResponse res, String uri, long attente, String message) throws IOException {
        res.setStatus(ErrorCode.DEBIT_LIMITE.status().value());
        res.setHeader("Retry-After", String.valueOf(attente));
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), ErreurApi.de(ErrorCode.DEBIT_LIMITE, message, uri, null));
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
