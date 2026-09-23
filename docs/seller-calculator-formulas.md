# Seller Calculator Formulas

All arithmetic uses decimal math. Percentage inputs are divided by 100. Intermediate division uses `HALF_UP`; displayed VND rounds to a whole đồng with dot grouping. These tools are estimates, not accounting or tax advice.

| Tool | Formula |
|---|---|
| Discounted price | `price × (1 − discount rate)` |
| Markup price | `cost × (1 + markup rate)` |
| Price for target margin | `cost ÷ (1 − margin rate)`; margin must be below 100% |
| Platform fee | `gross × fee rate` |
| Shipping per item | `total shipping ÷ quantity`; quantity must be positive |
| Quantity total | `quantity × unit price`; quantity cannot be negative |
| Profit estimate | `revenue − product cost − platform fee − shipping cost` |

Negative profit is valid. Negative price/cost inputs must be rejected by the calling UI unless a documented refund workflow explicitly permits them.
