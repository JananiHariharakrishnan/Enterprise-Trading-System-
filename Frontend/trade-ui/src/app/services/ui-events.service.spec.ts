import { TestBed } from '@angular/core/testing';
import { describe, expect, it, beforeEach } from 'vitest';
import { UiEventsService } from './ui-events.service';

describe('UiEventsService', () => {
  let service: UiEventsService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [UiEventsService] });
    service = TestBed.inject(UiEventsService);
  });

  it('notifies current subscribers when a refresh is emitted', () => {
    const seen: string[] = [];
    const subscription = service.onRefresh().subscribe(() => seen.push('tick'));

    expect(seen).toEqual([]);
    service.emitRefresh();
    service.emitRefresh();
    expect(seen).toEqual(['tick', 'tick']);

    subscription.unsubscribe();
    service.emitRefresh();
    expect(seen).toEqual(['tick', 'tick']);
  });

  it('delivers one refresh to every subscriber', () => {
    const first: number[] = [];
    const second: number[] = [];
    service.onRefresh().subscribe(() => first.push(1));
    service.onRefresh().subscribe(() => second.push(1));

    service.emitRefresh();

    expect(first).toEqual([1]);
    expect(second).toEqual([1]);
  });
});
