package com.tradingsystem.service.Impl;

import com.tradingsystem.dto.response.BalanceResponse;
import com.tradingsystem.dto.response.HealthDependency;
import com.tradingsystem.dto.response.HealthResponse;
import com.tradingsystem.dto.response.LatestMarketPrice;
import com.tradingsystem.dto.response.MoverEntry;
import com.tradingsystem.dto.response.MoversResponse;
import com.tradingsystem.dto.response.PnlResponse;
import com.tradingsystem.dto.response.PortfolioSummaryResponse;
import com.tradingsystem.dto.response.PricedPositionResponse;
import com.tradingsystem.dto.response.SymbolPnlResponse;
import com.tradingsystem.exception.AccountAccessDeniedException;
import com.tradingsystem.exception.InvalidPortfolioInputException;
import com.tradingsystem.exception.PricingUnavailableException;
import com.tradingsystem.exception.TradingAuthenticationException;
import com.tradingsystem.mapper.PortfolioDataMapper;
import com.tradingsystem.model.Holding;
import com.tradingsystem.security.CurrentUser;
import com.tradingsystem.service.AccountService;
import com.tradingsystem.service.MarketDataService;
import com.tradingsystem.service.PnlCalculator;
import com.tradingsystem.service.PnlCalculator.SaleBooking;
import com.tradingsystem.service.PortfolioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Prices holdings from the in-memory quote cache (fed by the market-data topic), so a
 * portfolio request makes no Fauxnance call and costs no quota. Cash comes from
 * AccountService.getBalance, the layer that owns it.
 *
 * READ-ONLY. The only database access is PortfolioDataMapper (SELECT statements).
 */
