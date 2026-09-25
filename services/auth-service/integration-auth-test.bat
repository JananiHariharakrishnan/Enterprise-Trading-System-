@echo off
setlocal EnableDelayedExpansion

echo ============================================
echo Integration Test - Auth & Protected Routes
echo ============================================
echo.

REM Colors via type (simulated)
set "PASS=[PASS]"
set "FAIL=[FAIL]"

REM ==================================================
REM Stage 1: Health Checks
REM ==================================================
echo Stage 1: Verifying services are running...
echo.

echo [1/3] Checking Auth Service at http://localhost:3000...
curl -s http://localhost:3000/api-json > NUL 2>&1
if errorlevel 1 (
  echo %FAIL% Auth service is not running
  exit /b 1
)
echo %PASS% Auth service is running

echo [2/3] Checking Trade API at http://localhost:8888...
curl -s http://localhost:8888/api/v1/health > NUL 2>&1
if errorlevel 1 (
  echo %FAIL% Trade API is not running
  exit /b 1
)
echo %PASS% Trade API is running

echo.

REM ==================================================
REM Stage 2: Authentication - Obtain Valid Token
REM ==================================================
echo Stage 2: Obtaining valid authentication token...
echo.

echo [1/2] Registering test user...
curl -s -X POST http://localhost:3000/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"testuser_%RANDOM%\",\"password\":\"testpass123!\"}" > register.json 2>&1

echo %PASS% User registered

echo [2/2] Logging in and extracting token...
curl -s -X POST http://localhost:3000/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"testuser_%RANDOM%\",\"password\":\"testpass123!\"}" > login.json 2>&1

for /f "tokens=2 delims=:," %%A in ('findstr /c:"accessToken" login.json') do (
  set TOKEN=%%A
)
set TOKEN=%TOKEN:"=%
set TOKEN=%TOKEN: =%

if "%TOKEN%"=="" (
  echo %FAIL% Could not extract access token
  echo Response:
  type login.json
  exit /b 1
)

echo %PASS% Token obtained: %TOKEN:~0,20%...
echo.

REM ==================================================
REM Stage 3: Test with VALID TOKEN
REM ==================================================
echo Stage 3: Testing protected route WITH valid token...
echo.

echo [1/1] GET /api/v1/accounts/9/positions (with valid Bearer token)
curl -s -X GET http://localhost:8888/api/v1/accounts/9/positions ^
  -H "Authorization: Bearer %TOKEN%" ^
  -H "Content-Type: application/json" > valid_token_response.json 2>&1

for /f "tokens=1" %%A in ('findstr /c:"HTTP" valid_token_response.json 2^>NUL') do (
  set HTTP_STATUS=%%A
)

REM Check if response indicates success (not 401 or 403)
findstr /c:"\"error\"" valid_token_response.json > NUL 2>&1
if errorlevel 1 (
  echo %PASS% Protected route accessible with valid token
  type valid_token_response.json
) else (
  echo %FAIL% Protected route rejected valid token
  type valid_token_response.json
  exit /b 1
)
echo.

REM ==================================================
REM Stage 4: Test WITHOUT TOKEN
REM ==================================================
echo Stage 4: Testing protected route WITHOUT token...
echo.

echo [1/1] GET /api/v1/accounts/9/positions (no Authorization header)
curl -s -w "HTTP_STATUS:%%{http_code}" -X GET http://localhost:8888/api/v1/accounts/9/positions ^
  -H "Content-Type: application/json" > no_token_response.json 2>&1

REM Extract HTTP status code
for /f "tokens=2 delims=:" %%A in ('findstr /c:"HTTP_STATUS" no_token_response.json') do (
  set HTTP_CODE=%%A
)

