import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class UiEventsService {
  private refreshSubject = new Subject<void>();

  emitRefresh(): void {
    this.refreshSubject.next();
  }

  onRefresh() {
    return this.refreshSubject.asObservable();
  }
}
