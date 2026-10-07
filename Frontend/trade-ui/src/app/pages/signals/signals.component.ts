import { Component, OnInit, inject, signal, computed, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { SignalsService, TradeSignalResponse } from '../../services/signals.service';

type ActionFilter = 'ALL' | 'BUY' | 'SELL' | 'HOLD';
type ViewMode = 'table' | 'card';

@Component({
  selector: 'app-signals',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './signals.component.html',
  styleUrls: ['./signals.component.css']
})
export class SignalsComponent implements OnInit {
  private readonly signalsService = inject(SignalsService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);

  readonly signals = signal<TradeSignalResponse[]>([]);
  readonly isLoading = signal(true);
  readonly isRefreshing = signal(false);
  readonly error = signal<string | null>(null);
  readonly lastUpdated = signal<Date | null>(null);

  readonly filterAction = signal<ActionFilter>('ALL');
  readonly searchQuery = signal<string>('');
  readonly viewMode = signal<ViewMode>('table');

  readonly analyzeInput = signal<string>('');
  readonly isAnalyzing = signal(false);
  readonly toastMessage = signal<string | null>(null);
  readonly toastType = signal<'success' | 'error'>('success');
  private toastTimer: any = null;

  // Key performance & signal metrics
  readonly totalCount = computed(() => this.signals().length);
  readonly buyCount = computed(() => this.signals().filter(s => s.action === 'BUY').length);
  readonly sellCount = computed(() => this.signals().filter(s => s.action === 'SELL').length);
  readonly holdCount = computed(() => this.signals().filter(s => s.action === 'HOLD').length);
  readonly avgConfidence = computed(() => {
    const list = this.signals();
    if (list.length === 0) return 0;
    const sum = list.reduce((acc, s) => acc + (s.confidence || 0), 0);
    return Math.round((sum / list.length) * 100);
  });

  // Filtered & sorted signal list
  readonly filteredSignals = computed(() => {
    let list = this.signals();
    const action = this.filterAction();
    const query = this.searchQuery().trim().toLowerCase();

    if (action !== 'ALL') {
      list = list.filter(s => s.action === action);
    }

    if (query) {
      list = list.filter(s =>
        s.symbol.toLowerCase().includes(query) ||
        (s.reasoning && s.reasoning.toLowerCase().includes(query))
      );
    }

    return [...list].sort((a, b) => {
      const confDiff = (b.confidence || 0) - (a.confidence || 0);
      if (confDiff !== 0) return confDiff;
      return new Date(b.generatedAt).getTime() - new Date(a.generatedAt).getTime();
    });
  });

  ngOnInit(): void {
    this.fetchSignals(true);

    // Auto-refresh every 30 seconds
    interval(30_000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.fetchSignals(false);
      });
  }

  fetchSignals(initial = false): void {
    if (initial) {
      this.isLoading.set(true);
    } else {
      this.isRefreshing.set(true);
    }
    this.error.set(null);

    this.signalsService.getAll().subscribe({
      next: (data) => {
        this.signals.set(data || []);
        this.lastUpdated.set(new Date());
        this.isLoading.set(false);
        this.isRefreshing.set(false);
      },
      error: (err) => {
        console.error('Failed to load trade signals', err);
        this.error.set(err?.message || 'Failed to fetch AI signals');
        this.isLoading.set(false);
        this.isRefreshing.set(false);
      }
    });
  }

  refreshAllSignals(): void {
    this.isRefreshing.set(true);
    this.signalsService.refreshAll().subscribe({
      next: (data) => {
        this.signals.set(data || []);
        this.lastUpdated.set(new Date());
        this.isRefreshing.set(false);
        this.showToast('success', 'Refreshed quantitative intelligence models.');
      },
      error: (err) => {
        this.isRefreshing.set(false);
        this.showToast('error', 'Unable to refresh signals at this time.');
      }
    });
  }

  analyzeSymbol(customSymbol?: string): void {
    const symbolToAnalyze = (customSymbol || this.analyzeInput()).trim().toUpperCase();
    if (!symbolToAnalyze) return;

    this.isAnalyzing.set(true);
    this.signalsService.generate(symbolToAnalyze).subscribe({
      next: (freshSignal) => {
        this.isAnalyzing.set(false);
        this.analyzeInput.set('');
        // Update or prepend the signal in the local list
        const currentList = this.signals();
        const existingIndex = currentList.findIndex(s => s.symbol === freshSignal.symbol);
        if (existingIndex >= 0) {
          const updated = [...currentList];
          updated[existingIndex] = freshSignal;
          this.signals.set(updated);
        } else {
          this.signals.set([freshSignal, ...currentList]);
        }
        this.lastUpdated.set(new Date());
        this.showToast('success', `AI Signal computed for ${freshSignal.symbol}: ${freshSignal.action} (${Math.round(freshSignal.confidence * 100)}%)`);
      },
      error: (err) => {
        this.isAnalyzing.set(false);
        this.showToast('error', `Failed to analyze ${symbolToAnalyze}. Please check ticker symbol.`);
      }
    });
  }

  setFilter(action: ActionFilter): void {
    this.filterAction.set(action);
  }

  setViewMode(mode: ViewMode): void {
    this.viewMode.set(mode);
  }

  onSearch(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.searchQuery.set(input.value);
  }

  resetFilters(): void {
    this.filterAction.set('ALL');
    this.searchQuery.set('');
  }

  tradeSymbol(symbol: string, action: string): void {
    const side = action === 'SELL' ? 'SELL' : 'BUY';
    this.router.navigate(['/orders'], { queryParams: { symbol, side } });
  }

  formatConfidence(confidence: number): number {
    return Math.round((confidence || 0) * 100);
  }

  symbolHue(symbol: string): number {
    let hash = 0;
    for (const char of symbol) {
      hash = (hash * 31 + char.charCodeAt(0)) % 360;
    }
    return hash;
  }

  showToast(type: 'success' | 'error', message: string): void {
    this.toastType.set(type);
    this.toastMessage.set(message);
    if (this.toastTimer) clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => {
      this.toastMessage.set(null);
    }, 4000);
  }

  trackBySymbol(_: number, item: TradeSignalResponse): string {
    return item.symbol;
  }
}
