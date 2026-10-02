import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';

@Component({
  selector: 'app-auth-layout',
  standalone: true,
  imports: [RouterLink, RouterOutlet],
  templateUrl: './auth-layout.component.html',
  styleUrls: ['./auth-layout.component.css']
})
export class AuthLayoutComponent {
  private readonly router = inject(Router);

  /** true on /register, false on /login. Updates on every navigation. */
  readonly isRegister = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => this.isRegisterUrl(event.urlAfterRedirects)),
      startWith(this.isRegisterUrl(this.router.routerState.snapshot.url))
    ),
    { initialValue: false }
  );

  private isRegisterUrl(url: string): boolean {
    return url.split(/[?#]/)[0].startsWith('/register');
  }
}
