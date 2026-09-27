# V12 Scanner Rules

Default symbols:
- XAU/USD
- EUR/USD
- GBP/USD
- USD/JPY

Default timeframes:
- 15min
- 1h

The scanner uses the V11 four-filter framework:
- EMA20 vs EMA50
- RSI14
- MACD histogram
- Bollinger midpoint

A signal is created only when the score reaches the configured threshold.

Duplicate prevention:
- An active signal with the same symbol/timeframe/direction is not duplicated until expiry.

Signal expiry:
- 15min: 30 minutes
- 1h: 120 minutes

Change these rules only after testing.
