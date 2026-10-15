import {
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  Output,
  ViewChild,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, catchError, debounce, firstValueFrom, map, of, switchMap, timer } from 'rxjs';
import { TokenStorageService } from '../../services/token-storage.service';
import { getTradeApiV1Url } from '../../config/api.config';

export type InstrumentListItem = {
  symbol: string;
  name: string;
  assetClass: string | null;
  currency: string | null;
};

type InstrumentLookupApiItem = InstrumentListItem;

type SearchRequest = { query: string; immediate: boolean };
type SearchOutcome =
  | { query: string; rows: InstrumentListItem[]; failed: false }
  | { query: string; rows: []; failed: true };

type TextPart = { text: string; match: boolean };

const RESULT_LIMIT = 75;
const TYPING_DEBOUNCE_MS = 200;
let nextInstanceId = 0;

@Component({
  selector: 'app-instrument-search',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './instrument-search.component.html',
  styleUrl: './instrument-search.component.css'
})
export class InstrumentSearchComponent {
  private readonly http = inject(HttpClient);
  private readonly tokenStorage = inject(TokenStorageService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** True while the parent is saving; rows can't be added until it finishes. */
  @Input() disabled = false;

  /** Symbols already in the open watchlist, so we can mark them instead of adding duplicates. */
  @Input() set existingSymbols(symbols: readonly string[] | null | undefined) {
    this.existing.set(new Set((symbols ?? []).map((s) => s.toUpperCase())));
  }

  /** Placeholder text for the search box. */
  @Input() placeholder = 'Add an instrument: search by ticker or company name';

  /** Verb shown on the highlighted row and in the keyboard hint ("Add", "Select", ...). */
  @Input() actionLabel = 'Add';

  /** Symbol that is currently chosen by the parent; its row is marked "Selected". */
  @Input() selectedSymbol: string | null = null;

  /** Close the dropdown and clear the box right after a pick (useful for "select one" flows). */
  @Input() closeOnSelect = false;

  /** Emits the picked instrument (name kept for backwards compatibility with the watchlist page). */
  @Output() instrumentAdded = new EventEmitter<InstrumentListItem>();

  @ViewChild('searchInput') private searchInput?: ElementRef<HTMLInputElement>;

  readonly listId = `instrument-listbox-${nextInstanceId++}`;

  readonly query = signal('');
  readonly isOpen = signal(false);
  readonly isLoading = signal(false);
  readonly hasError = signal(false);
  readonly results = signal<InstrumentListItem[]>([]);
  /** The query the current results belong to (drives highlighting). */
  readonly resultQuery = signal('');
  readonly activeIndex = signal(-1);

  private readonly existing = signal<Set<string>>(new Set());
  private readonly requests$ = new Subject<SearchRequest>();
  private hasLoadedOnce = false;

  /** Announced to screen readers as results change. */
  readonly statusText = computed(() => {
    if (this.isLoading()) return 'Searching';
    if (this.hasError()) return 'Search failed';
    const count = this.results().length;
    return count === 0 ? 'No results' : `${count} result${count === 1 ? '' : 's'}`;
  });

  constructor() {
    // switchMap cancels the previous HTTP call, so a slow old response can never
    // overwrite the results for what the user typed last.
    this.requests$
      .pipe(
        debounce(({ immediate }) => timer(immediate ? 0 : TYPING_DEBOUNCE_MS)),
        switchMap(({ query }) => this.fetchInstruments(query)),
        takeUntilDestroyed()
      )
      .subscribe((outcome) => this.applyOutcome(outcome));
  }

  // ---------- Events ----------

  onFocus(): void {
    this.isOpen.set(true);
    if (!this.hasLoadedOnce) {
      this.request('', true);
    }
  }

  onInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.query.set(value);
    this.isOpen.set(true);
    this.request(value.trim(), false);
  }

