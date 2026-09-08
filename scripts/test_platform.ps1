<#
.SYNOPSIS
    End-to-End Test Suite for Polyglot Trading Platform.
.DESCRIPTION
    Tests the complete trading lifecycle across PostgreSQL, C++ Engine, and Java Orchestrator:
    - Portfolio query
    - Risk Engine validations (insufficient cash / holdings)
    - Resting Limit Orders (PENDING)
    - Order Cancellation (CANCELLED)
    - Trade Execution and Matching (FILLED)
    - Real-time Trade Persistence via TradeConsumer
    - Balance and Position adjustments with cost-basis recalculation
#>

$baseUrl = "http://localhost:8080/api"
$buyerId  = "00000000-0000-0000-0000-000000000001"
$sellerId = "00000000-0000-0000-0000-000000000002"
$symbol   = "MSFT" # Use fresh symbol to have a clean order book

$totalTests = 0
$passedTests = 0

function Assert-Step {
    param(
        [string]$Name,
        [bool]$Condition,
        [string]$Detail = ""
    )
    $script:totalTests++
    if ($Condition) {
        $script:passedTests++
        Write-Host "  [PASS] $Name" -ForegroundColor Green
        if ($Detail) { Write-Host "         $Detail" -ForegroundColor DarkGray }
    } else {
        Write-Host "  [FAIL] $Name" -ForegroundColor Red
        if ($Detail) { Write-Host "         Error: $Detail" -ForegroundColor Yellow }
    }
}

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "  Polyglot Trading Platform - Automated E2E Test Suite  " -ForegroundColor Cyan
Write-Host "========================================================`n" -ForegroundColor Cyan

# 0. Seed test accounts in database
Write-Host "Setting up test accounts in database..." -ForegroundColor Yellow
try {
    $seedFile = Join-Path $PSScriptRoot "seed_test.sql"
    Get-Content $seedFile | docker exec -i trading-postgres psql -U trading_user -d trading_db | Out-Null
    Write-Host "Test accounts seeded successfully.`n" -ForegroundColor DarkGreen
} catch {
    Write-Host "Warning: Could not seed test accounts via docker: $_" -ForegroundColor DarkYellow
}

# ── 1. Check buyer initial portfolio ──────────────────────────────────────
Write-Host "1. Testing Portfolio Query..." -ForegroundColor Cyan
try {
    $buyerPortfolio = Invoke-RestMethod -Uri "$baseUrl/portfolio/$buyerId"
    Assert-Step -Name "Buyer portfolio retrieved" `
                -Condition ($buyerPortfolio.userId -eq $buyerId -and $buyerPortfolio.cash -ge 100000) `
                -Detail "Buyer Cash: $($buyerPortfolio.cash)"
} catch {
    Assert-Step -Name "Buyer portfolio retrieved" -Condition $false -Detail $_.Exception.Message
}

# ── 2. Risk Engine Rejection: Insufficient Cash ───────────────────────────
Write-Host "`n2. Testing Risk Engine Validation (Insufficient Cash)..." -ForegroundColor Cyan
try {
    $excessiveBuy = @{
        userId   = $buyerId
        symbol   = $symbol
        side     = "BUY"
        type     = "LIMIT"
        quantity = 10000
        price    = 500.00  # Requires $5,000,000 (exceeds $100,000)
    } | ConvertTo-Json

    $resp = Invoke-RestMethod -Uri "$baseUrl/orders" -Method Post -Body $excessiveBuy -ContentType "application/json"
    Assert-Step -Name "Excessive BUY order rejected" -Condition $false -Detail "Should have returned HTTP 422"
} catch {
    $isRejected = $_.Exception.Response.StatusCode.value__ -eq 422
    Assert-Step -Name "Excessive BUY order rejected by RiskEngine" `
                -Condition $isRejected `
                -Detail "HTTP 422 Unprocessable Entity returned as expected"
}

# ── 3. Risk Engine Rejection: Insufficient Holdings ───────────────────────
Write-Host "`n3. Testing Risk Engine Validation (Insufficient Holdings)..." -ForegroundColor Cyan
try {
    $unownedSell = @{
        userId   = $buyerId  # Buyer has 0 MSFT
        symbol   = $symbol
        side     = "SELL"
        type     = "LIMIT"
        quantity = 10
        price    = 250.00
    } | ConvertTo-Json

    $resp = Invoke-RestMethod -Uri "$baseUrl/orders" -Method Post -Body $unownedSell -ContentType "application/json"
    Assert-Step -Name "Unowned SELL order rejected" -Condition $false -Detail "Should have returned HTTP 422"
} catch {
    $isRejected = $_.Exception.Response.StatusCode.value__ -eq 422
    Assert-Step -Name "Unowned SELL order rejected by RiskEngine" `
                -Condition $isRejected `
                -Detail "HTTP 422 Unprocessable Entity returned as expected"
}

# ── 4. Place Resting Limit BUY Order ──────────────────────────────────────
Write-Host "`n4. Testing Resting Limit Order in C++ Engine..." -ForegroundColor Cyan
$restingOrderId = $null
try {
    $restingBuy = @{
        userId   = $buyerId
        symbol   = $symbol
        side     = "BUY"
        type     = "LIMIT"
        quantity = 10
        price    = 200.00
    } | ConvertTo-Json

    $resp = Invoke-RestMethod -Uri "$baseUrl/orders" -Method Post -Body $restingBuy -ContentType "application/json"
    $restingOrderId = $resp.orderId
    Assert-Step -Name "Resting BUY limit order placed in C++ book" `
                -Condition ($resp.status -eq "PENDING") `
                -Detail "Order ID: $restingOrderId, Status: $($resp.status)"
} catch {
    Assert-Step -Name "Resting BUY limit order placed" -Condition $false -Detail $_.Exception.Message
}

