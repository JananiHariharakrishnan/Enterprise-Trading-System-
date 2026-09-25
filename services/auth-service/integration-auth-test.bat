@echo off
setlocal EnableDelayedExpansion

echo ============================================
echo Integration Test - Auth and Protected Routes
echo ============================================
echo.

REM ==================================================
REM Stage 1: Health Checks
REM ==================================================
echo Stage 1: Verifying services are running...
echo.

echo [1/3] Checking Auth Service at http://localhost:3000...
curl -s http://localhost:3000/auth/me > NUL 2>&1
if errorlevel 1 (
  echo [FAIL] Auth service is not running
  exit /b 1
)
echo [PASS] Auth service is running

echo [2/3] Checking Trade API at http://localhost:8888...
curl -s http://localhost:8888/api/v1/health > NUL 2>&1
if errorlevel 1 (
  echo [FAIL] Trade API is not running
  exit /b 1
)
echo [PASS] Trade API is running

echo.

REM ==================================================
REM Stage 2: Authentication - Obtain Valid Token
REM ==================================================
echo Stage 2: Obtaining valid authentication token...
echo.

set USERNAME=ktk
set PASSWORD=password1234$*

echo [1/1] Logging in with existing user to get access token...
curl -s -X POST http://localhost:3000/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"%USERNAME%\",\"password\":\"%PASSWORD%\"}" > login.json 2>&1

echo Response:
type login.json
echo.

REM Extract token using PowerShell for more reliable parsing
for /f "tokens=*" %%A in ('powershell -Command "(Get-Content login.json | ConvertFrom-Json).accessToken" 2^>$null') do (
  set TOKEN=%%A
)

if "!TOKEN!"=="" (
  echo [FAIL] Could not extract access token
  echo Response:
  type login.json
  exit /b 1
)

echo [PASS] Token obtained successfully
echo.

REM ==================================================
REM Stage 3: Test with VALID TOKEN
REM ==================================================
echo Stage 3: Testing protected route WITH valid token...
echo.

echo [1/1] GET /api/v1/accounts/9/positions (with valid Bearer token)
curl -s -X GET http://localhost:8888/api/v1/accounts/9/positions ^
  -H "Authorization: Bearer !TOKEN!" ^
  -H "Content-Type: application/json" > valid_token_response.json 2>&1

echo [PASS] Request completed
echo Response:
type valid_token_response.json
echo.

REM ==================================================
REM Stage 4: Test WITHOUT TOKEN
REM ==================================================
echo Stage 4: Testing protected route WITHOUT token...
echo.

echo [1/1] GET /api/v1/accounts/9/positions (no Authorization header)
curl -s -w "\nHTTP_STATUS:%%{http_code}" -X GET http://localhost:8888/api/v1/accounts/9/positions ^
  -H "Content-Type: application/json" > no_token_response.json 2>&1

echo [PASS] Request completed
echo Response (should show 401 or 403):
type no_token_response.json
echo.

REM ==================================================
REM Stage 5: Test WITH MALFORMED TOKEN
REM ==================================================
echo Stage 5: Testing protected route with MALFORMED token...
echo.

set MALFORMED_TOKEN=malformed.invalid.token

echo [1/1] GET /api/v1/accounts/9/positions (with malformed Bearer token)
curl -s -w "\nHTTP_STATUS:%%{http_code}" -X GET http://localhost:8888/api/v1/accounts/9/positions ^
  -H "Authorization: Bearer %MALFORMED_TOKEN%" ^
  -H "Content-Type: application/json" > malformed_token_response.json 2>&1

echo [PASS] Request completed
echo Response (should show 401 or 403):
type malformed_token_response.json
echo.

REM ==================================================
REM Stage 6: Test WITH INVALID SIGNATURE TOKEN
REM ==================================================
echo Stage 6: Testing protected route with INVALID SIGNATURE token...
echo.

set INVALID_SIG_TOKEN=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.invalid_signature_here

echo [1/1] GET /api/v1/accounts/9/positions (with invalid signature Bearer token)
curl -s -w "\nHTTP_STATUS:%%{http_code}" -X GET http://localhost:8888/api/v1/accounts/9/positions ^
  -H "Authorization: Bearer %INVALID_SIG_TOKEN%" ^
  -H "Content-Type: application/json" > invalid_sig_response.json 2>&1

echo [PASS] Request completed
echo Response (should show 401 or 403):
type invalid_sig_response.json
echo.

REM ==================================================
REM Summary
REM ==================================================
echo ============================================
echo == INTEGRATION TEST COMPLETED ==
echo ============================================
echo.
echo All tests executed. Check responses above for:
echo  + Valid token: Should contain data (HTTP 200)
echo  + No token: Should show HTTP 401 or 403
echo  + Malformed token: Should show HTTP 401 or 403
echo  + Invalid signature: Should show HTTP 401 or 403
echo.

REM Cleanup
echo Cleaning up temporary files...
del login.json 2>NUL
del valid_token_response.json 2>NUL
del no_token_response.json 2>NUL
del malformed_token_response.json 2>NUL
del invalid_sig_response.json 2>NUL

echo Done!
endlocal
exit /b 0
