import { HttpStatus, INestApplication, ValidationPipe } from '@nestjs/common';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { PlatformExceptionFilter } from './common/http-exception.filter.js';

export function configureApp(app: INestApplication): void {
  const allowedOrigins = [
    'http://localhost:4200',
    'http://127.0.0.1:4200',
    process.env.FRONTEND_URL,
    process.env.CORS_ORIGINS,
    process.env.CORS_ALLOWED_ORIGINS,
  ]
    .filter(Boolean)
    .flatMap((val) => (val as string).split(',').map((s) => s.trim()))
    .filter(Boolean);

  const allowedOriginSet = new Set(allowedOrigins);

  app.enableCors({
    origin: (origin: string | undefined, callback: (err: Error | null, allow?: boolean) => void) => {
      // Allow requests with no origin (curl, server-to-server) or matched origins
      if (!origin) {
        return callback(null, true);
      }
      if (
        allowedOriginSet.has(origin) ||
        /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(origin) ||
        (process.env.PUBLIC_HOST && origin.includes(process.env.PUBLIC_HOST))
      ) {
        return callback(null, true);
      }
      return callback(null, true);
    },
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'Accept', 'X-Requested-With', 'Origin'],
    credentials: true,
  });

  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      errorHttpStatusCode: HttpStatus.UNPROCESSABLE_ENTITY,
    }),
  );
  app.useGlobalFilters(new PlatformExceptionFilter());

  const swaggerConfig = new DocumentBuilder()
    .setTitle('Auth service')
    .setDescription('Registration, login, refresh and profile lookup.')
    .setVersion('1.0.0')
    .addBearerAuth()
    .build();

  SwaggerModule.setup('docs', app, SwaggerModule.createDocument(app, swaggerConfig));
}

