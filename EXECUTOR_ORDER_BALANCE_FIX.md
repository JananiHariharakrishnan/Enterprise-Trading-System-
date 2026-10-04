# Order Balance Reversion Bug - Root Cause & Fix

## Problem Statement
When a user places an order while the executor service is offline:
1. Order enters `NEW` state
2. Buying power is deducted from the account (for BUY orders)
3. When executor comes back online and fills the order
4. **BUG**: Account balance and buying power revert to old values instead of reflecting the filled order

## Root Cause Analysis

### The Race Condition
The issue is an **optimistic locking conflict** that occurs during concurrent account updates:

#### Scenario Timeline:
```
T1: User places BUY order (5 shares @ $190)
    - Order status → NEW
    - Buying power reduced: 100000 → 99050 (order value = 950)
    - Account version incremented: v1 → v2

T2-Tn: Executor is OFFLINE (order sits in Kafka/queue)
    - User performs other operations (cancel orders, place orders, etc.)
    - Account version continues incrementing: v2 → v3 → v4...
    
Tn+1: Executor comes BACK ONLINE
    - Loads account from DB (has CURRENT version, e.g. v4)
    - Tries to update account using ORIGINAL version (v2)
    - Version check FAILS: expected v2, but found v4
    - Update affects 0 rows → OrderStateConflictException
    - Transaction ROLLS BACK → balance reverts to old values
```

### Code Location - The Bug
**File**: `Services/trade-api/src/main/java/com/tradingsystem/service/Impl/ExecutionProcessingService.java`

**Original problematic code** (lines ~170-195):
```java
// Load account at T0
Account account = orderDataMapper.findAccountById(order.getAccountId()); // version = v_current
// ... calculate newCashBalance, newBuyingPower ...

// Try to update with T0 version
int accountRows = orderDataMapper.updateAccountBalancesWithVersion(
        account.getAccountId(),
        newCashBalance,
        newBuyingPower,
        account.getVersion()  // ← Uses version at load time
);

if (accountRows != 1) {
    // FAILURE: Another transaction changed the account!
    throw new OrderStateConflictException();  // ← Causes rollback
}
```

**Root cause**: If ANY other transaction modifies the account between load and update, the optimistic lock fails.

---

## Solution Implemented

### Add Retry Logic with Exponential Backoff
Implement a retry mechanism similar to the existing `updatePositionWithRetry()` method:

```java
private void updateAccountWithRetry(Long accountId, BigDecimal cashBalance, 
                                    BigDecimal buyingPower, int initialVersion)
```

#### How it works:
1. **Attempt 1**: Try to update with the loaded version
2. **If fails** (version mismatch):
   - Wait 50ms (exponential backoff)
   - Reload the account from database (gets latest version)
   - Try again with new version
3. **Attempt 2**: Try with reloaded version
4. **If fails**:
   - Wait 100ms
   - Reload and retry
5. **Attempt 3**: Final attempt
6. **If all fail**: Throw `OptimisticLockException` (goes to DLT)

#### Key Changes:
1. **REJECTED order path** (line ~103):
   ```java
   updateAccountWithRetry(
       account.getAccountId(),
       account.getCashBalance(),
       restoredBuyingPower,
       account.getVersion()
   );
   ```

2. **FILLED order path** (line ~188):
   ```java
   updateAccountWithRetry(
       account.getAccountId(),
       newCashBalance,
       newBuyingPower,
       account.getVersion()
   );
   ```

3. **New helper method** (lines ~327-390):
   - Retries up to 3 times
   - Uses exponential backoff (50ms, 100ms)
   - Reloads account version on each retry
   - Proper error logging
   - Throws `OptimisticLockException` on permanent failure

---

## Technical Details

### Why This Fixes the Bug

**Before (Fails on version conflict)**:
- Load account: version = 4
- Try update with version = 2
- ❌ Fails → Rollback

**After (Retries with correct version)**:
- Load account: version = 4
- Try update with version = 4
- ✅ Succeeds → Commit

### Thread Safety
- Uses transactional boundary to ensure atomicity
- Optimistic locking prevents lost updates
- Retry logic handles concurrent modifications gracefully

### Logging
Enhanced logging for debugging:
```
"Account update failed due to version mismatch (concurrent update), attempt 1/3"
"Retrying account update in 50ms..."
"Re-read account version: 4 (was: 2)"
"Account update succeeded for account=1001 on attempt 2"
```

---

## Testing the Fix

### Test Scenario
1. Start the application
2. Place an order while executor is offline
   ```
   POST /api/v1/accounts/{id}/orders
   Body: {symbol: "AAPL", side: "BUY", quantity: 5, price: 190, orderType: "LIMIT"}
   ```
3. Verify order is NEW and buying power is reduced
4. Perform other account operations (cancel orders, etc.)
5. Bring executor online
6. Verify order transitions to FILLED
7. **Verify** ✅ account balance and buying power reflect the execution

### Expected Behavior
- Balance: 100000 - 950 = 99050 ✅
- Buying power: 100000 - 950 = 99050 ✅
- Order status: FILLED ✅

---

## Files Modified
- `Services/trade-api/src/main/java/com/tradingsystem/service/Impl/ExecutionProcessingService.java`
  - Added `updateAccountWithRetry()` method
  - Modified REJECTED path to use retry logic
  - Modified FILLED path to use retry logic

---

## Backward Compatibility
✅ Fully backward compatible
- No API changes
- No database schema changes
- Existing tests continue to pass
- New behavior only affects error handling (retries instead of immediate failure)

---

## Performance Impact
Minimal:
- Only triggers retry on concurrent updates (rare)
- Backoff times are small (50-100ms)
- Additional database reads only on retry

---

## Error Handling
If all retries fail:
- Exception: `OptimisticLockException`
- Message: "Failed to update account after 3 retries..."
- Behavior: Message sent to DLT (Dead Letter Topic) for manual review
- Transaction: Rolls back cleanly

---

## Additional Notes

### Similar Pattern for Positions
The position update already had this retry logic, which is why position updates worked correctly:
```java
private void updatePositionWithRetry(Long accountId, String symbol, int quantity, 
                                     java.math.BigDecimal averageCost, int initialVersion)
```

This fix brings account updates to the same level of robustness.

### Why It Wasn't Caught Earlier
- Only manifests when:
  1. Executor is offline
  2. Other transactions modify the account before executor returns
  3. Timing is tight (rare race condition)
- Test environment may not have this concurrency pattern

### Long-term Improvements
Consider for future sprints:
- Use pessimistic locking (SELECT ... FOR UPDATE) for critical operations
- Implement distributed tracing to monitor account update conflicts
- Add metrics/alerts for retry attempts
