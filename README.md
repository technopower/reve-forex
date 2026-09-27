# REVE FOREX V12 — Automatic Live Signal Scanner

V12 connects the V7 market-data foundation with the V11 indicator engine.

Features:
- Scheduled scanner
- Multi-symbol scanning
- Multi-timeframe configuration
- Provider adapter interface
- OHLC candle normalization
- EMA/RSI/MACD/Bollinger/ATR analysis
- Duplicate signal prevention
- Signal expiry
- Signal persistence (SQLite starter)
- Admin signal listing
- FCM notification hook
- Demo mode when provider is unavailable
- Scanner status endpoint
- Manual scan endpoint
- No automatic trade execution

IMPORTANT:
- This is a signal-analysis/notification system, not an auto-trading bot.
- Signals are informational and do not guarantee results.
- Demo mode is clearly labeled and must not be treated as live data.
- Production use requires reliable market-data licensing, persistent storage, monitoring, authentication, rate limiting and testing.
