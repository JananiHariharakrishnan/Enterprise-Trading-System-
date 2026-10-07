import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { StrategiesComponent } from './strategies.component';
import { StrategyService, type StrategyResponse } from '../../services/strategy.service';

const strategy: StrategyResponse = {
  strategyId: 's-1',
  accountId: 1,
  symbol: 'AAPL',
  type: 'MA_CROSSOVER',
  quantity: 10,
  enabled: true,
  currentSpend: 0,
  maxSpend: 50000,
  maxPositionSize: 200,
  consecutiveFailures: 0,
  maxConsecutiveFailures: 3
};

describe('StrategiesComponent', () => {
  let fixture: ComponentFixture<StrategiesComponent>;
  let component: StrategiesComponent;
  let api: {
    getStrategies: ReturnType<typeof vi.fn>;
    createStrategy: ReturnType<typeof vi.fn>;
    enableStrategy: ReturnType<typeof vi.fn>;
    disableStrategy: ReturnType<typeof vi.fn>;
    deleteStrategy: ReturnType<typeof vi.fn>;
    simulateTick: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    api = {
      getStrategies: vi.fn().mockReturnValue(of([strategy])),
      createStrategy: vi.fn().mockReturnValue(of({ ...strategy, strategyId: 's-2' })),
      enableStrategy: vi.fn().mockReturnValue(of({})),
      disableStrategy: vi.fn().mockReturnValue(of({})),
      deleteStrategy: vi.fn().mockReturnValue(of({})),
      simulateTick: vi.fn().mockReturnValue(of({ results: ['bought'] }))
    };
    await TestBed.configureTestingModule({
      imports: [StrategiesComponent],
      providers: [
        provideRouter([]),
        { provide: StrategyService, useValue: api }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(StrategiesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads strategies on init', () => {
    expect(component.strategies()).toEqual([strategy]);
    expect(component.loading()).toBe(false);
  });

  it('records a load error', () => {
    api.getStrategies.mockReturnValueOnce(throwError(() => new Error('offline')));
    component.loadStrategies();
    expect(component.errorMessage()).toBe('offline');
    expect(component.loading()).toBe(false);
  });

  it('rejects an incomplete strategy and creates a valid one', () => {
    component.symbol = ' ';
    component.quantity = 0;
    component.createStrategy();
    expect(api.createStrategy).not.toHaveBeenCalled();

    component.symbol = ' msft ';
    component.quantity = 2;
    component.createStrategy();
    expect(api.createStrategy).toHaveBeenCalledWith(expect.objectContaining({ symbol: 'MSFT', quantity: 2 }));
    expect(component.actionMessage()).toContain('s-2');
  });

  it('shows the create error from the response body', () => {
    api.createStrategy.mockReturnValueOnce(throwError(() => ({ error: { message: 'duplicate' } })));
    component.createStrategy();
    expect(component.errorMessage()).toBe('duplicate');
  });

  it('enables and disables a strategy', () => {
    component.toggleStrategy({ ...strategy, enabled: false });
    expect(api.enableStrategy).toHaveBeenCalledWith('s-1');
    component.toggleStrategy(strategy);
    expect(api.disableStrategy).toHaveBeenCalledWith('s-1');

    api.disableStrategy.mockReturnValueOnce(throwError(() => new Error('no')));
    component.toggleStrategy(strategy);
    expect(component.errorMessage()).toBe('no');
    api.enableStrategy.mockReturnValueOnce(throwError(() => ({})));
    component.toggleStrategy({ ...strategy, enabled: false });
    expect(component.errorMessage()).toBe('Failed to enable strategy');
  });

  it('removes a strategy only after confirmation', () => {
    vi.spyOn(window, 'confirm').mockReturnValueOnce(false);
    component.removeStrategy(strategy);
    expect(api.deleteStrategy).not.toHaveBeenCalled();

    vi.spyOn(window, 'confirm').mockReturnValueOnce(true);
    component.removeStrategy(strategy);
    expect(api.deleteStrategy).toHaveBeenCalledWith('s-1');
    expect(component.actionMessage()).toContain('removed');

    vi.spyOn(window, 'confirm').mockReturnValueOnce(true);
    api.deleteStrategy.mockReturnValueOnce(throwError(() => new Error('locked')));
    component.removeStrategy(strategy);
    expect(component.errorMessage()).toBe('locked');
  });

  it('sends a simulated tick and reports an empty result', () => {
    component.simSymbol = '';
    component.simPrice = 0;
    component.sendSimulatedTick();
    expect(api.simulateTick).not.toHaveBeenCalled();

    component.simSymbol = 'aapl';
    component.simPrice = 10;
    component.sendSimulatedTick();
    expect(component.simStatus()).toContain('bought');

    api.simulateTick.mockReturnValueOnce(of({ results: [] }));
    component.sendSimulatedTick();
    expect(component.simStatus()).toContain('No active strategy');

    api.simulateTick.mockReturnValueOnce(throwError(() => new Error('boom')));
    component.sendSimulatedTick();
    expect(component.simStatus()).toContain('boom');
  });
});
