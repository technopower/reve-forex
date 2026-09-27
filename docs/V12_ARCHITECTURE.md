# V12 Architecture

V7 Market Data
      |
      v
Candle Collector
      |
      v
V11 Indicator Engine
      |
      v
V12 Scanner Scheduler
      |
      +----> Duplicate/Expiry Check
      |
      v
Signal Database
      |
      +----> Admin Dashboard
      |
      +----> V10 Notification Service
                       |
                       v
                  Android FCM

No order execution is included.
