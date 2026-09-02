sequenceDiagram
actor Trader
participant API as Trade REST API
participant Domain as Trading Domain
participant Account as Account
participant Instrument as Instrument
participant Order as Order
participant Idempotency as Idempotency Checker

    Trader->>API: Submit BUY order
    API->>Domain: placeOrder(order)

    Domain->>Account: verify account
    Account-->>Domain: Active

    Domain->>Instrument: verify instrument
    Instrument-->>Domain: Tradable

    Domain->>Order: validate quantity
    Order-->>Domain: Valid

    Domain->>Order: validate price
    Order-->>Domain: Valid

    Domain->>Account: check affordability
    Account-->>Domain: Sufficient funds

    Domain->>Idempotency: check(key)
    Idempotency-->>Domain: Key available

    Domain-->>API: Order accepted
    API-->>Trader: Order accepted