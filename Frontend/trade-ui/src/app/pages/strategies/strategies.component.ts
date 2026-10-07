import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { StrategyService, type StrategyResponse, type CreateStrategyRequest } from '../../services/strategy.service';

@Component({
  selector: 'app-strategies',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './strategies.component.html',
  styleUrls: ['./strategies.component.css']
})
export class StrategiesComponent implements OnInit {
  private strategyService = inject(StrategyService);

  readonly strategies = signal<StrategyResponse[]>([]);
  readonly loading = signal(false);
  readonly actionMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  // New Strategy Form Model
  symbol = 'AAPL';
  type: 'MA_CROSSOVER' | 'BOLLINGER_BANDS' = 'MA_CROSSOVER';
  quantity = 10;
  maxSpend = 50000;
  maxPositionSize = 200;

  // Simulate Tick Model
  simSymbol = 'AAPL';
  simPrice = 150.00;
  simStatus = signal<string | null>(null);

  ngOnInit(): void {
    this.loadStrategies();
  }

  loadStrategies(): void {
    this.loading.set(true);
    this.strategyService.getStrategies().subscribe({
      next: (list) => {
        this.strategies.set(list);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err?.message || 'Failed to load strategies');
        this.loading.set(false);
      }
    });
  }

  createStrategy(): void {
    if (!this.symbol || this.quantity <= 0) {
      this.errorMessage.set('Please provide a valid symbol and quantity');
      return;
    }

    const req: CreateStrategyRequest = {
      symbol: this.symbol.trim().toUpperCase(),
      type: this.type,
      quantity: this.quantity,
      maxSpend: this.maxSpend,
      maxPositionSize: this.maxPositionSize,
      fastPeriod: 5,
      slowPeriod: 20,
      bbPeriod: 20,
      bbStdDev: 2.0
    };

    this.strategyService.createStrategy(req).subscribe({
      next: (res) => {
        this.actionMessage.set(`Strategy ${res.strategyId} created for ${res.symbol}`);
        this.errorMessage.set(null);
        this.loadStrategies();
      },
      error: (err) => {
        this.errorMessage.set(err?.error?.message || 'Failed to create strategy');
      }
    });
  }

  toggleStrategy(strat: StrategyResponse): void {
    if (strat.enabled) {
      this.strategyService.disableStrategy(strat.strategyId).subscribe({
        next: () => {
          this.actionMessage.set(`Strategy ${strat.strategyId} immediately disabled`);
          this.loadStrategies();
        },
        error: (err) => this.errorMessage.set(err?.message || 'Failed to disable strategy')
      });
    } else {
      this.strategyService.enableStrategy(strat.strategyId).subscribe({
        next: () => {
          this.actionMessage.set(`Strategy ${strat.strategyId} enabled`);
          this.loadStrategies();
        },
        error: (err) => this.errorMessage.set(err?.message || 'Failed to enable strategy')
      });
    }
  }

  sendSimulatedTick(): void {
    if (!this.simSymbol || this.simPrice <= 0) {
      return;
    }

    this.strategyService.simulateTick(this.simSymbol.trim().toUpperCase(), this.simPrice).subscribe({
      next: () => {
        this.simStatus.set(`Tick pushed: ${this.simSymbol} @ $${this.simPrice}. Checked strategies.`);
        setTimeout(() => this.simStatus.set(null), 4000);
        this.loadStrategies();
      },
      error: (err) => {
        this.simStatus.set(`Error sending tick: ${err?.message || 'Unknown'}`);
      }
    });
  }
}