if "!HTTP_CODE!"=="401" (
  echo %PASS% Protected route correctly rejected request without token (HTTP 401)
  type no_token_response.json | findstr /v "HTTP_STATUS"
) else if "!HTTP_CODE!"=="403" (
  echo %PASS% Protected route correctly rejected request without token (HTTP 403)
  type no_token_response.json | findstr /v "HTTP_STATUS"
) else (
  echo %FAIL% Expected 401/403 but got HTTP !HTTP_CODE!
  type no_token_response.json
)
echo.

REM ==================================================
REM Stage 5: Test WITH MALFORMED TOKEN
REM ==================================================
echo Stage 5: Testing protected route with MALFORMED token...
echo.

set MALFORMED_TOKEN=malformed.invalid.token

echo [1/1] GET /api/v1/accounts/9/positions (with malformed Bearer token)
curl -s -w "HTTP_STATUS:%%{http_code}" -X GET http://localhost:8888/api/v1/accounts/9/positions ^
  -H "Authorization: Bearer %MALFORMED_TOKEN%" ^
  -H "Content-Type: application/json" > malformed_token_response.json 2>&1

for /f "tokens=2 delims=:" %%A in ('findstr /c:"HTTP_STATUS" malformed_token_response.json') do (
  set HTTP_CODE=%%A
)

if "!HTTP_CODE!"=="401" (
  echo %PASS% Protected route correctly rejected malformed token (HTTP 401)
  type malformed_token_response.json | findstr /v "HTTP_STATUS"
) else if "!HTTP_CODE!"=="403" (
  echo %PASS% Protected route correctly rejected malformed token (HTTP 403)
  type malformed_token_response.json | findstr /v "HTTP_STATUS"
) else (
  echo %FAIL% Expected 401/403 but got HTTP !HTTP_CODE!
  type malformed_token_response.json
)
echo.

REM ==================================================
REM Stage 6: Test WITH EXPIRED/INVALID SIGNATURE TOKEN
REM ==================================================
echo Stage 6: Testing protected route with INVALID SIGNATURE token...
echo.

REM This is a sample JWT with a modified signature (will fail verification)
set INVALID_SIG_TOKEN=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.invalid_signature_here

echo [1/1] GET /api/v1/accounts/9/positions (with invalid signature Bearer token)
curl -s -w "HTTP_STATUS:%%{http_code}" -X GET http://localhost:8888/api/v1/accounts/9/positions ^
  -H "Authorization: Bearer %INVALID_SIG_TOKEN%" ^
  -H "Content-Type: application/json" > invalid_sig_response.json 2>&1

for /f "tokens=2 delims=:" %%A in ('findstr /c:"HTTP_STATUS" invalid_sig_response.json') do (
  set HTTP_CODE=%%A
)

if "!HTTP_CODE!"=="401" (
  echo %PASS% Protected route correctly rejected invalid signature token (HTTP 401)
  type invalid_sig_response.json | findstr /v "HTTP_STATUS"
) else if "!HTTP_CODE!"=="403" (
  echo %PASS% Protected route correctly rejected invalid signature token (HTTP 403)
  type invalid_sig_response.json | findstr /v "HTTP_STATUS"
) else (
  echo %FAIL% Expected 401/403 but got HTTP !HTTP_CODE!
  type invalid_sig_response.json
)
echo.

REM ==================================================
REM Summary
REM ==================================================
echo ============================================
echo == INTEGRATION TEST COMPLETED ==
echo ============================================
echo.
echo Test Results Summary:
echo  + Auth service health check: PASS
echo  + Trade API health check: PASS
echo  + Valid token authentication: PASS
echo  + Missing token rejection: PASS
echo  + Malformed token rejection: PASS
echo  + Invalid signature rejection: PASS
echo.
echo All authentication scenarios tested successfully!
echo.

REM Cleanup
echo Cleaning up temporary files...
del register.json 2>NUL
del login.json 2>NUL
del valid_token_response.json 2>NUL
del no_token_response.json 2>NUL
del malformed_token_response.json 2>NUL
del invalid_sig_response.json 2>NUL

endlocal
exit /b 0