@Service
public class PortfolioServiceImpl implements PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioServiceImpl.class);

    private static final String BASIS_TOTAL = "total";
    private static final String BASIS_DAY = "day";
    private static final int MAX_MOVERS = 50;

    private final PortfolioDataMapper portfolioDataMapper;
    private final AccountService accountService;
    private final MarketDataService marketDataService;
    private final CurrentUser currentUser;

    private final String baseCurrency;
    private final BigDecimal inrPerUsd;
    private final Duration maxQuoteAge;

    public PortfolioServiceImpl(
            PortfolioDataMapper portfolioDataMapper,
            AccountService accountService,
            MarketDataService marketDataService,
            CurrentUser currentUser,
            @Value("${portfolio.base-currency:USD}") String baseCurrency,
            @Value("${portfolio.inr-per-usd:96}") BigDecimal inrPerUsd,
            @Value("${portfolio.quote-max-age-seconds:300}") long quoteMaxAgeSeconds) {

        this.portfolioDataMapper = portfolioDataMapper;
        this.accountService = accountService;
        this.marketDataService = marketDataService;
        this.currentUser = currentUser;
        this.baseCurrency = baseCurrency;
        this.inrPerUsd = inrPerUsd;
        this.maxQuoteAge = Duration.ofSeconds(quoteMaxAgeSeconds);
    }

    /** One holding with whatever pricing could be attached to it. */
    private record Line(
            Holding holding,
            BigDecimal costBasis,
            BigDecimal lastPrice,
            BigDecimal marketValue,
            BigDecimal unrealisedPnl,
            Instant priceAsOf,
            boolean stale,
            LatestMarketPrice quote) {

        boolean priced() {
            return lastPrice != null;
        }
    }

    // =========================================================
    // SUMMARY
    // =========================================================

    @Override
    public PortfolioSummaryResponse getSummary(Long accountId) {
        BalanceResponse balance = authoriseAndLoadAccount(accountId);
        List<Line> lines = priceHoldings(accountId, null);

        BigDecimal costBasis = BigDecimal.ZERO;
        BigDecimal pricedCost = BigDecimal.ZERO;
        BigDecimal marketValue = BigDecimal.ZERO;
        BigDecimal unrealised = BigDecimal.ZERO;
        boolean partial = false;

        for (Line line : lines) {
            String currency = line.holding().getCurrency();
            costBasis = costBasis.add(toBase(line.costBasis(), currency));
            if (line.priced()) {
                pricedCost = pricedCost.add(toBase(line.costBasis(), currency));
                marketValue = marketValue.add(toBase(line.marketValue(), currency));
                unrealised = unrealised.add(toBase(line.unrealisedPnl(), currency));
            } else {
                partial = true;
            }
        }

        BigDecimal realised = sumRealised(accountId, null, null);
        BigDecimal cash = balance.getCashBalance() == null ? BigDecimal.ZERO : balance.getCashBalance();

        return new PortfolioSummaryResponse(
                accountId,
                baseCurrency,
                PnlCalculator.money(cash),
                PnlCalculator.money(marketValue),
                PnlCalculator.money(costBasis),
                PnlCalculator.money(unrealised),
                // against the cost of the positions that were actually priced
                PnlCalculator.percent(unrealised, pricedCost),
                PnlCalculator.money(realised),
                PnlCalculator.money(cash.add(marketValue)),
                lines.size(),
                partial,
                Instant.now().toString());
    }

    // =========================================================
    // PRICED POSITIONS
    // =========================================================

    @Override
    public List<PricedPositionResponse> getPricedPositions(Long accountId, String symbol) {
        authoriseAndLoadAccount(accountId);
        List<Line> lines = priceHoldings(accountId, symbol);

        List<PricedPositionResponse> result = new ArrayList<>();
        for (Line line : lines) {
            Holding h = line.holding();
            result.add(new PricedPositionResponse(
                    accountId,
                    h.getSymbol(),
                    h.getQuantity(),
                    h.getAverageCost(),
                    PnlCalculator.money(line.costBasis()),
                    line.lastPrice(),
                    PnlCalculator.money(line.marketValue()),
                    PnlCalculator.money(line.unrealisedPnl()),
                    line.priced() ? PnlCalculator.percent(line.unrealisedPnl(), line.costBasis()) : null,
                    h.getCurrency(),
                    line.priceAsOf() == null ? null : line.priceAsOf().toString(),
                    line.stale()));
        }
        return result;
    }

    // =========================================================
    // P&L
    // =========================================================

    @Override
    public PnlResponse getPnl(Long accountId, LocalDate from, LocalDate to, boolean bySymbol) {
        authorise(accountId);
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidPortfolioInputException();
        }
        accountService.getBalance(accountId); // ACC-404 / inactive account

        List<Line> lines = priceHoldings(accountId, null);

        BigDecimal unrealised = BigDecimal.ZERO;
        Map<String, BigDecimal> unrealisedBySymbol = new TreeMap<>();
        for (Line line : lines) {
            if (line.priced()) {
                BigDecimal inBase = toBase(line.unrealisedPnl(), line.holding().getCurrency());
                unrealised = unrealised.add(inBase);
                unrealisedBySymbol.merge(line.holding().getSymbol(), inBase, BigDecimal::add);
            }
        }

        BigDecimal realised = BigDecimal.ZERO;
        Map<String, BigDecimal> realisedBySymbol = new TreeMap<>();
        for (SaleBooking sale : PnlCalculator.replaySales(portfolioDataMapper.findFilledExecutions(accountId))) {
            if (inRange(sale, from, to)) {
                BigDecimal inBase = toBase(sale.realised(), sale.currency());
                realised = realised.add(inBase);
                realisedBySymbol.merge(sale.symbol(), inBase, BigDecimal::add);
            }
        }

        List<SymbolPnlResponse> breakdown = null;
        if (bySymbol) {
            breakdown = new ArrayList<>();
            TreeSet<String> symbols = new TreeSet<>(realisedBySymbol.keySet());
            symbols.addAll(unrealisedBySymbol.keySet());
            for (String symbol : symbols) {
                BigDecimal r = realisedBySymbol.getOrDefault(symbol, BigDecimal.ZERO);
                BigDecimal u = unrealisedBySymbol.getOrDefault(symbol, BigDecimal.ZERO);
                breakdown.add(new SymbolPnlResponse(
                        symbol,
                        PnlCalculator.money(r),
                        PnlCalculator.money(u),
                        PnlCalculator.money(r.add(u))));
            }
        }

        return new PnlResponse(
                accountId,
                baseCurrency,
                from == null ? null : from.toString(),
                to == null ? null : to.toString(),
                PnlCalculator.money(realised),
                PnlCalculator.money(unrealised),
                PnlCalculator.money(realised.add(unrealised)),
                breakdown,
                Instant.now().toString());
    }

    // =========================================================
    // MOVERS (extension)
    // =========================================================

    @Override
    public MoversResponse getMovers(Long accountId, int limit, String basis) {
        authorise(accountId);
        String normalisedBasis = basis == null ? BASIS_TOTAL : basis.trim().toLowerCase();
        if (!BASIS_TOTAL.equals(normalisedBasis) && !BASIS_DAY.equals(normalisedBasis)) {
            throw new InvalidPortfolioInputException();
        }
        int cap = Math.max(1, Math.min(limit, MAX_MOVERS));
        accountService.getBalance(accountId);

        List<Line> lines = priceHoldings(accountId, null);

        List<MoverEntry> entries = new ArrayList<>();
        boolean partial = false;
        for (Line line : lines) {
            if (!line.priced()) {
                partial = true;
                continue;
            }
            String currency = line.holding().getCurrency();
            BigDecimal pnl;
            BigDecimal pct;

            if (BASIS_DAY.equals(normalisedBasis)) {
                BigDecimal change = line.quote() == null ? null : line.quote().getChange();
                if (change == null) {
                    continue; // no day movement known for this symbol
                }
                pnl = toBase(change.multiply(BigDecimal.valueOf(line.holding().getQuantity())), currency);
                // computed from previous close, so it does not depend on how the feed scales changePercent
                pct = PnlCalculator.percent(change, line.quote().getPreviousClose());
            } else {
                pnl = toBase(line.unrealisedPnl(), currency);
                pct = PnlCalculator.percent(line.unrealisedPnl(), line.costBasis());
            }
            entries.add(new MoverEntry(line.holding().getSymbol(), PnlCalculator.money(pnl), pct, line.stale()));
        }

        List<MoverEntry> gainers = entries.stream()
                .filter(e -> e.pnl().signum() > 0)
                .sorted(Comparator.comparing(MoverEntry::pnl).reversed())
                .limit(cap)
                .toList();
        List<MoverEntry> losers = entries.stream()
                .filter(e -> e.pnl().signum() < 0)
                .sorted(Comparator.comparing(MoverEntry::pnl))
                .limit(cap)
                .toList();

        return new MoversResponse(accountId, baseCurrency, normalisedBasis, gainers, losers, partial,
                Instant.now().toString());
    }

    // =========================================================
    // HEALTH
    // =========================================================

    @Override
    public HealthResponse getHealth() {
        String postgres;
        try {
            postgres = portfolioDataMapper.ping() == 1 ? "ok" : "down";
        } catch (Exception e) {
            log.warn("Health: postgres probe failed: {}", e.getMessage());
            postgres = "down";
        }

        // Pricing health is judged from the quote cache the Fauxnance poller fills. Nothing is called,
        // so /health costs no quota. quotaRemaining is left null for the same reason.
        Instant now = Instant.now();
        Map<String, LatestMarketPrice> cache = marketDataService.getAllCachedPrices();
        boolean anyFresh = cache.values().stream().anyMatch(q -> !PnlCalculator.isStale(q, now, maxQuoteAge));
        String fauxnance = cache.isEmpty() ? "down" : (anyFresh ? "ok" : "degraded");

        String overall = "ok".equals(postgres) && "ok".equals(fauxnance) ? "ok" : "degraded";
        return new HealthResponse(
                overall,
                List.of(new HealthDependency("postgres", postgres, null),
                        new HealthDependency("fauxnance", fauxnance, null)),
                now.toString());
    }

    // =========================================================
    // INTERNALS
    // =========================================================

    /**
     * Token check FIRST, before any read. A customer probing another customer's
     * portfolio is an access-control failure (403 + log), not a lookup miss (404).
     */
    private void authorise(Long accountId) {
        Long tokenAccountId;
        try {
            tokenAccountId = currentUser.getAccountId();
        } catch (IllegalStateException e) {
            throw new TradingAuthenticationException();
        }
        if (tokenAccountId == null) {
            throw new TradingAuthenticationException();
        }
        if (accountId == null || !accountId.equals(tokenAccountId)) {
            log.warn("ACC-403: token for account {} attempted to read portfolio of account {}",
                    tokenAccountId, accountId);
            throw new AccountAccessDeniedException();
        }
    }

    /** Authorises, then confirms the account exists and is active, and returns its cash. */
    private BalanceResponse authoriseAndLoadAccount(Long accountId) {
        authorise(accountId);
        return accountService.getBalance(accountId);
    }

    /**
     * Loads open holdings and attaches a quote to each. Unpriced holdings stay in the list
     * (price fields null, stale = true). If there are holdings and none could be priced,
     * that is MKT-503.
     */
    private List<Line> priceHoldings(Long accountId, String symbolFilter) {
        Instant now = Instant.now();
        boolean filtered = symbolFilter != null && !symbolFilter.isBlank();

        List<Line> lines = new ArrayList<>();
        for (Holding holding : portfolioDataMapper.findHoldings(accountId)) {
            if (filtered && !holding.getSymbol().equalsIgnoreCase(symbolFilter.trim())) {
                continue;
            }
            BigDecimal cost = PnlCalculator.costBasis(holding.getQuantity(), holding.getAverageCost());
            LatestMarketPrice quote = marketDataService.getLatestPrice(holding.getSymbol());

            if (quote == null || quote.getPrice() == null) {
                lines.add(new Line(holding, cost, null, null, null, null, true, null));
                continue;
            }
            BigDecimal marketValue = PnlCalculator.marketValue(holding.getQuantity(), quote.getPrice());
            lines.add(new Line(
                    holding,
                    cost,
                    quote.getPrice(),
                    marketValue,
                    marketValue.subtract(cost),
                    PnlCalculator.parseQuoteTime(quote.getQuoteAsOf()),
                    PnlCalculator.isStale(quote, now, maxQuoteAge),
                    quote));
        }

        if (!lines.isEmpty() && lines.stream().noneMatch(Line::priced)) {
            log.warn("MKT-503: no held instrument for account {} could be priced", accountId);
            throw new PricingUnavailableException();
        }
        return lines;
    }

    private BigDecimal sumRealised(Long accountId, LocalDate from, LocalDate to) {
        BigDecimal total = BigDecimal.ZERO;
        for (SaleBooking sale : PnlCalculator.replaySales(portfolioDataMapper.findFilledExecutions(accountId))) {
            if (inRange(sale, from, to)) {
                total = total.add(toBase(sale.realised(), sale.currency()));
            }
        }
        return total;
    }

    private boolean inRange(SaleBooking sale, LocalDate from, LocalDate to) {
        LocalDate booked = sale.bookedAt().toLocalDate();
        return (from == null || !booked.isBefore(from)) && (to == null || !booked.isAfter(to));
    }

    private BigDecimal toBase(BigDecimal amount, String currency) {
        return PnlCalculator.toBase(amount, currency, baseCurrency, inrPerUsd);
    }
}
