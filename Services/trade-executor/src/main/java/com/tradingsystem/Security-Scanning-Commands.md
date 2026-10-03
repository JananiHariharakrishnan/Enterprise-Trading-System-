# Security Scan Commands

## 1. Semgrep — SAST

```powershell
semgrep scan --config=auto .
```

## 2. Gitleaks — Secret Detection

```powershell
gitleaks detect --source . --redact
```

```powershell
gitleaks detect --source . --redact --report-format json --report-path gitleaks-report.json
```

## 3. Trivy — Dependency Scanning

```powershell
trivy fs --scanners vuln .
```

```powershell
mvn dependency:tree -Dincludes=org.apache.tomcat.embed:tomcat-embed-core
```

```powershell
mvn dependency:tree -Dincludes=at.yawk.lz4:lz4-java
```

```powershell
trivy fs --scanners vuln .
```

## 4. SonarQube

```powershell
set SONAR_TOKEN=your-token
```

```powershell
mvn org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.host.url="http://localhost:9000" -Dsonar.projectKey="trade-executor" -Dsonar.token="%SONAR_TOKEN%"
```
