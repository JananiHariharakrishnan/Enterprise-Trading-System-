import 'dotenv/config';
import { createApp } from './app';
import { loadConfig } from './config';

export function startServer() {
  const config = loadConfig();
  const app = createApp(config);

  return app.listen(config.port, () => {
    process.stdout.write(
      `${JSON.stringify({
        level: 'info',
        event: 'service.started',
        service: 'auth-service',
        port: config.port,
        issuer: config.jwtIssuer
      })}\n`
    );
  });
}

if (require.main === module) {
  startServer();
}

