import express, { Request, Response } from 'express';
import { AuthServiceConfig } from './config';

function notImplemented(_request: Request, response: Response): void {
  response.status(501).json({
    errorCode: 'AUTH-501',
    message: 'Not implemented yet'
  });
}

export function createApp(_config: AuthServiceConfig) {
  const app = express();

  app.use(express.json());

  app.get('/health', (_request: Request, response: Response) => {
    response.status(200).json({
      status: 'ok',
      service: 'auth-service'
    });
  });

  app.post('/auth/login', notImplemented);
  app.post('/auth/signup', notImplemented);

  app.use((_request: Request, response: Response) => {
    response.status(404).json({
      errorCode: 'AUTH-404',
      message: 'Not found'
    });
  });

  return app;
}

