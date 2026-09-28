# TradeUi

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 21.2.24.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Authentication flow

The app now includes a sign-in page at `/login` and a guarded dashboard at `/dashboard`.

- Access and refresh tokens are stored in `sessionStorage`.
- Sign-out is available from the top header and clears session state.
- Stable test identifiers are provided for the auth journey (`username-input`, `password-input`, `sign-in-button`, `sign-out-button`).

## Running end-to-end tests

Playwright tests are configured in this project and can boot both the UI and Auth service.

Install browser binaries once:

```bash
npx playwright install chromium
```

Run e2e tests:

```bash
npm run e2e
```

Run headed mode:

```bash
npm run e2e:headed
```

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
