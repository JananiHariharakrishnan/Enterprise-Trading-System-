sequenceDiagram
actor Trader
participant API as Trade REST API
participant Engine as Trading Domain Engine
participant Account as Account
participant Instrument as Instrument
participant Position as Position
participant Idempotency as Idempotency Store
participant Order as Order

    Trader->>API: Submit order
    API->>Engine: placeOrder(order)

    Engine->>Account: find(accountId)

    alt Account does not exist
        Account-->>Engine: not found
        Engine-->>API: ACC-404
        API-->>Trader: Order rejected
    else Account exists

        Engine->>Account: isActive()

        alt Account is suspended/closed
            Account-->>Engine: inactive
            Engine-->>API: ACC-403
            API-->>Trader: Order rejected
        else Account active

            Engine->>Instrument: find(symbol)

            alt Instrument unknown
                Instrument-->>Engine: not found
                Engine-->>API: instrument error
                API-->>Trader: Order rejected
            else Instrument exists

                Engine->>Instrument: isTradable()

                alt Instrument not tradable
                    Instrument-->>Engine: false
                    Engine-->>API: instrument error
                    API-->>Trader: Order rejected
                else Instrument tradable

                    Engine->>Order: validateQuantity()

                    alt Invalid quantity
                        Order-->>Engine: invalid
                        Engine-->>API: ORD-400
                        API-->>Trader: Order rejected
                    else Quantity valid

                        Engine->>Order: validatePrice()

                        alt Invalid price
                            Order-->>Engine: invalid
                            Engine-->>API: ORD-400
                            API-->>Trader: Order rejected
                        else Price valid

                            alt BUY
                                Engine->>Account: canAfford(order)

                                alt Insufficient funds
                                    Account-->>Engine: false
                                    Engine-->>API: insufficient funds
                                    API-->>Trader: Order rejected
                                else Affordable
                                    Account-->>Engine: true
                                end
                            else SELL
                                Engine->>Position: hasQuantity(order)

                                alt Insufficient holdings
                                    Position-->>Engine: false
                                    Engine-->>API: insufficient holdings
                                    API-->>Trader: Order rejected
                                else Holdings sufficient
                                    Position-->>Engine: true
                                end
                            end

                            Engine->>Idempotency: exists(key)

                            alt Duplicate key
                                Idempotency-->>Engine: true
                                Engine-->>API: ORD-409
                                API-->>Trader: Order rejected
                            else Key available
                                Idempotency-->>Engine: false
                                Engine-->>API: Order accepted
                                API-->>Trader: Order accepted
                            end
                        end
                    end
                end
            end
        end
    end