  onKeydown(event: KeyboardEvent): void {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        if (!this.isOpen()) {
          this.onFocus();
        } else {
          this.moveActive(1);
        }
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.moveActive(-1);
        break;
      case 'Enter': {
        const item = this.results()[this.activeIndex()];
        if (this.isOpen() && item) {
          event.preventDefault();
          this.select(item);
        }
        break;
      }
      case 'Escape':
        if (this.isOpen()) {
          event.stopPropagation();
          this.isOpen.set(false);
        }
        break;
      case 'Tab':
        this.isOpen.set(false);
        break;
    }
  }

  @HostListener('document:mousedown', ['$event'])
  onDocumentMouseDown(event: MouseEvent): void {
    if (this.isOpen() && !this.host.nativeElement.contains(event.target as Node)) {
      this.isOpen.set(false);
    }
  }

  // ---------- Actions ----------

  select(item: InstrumentListItem): void {
    if (this.disabled || this.isAdded(item)) return;
    this.instrumentAdded.emit(item);
    if (this.closeOnSelect) this.resetSearch();
  }

  /** Looks up one exact symbol, e.g. to prefill from a URL. Resolves to null if it isn't found. */
  async findBySymbol(symbol: string): Promise<InstrumentListItem | null> {
    const target = symbol.trim().toUpperCase();
    if (!target) return null;
    const outcome = await firstValueFrom(this.fetchInstruments(target));
    return outcome.rows.find((row) => row.symbol.toUpperCase() === target) ?? null;
  }

  clear(): void {
    this.query.set('');
    this.request('', true);
    this.searchInput?.nativeElement.focus();
  }

  retry(): void {
    this.request(this.query().trim(), true);
  }

  /** Called by the parent after an instrument has been added. */
  resetSearch(): void {
    this.query.set('');
    this.results.set([]);
    this.resultQuery.set('');
    this.activeIndex.set(-1);
    this.hasError.set(false);
    this.isLoading.set(false);
    this.isOpen.set(false);
    this.hasLoadedOnce = false;
  }

  onHover(index: number, item: InstrumentListItem): void {
    if (!this.isAdded(item)) this.activeIndex.set(index);
  }

  // ---------- Template helpers ----------

  isAdded(item: InstrumentListItem): boolean {
    return this.existing().has(item.symbol.toUpperCase());
  }

  isCurrent(item: InstrumentListItem): boolean {
    return !!this.selectedSymbol && item.symbol.toUpperCase() === this.selectedSymbol.toUpperCase();
  }

  optionId(index: number): string {
    return `${this.listId}-option-${index}`;
  }

  /** Splits text around the matched query so the template can wrap the match in <mark>. */
  highlight(text: string): TextPart[] {
    const q = this.resultQuery();
    const index = q ? text.toLowerCase().indexOf(q.toLowerCase()) : -1;
    if (index < 0) return [{ text, match: false }];
    return [
      { text: text.slice(0, index), match: false },
      { text: text.slice(index, index + q.length), match: true },
      { text: text.slice(index + q.length), match: false }
    ].filter((part) => part.text.length > 0);
  }

  formatAssetClass(value: string | null): string {
    const text = (value || 'equity').replace(/_/g, ' ').toLowerCase();
    return text.length <= 4 ? text.toUpperCase() : text.charAt(0).toUpperCase() + text.slice(1);
  }

  /** Stable hue per symbol, same idea as the badges in the watchlist table. */
  symbolHue(symbol: string): number {
    let hash = 0;
    for (const char of symbol) {
      hash = (hash * 31 + char.charCodeAt(0)) % 360;
    }
    return hash;
  }

  trackBySymbol(_: number, item: InstrumentListItem): string {
    return item.symbol;
  }

  trackByIndex(index: number): number {
    return index;
  }

  // ---------- Internals ----------

  private request(query: string, immediate: boolean): void {
    this.hasLoadedOnce = true;
    this.isLoading.set(true);
    this.requests$.next({ query, immediate });
  }

  private fetchInstruments(query: string) {
    const params: Record<string, string> = { limit: String(RESULT_LIMIT) };
    if (query) params['query'] = query;

    const token = this.tokenStorage.getAccessToken();
    const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {};

    return this.http
      .get<InstrumentLookupApiItem[]>(`${getTradeApiV1Url()}/instruments`, { params, headers })
      .pipe(
        map((items): SearchOutcome => ({ query, rows: this.normalise(items ?? [], query), failed: false })),
        catchError(() => of<SearchOutcome>({ query, rows: [], failed: true }))
      );
  }

  /** Drops junk rows, removes duplicates, and puts the best matches first. */
  private normalise(items: InstrumentLookupApiItem[], query: string): InstrumentListItem[] {
    const seen = new Set<string>();
    const rows = items
      .filter((item) => item?.symbol && item?.name)
      .filter((item) => {
        const key = item.symbol.toUpperCase();
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      })
      .map((item) => ({
        symbol: item.symbol,
        name: item.name,
        assetClass: item.assetClass,
        currency: item.currency
      }));

    if (!query) return rows;

    const q = query.toLowerCase();
    const rank = (row: InstrumentListItem): number => {
      const symbol = row.symbol.toLowerCase();
      const name = row.name.toLowerCase();
      if (symbol === q) return 0;
      if (symbol.startsWith(q)) return 1;
      if (name.startsWith(q)) return 2;
      if (symbol.includes(q)) return 3;
      return 4;
    };
    return rows
      .map((row, order) => ({ row, order, score: rank(row) }))
      .sort((a, b) => a.score - b.score || a.order - b.order)
      .map((entry) => entry.row);
  }

  private applyOutcome(outcome: SearchOutcome): void {
    this.isLoading.set(false);
    this.hasError.set(outcome.failed);
    this.results.set(outcome.rows);
    this.resultQuery.set(outcome.query);
    // Highlight the first addable row while searching so Enter adds the top match.
    this.activeIndex.set(outcome.query ? this.firstSelectable(outcome.rows) : -1);
  }

  private firstSelectable(rows: InstrumentListItem[]): number {
    return rows.findIndex((row) => !this.isAdded(row));
  }

  private moveActive(direction: 1 | -1): void {
    const rows = this.results();
    if (!rows.length) return;

    let index = this.activeIndex();
    if (index < 0) index = direction > 0 ? -1 : 0;

    for (let step = 0; step < rows.length; step++) {
      index = (index + direction + rows.length) % rows.length;
      if (!this.isAdded(rows[index])) {
        this.activeIndex.set(index);
        setTimeout(() =>
          this.host.nativeElement
            .querySelector(`#${this.optionId(index)}`)
            ?.scrollIntoView({ block: 'nearest' })
        );
        return;
      }
    }
  }
}
