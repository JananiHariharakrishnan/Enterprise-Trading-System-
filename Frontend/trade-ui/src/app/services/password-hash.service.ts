import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class PasswordHashService {
  /**
   * Encode a password using Base64 for transmission to the backend.
   * The password is encoded (not hashed) so the backend can decode and hash it with Argon2.
   * This prevents the plaintext password from appearing in network tabs.
   */
  encodePassword(password: string): string {
    // Convert string to UTF-8 bytes, then Base64 encode
    return btoa(unescape(encodeURIComponent(password)));
  }
}
