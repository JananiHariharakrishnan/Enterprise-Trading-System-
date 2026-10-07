import { TestBed } from '@angular/core/testing';
import { describe, expect, it, beforeEach } from 'vitest';
import { PasswordHashService } from './password-hash.service';

describe('PasswordHashService', () => {
  let service: PasswordHashService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [PasswordHashService] });
    service = TestBed.inject(PasswordHashService);
  });

  it('base64-encodes UTF-8 passwords without hashing them', () => {
    const samples = ['Secret123!', 'héllo 😀', ''];

    for (const password of samples) {
      expect(service.encodePassword(password)).toBe(btoa(unescape(encodeURIComponent(password))));
    }
  });
});