# ── 5. Cancel the Resting Order ───────────────────────────────────────────
Write-Host "`n5. Testing Order Cancellation..." -ForegroundColor Cyan
if ($restingOrderId) {
    try {
        $cancelResp = Invoke-RestMethod -Uri "$baseUrl/orders/$restingOrderId" -Method Delete
        
        # Verify status in database
        $orders = Invoke-RestMethod -Uri "$baseUrl/orders?userId=$buyerId"
        $cancelledOrder = $orders | Where-Object { $_.id -eq $restingOrderId }
        Assert-Step -Name "Resting order cancelled in engine & DB" `
                    -Condition ($cancelledOrder.status -eq "CANCELLED") `
                    -Detail "Status updated to CANCELLED"
    } catch {
        Assert-Step -Name "Order cancelled" -Condition $false -Detail $_.Exception.Message
    }
}

# ── 6. Match Orders: Buyer BUY + Seller SELL ──────────────────────────────
Write-Host "`n6. Testing Order Matching & Trade Execution..." -ForegroundColor Cyan
try {
    # Buyer places BUY 5 @ 250.00
    $matchBuy = @{
        userId   = $buyerId
        symbol   = $symbol
        side     = "BUY"
        type     = "LIMIT"
        quantity = 5
        price    = 250.00
    } | ConvertTo-Json
    $buyResp = Invoke-RestMethod -Uri "$baseUrl/orders" -Method Post -Body $matchBuy -ContentType "application/json"
    $buyerOrderId = $buyResp.orderId

    # Seller places SELL 5 @ 250.00 (Crosses / matches with resting BUY)
    $matchSell = @{
        userId   = $sellerId
        symbol   = $symbol
        side     = "SELL"
        type     = "LIMIT"
        quantity = 5
        price    = 250.00
    } | ConvertTo-Json
    $sellResp = Invoke-RestMethod -Uri "$baseUrl/orders" -Method Post -Body $matchSell -ContentType "application/json"
    $sellerOrderId = $sellResp.orderId

    Assert-Step -Name "Crossing orders submitted and matched" `
                -Condition ($sellResp.status -eq "FILLED") `
                -Detail "Seller Order: $sellerOrderId, Status: $($sellResp.status)"
} catch {
    Assert-Step -Name "Crossing orders submitted" -Condition $false -Detail $_.Exception.Message
}

# ── 7. Verify Trade Persistence in Database ───────────────────────────────
Write-Host "`n7. Testing Real-time Trade Feed Persistence..." -ForegroundColor Cyan
Start-Sleep -Milliseconds 800 # Brief delay for async stream processing
try {
    $trades = Invoke-RestMethod -Uri "$baseUrl/trades?symbol=$symbol"
    $executedTrade = $trades | Where-Object { $_.symbol -eq $symbol -and $_.quantity -eq 5 }
    Assert-Step -Name "Trade persisted by TradeConsumer" `
                -Condition ($null -ne $executedTrade) `
                -Detail "Trade ID: $($executedTrade.id), Qty: $($executedTrade.quantity) @ $($executedTrade.price)"
} catch {
    Assert-Step -Name "Trade persisted" -Condition $false -Detail $_.Exception.Message
}

# ── 8. Verify Updated Portfolios & Cost Basis ──────────────────────────────
Write-Host "`n8. Testing Portfolio Balance & Cost Basis Updates..." -ForegroundColor Cyan
try {
    $updatedBuyer = Invoke-RestMethod -Uri "$baseUrl/portfolio/$buyerId"
    $buyerPos = $updatedBuyer.positions | Where-Object { $_.symbol -eq $symbol }
    
    # Buyer should have 5 shares @ 250.00 avg cost and cash deducted by 5 * 250 = 1250
    $buyerCorrect = ($buyerPos.quantity -eq 5 -and $buyerPos.averageCost -eq 250)
    Assert-Step -Name "Buyer position credited with correct average cost" `
                -Condition $buyerCorrect `
                -Detail "Holdings: $($buyerPos.quantity) $symbol @ avg $($buyerPos.averageCost)"

    $updatedSeller = Invoke-RestMethod -Uri "$baseUrl/portfolio/$sellerId"
    $sellerPos = $updatedSeller.positions | Where-Object { $_.symbol -eq $symbol }
    # Seller started with 100, sold 5 -> remaining 95
    $sellerCorrect = ($sellerPos.quantity -eq 95)
    Assert-Step -Name "Seller position debited correctly" `
                -Condition $sellerCorrect `
                -Detail "Holdings: $($sellerPos.quantity) $symbol remaining"
} catch {
    Assert-Step -Name "Portfolio accounting verified" -Condition $false -Detail $_.Exception.Message
}

# ── Summary ───────────────────────────────────────────────────────────────
Write-Host "`n========================================================" -ForegroundColor Cyan
if ($passedTests -eq $totalTests) {
    Write-Host "  ALL $passedTests / $totalTests TESTS PASSED! Platform is working 100%." -ForegroundColor Green
} else {
    Write-Host "  $passedTests / $totalTests tests passed. Review failures above." -ForegroundColor Yellow
}
Write-Host "========================================================`n" -ForegroundColor Cyan
