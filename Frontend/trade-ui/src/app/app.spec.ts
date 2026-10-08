import { TestBed } from '@angular/core/testing';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';
import { PLATFORM_AUTH_MIDDLEWARE } from './services/platform-auth-middleware.provider';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        {
          provide: PLATFORM_AUTH_MIDDLEWARE,
          useValue: {
            pre: async (ctx: any) => ({ url: ctx.url, init: ctx.init }),
            post: async (ctx: any) => ({ response: ctx.response })
          }
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

  it('loads the lazy alert, signal, wallet, and admin pages', async () => {
    const loaders = routes.flatMap((route) => (route.loadComponent ? [route.loadComponent] : []));

    const components = await Promise.all(loaders.map((load) => load()));

    expect(components).toHaveLength(4);
    expect(components.every((component) => component)).toBe(true);
  });
});

