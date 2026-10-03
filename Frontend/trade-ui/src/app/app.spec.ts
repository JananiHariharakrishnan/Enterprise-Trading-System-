import { TestBed } from '@angular/core/testing';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';
import { PLATFORM_AUTH_MIDDLEWARE } from './services/platform-auth-middleware.provider';
// Removed raw template/style imports; rely on TestBed compilation instead.

describe('App', () => {
  beforeEach(async () => {
    // Component resources are provided centrally in src/test-setup.ts

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        {
          provide: PLATFORM_AUTH_MIDDLEWARE,
          useValue: { pre: async (ctx: any) => ({ url: ctx.url, init: ctx.init }) }
        }
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render router outlet shell', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });
});
