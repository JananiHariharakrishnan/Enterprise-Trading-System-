import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ChatbotWidgetComponent } from './chatbot-widget.component';
import { ChatService } from '../../services/chat.service';

describe('ChatbotWidgetComponent', () => {
  let fixture: ComponentFixture<ChatbotWidgetComponent>;
  let component: ChatbotWidgetComponent;
  let chat: { sendMessage: ReturnType<typeof vi.fn> };
  let router: Router;

  beforeEach(async () => {
    chat = {
      sendMessage: vi.fn().mockReturnValue(of({
        reply: 'Buy AAPL',
        relatedSignals: [
          { symbol: 'AAPL', action: 'BUY', confidence: 0.8, reasoning: 'ok', generatedAt: '2026-10-07T00:00:00Z' },
          { symbol: 'RSI', action: 'HOLD', confidence: 0.1, reasoning: 'skip', generatedAt: '2026-10-07T00:00:00Z' },
          { symbol: 'X', action: 'HOLD', confidence: 0.1, reasoning: 'short', generatedAt: '2026-10-07T00:00:00Z' }
        ]
      }))
    };
    await TestBed.configureTestingModule({
      imports: [ChatbotWidgetComponent],
      providers: [
        provideRouter([]),
        { provide: ChatService, useValue: chat }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(ChatbotWidgetComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('toggles with the button and with Alt+C', () => {
    component.toggleOpen();
    expect(component.isOpen()).toBe(true);
    const event = new KeyboardEvent('keydown', { altKey: true, key: 'c' });
    component.handleKeyboardShortcut(event);
    expect(component.isOpen()).toBe(false);
    component.handleKeyboardShortcut(new KeyboardEvent('keydown', { key: 'c' }));
    expect(component.isOpen()).toBe(false);
    component.close();
    expect(component.isOpen()).toBe(false);
  });

  it('sends a message and keeps only real tickers', () => {
    component.inputText.set('  hello  ');
    component.sendMessage();
    expect(chat.sendMessage).toHaveBeenCalledWith('hello');
    expect(component.inputText()).toBe('');
    const last = component.messages().at(-1);
    expect(last?.role).toBe('assistant');
    expect(last?.signals?.map((row) => row.symbol)).toEqual(['AAPL']);
    expect(component.loading()).toBe(false);
  });

  it('ignores an empty or in-flight message and shows a chat error', () => {
    component.sendMessage('   ');
    expect(chat.sendMessage).not.toHaveBeenCalled();

    component.loading.set(true);
    component.sendMessage('again');
    expect(chat.sendMessage).not.toHaveBeenCalled();
    component.loading.set(false);

    chat.sendMessage.mockReturnValueOnce(throwError(() => new Error('llm down')));
    component.sendQuickPrompt('status');
    expect(component.messages().at(-1)?.text).toContain('backend LLM');
  });

  it('navigates to an order and clears the transcript', () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    component.navigateToTrade('AAPL', 'SELL');
    expect(navigate).toHaveBeenCalledWith(['/orders'], { queryParams: { symbol: 'AAPL', side: 'SELL' } });
    component.navigateToTrade('MSFT', 'BUY');
    component.clearChat();
    expect(component.messages()).toHaveLength(1);
    expect(component.messages()[0].text).toContain('cleared');
  });

  it('scrolls the transcript after the view checks', () => {
    const scroller = { scrollTop: 0, scrollHeight: 40 };
    (component as unknown as { scrollContainer: { nativeElement: typeof scroller } }).scrollContainer = {
      nativeElement: scroller
    };
    (component as unknown as { shouldScroll: boolean }).shouldScroll = true;
    component.ngAfterViewChecked();
    expect(scroller.scrollTop).toBe(40);
  });

  it('renders the open transcript, related signals, and a pending reply', () => {
    component.toggleOpen();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Suggested Questions');

    component.sendMessage('hello');
    component.loading.set(true);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.chatbot-panel')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('AAPL');
    expect(fixture.nativeElement.querySelector('.typing-bubble')).toBeTruthy();
  });
});
