import {
  Component,
  HostListener,
  inject,
  signal,
  ElementRef,
  ViewChild,
  AfterViewChecked
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ChatService } from '../../services/chat.service';
import { TradeSignalResponse } from '../../services/signals.service';

export interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
  signals?: TradeSignalResponse[];
  timestamp: Date;
}

@Component({
  selector: 'app-chatbot-widget',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chatbot-widget.component.html',
  styleUrls: ['./chatbot-widget.component.css']
})
export class ChatbotWidgetComponent implements AfterViewChecked {
  private readonly chatService = inject(ChatService);
  private readonly router = inject(Router);

  @ViewChild('scrollContainer') private scrollContainer?: ElementRef<HTMLDivElement>;
  @ViewChild('messageInput') private messageInput?: ElementRef<HTMLInputElement>;

  readonly isOpen = signal(false);
  readonly loading = signal(false);
  readonly inputText = signal('');
  readonly messages = signal<ChatMessage[]>([
    {
      role: 'assistant',
      text: "Hello! I'm your AI Trading Assistant. Ask me about your portfolio, watchlists, technical indicators, or current AI trade signals.",
      timestamp: new Date()
    }
  ]);

  readonly quickPrompts = [
    'What are the strongest BUY signals today?',
    'Should I buy more AAPL?',
    'Summarize my watchlists',
    'How does RSI and EMA work in your signal engine?'
  ];

  private shouldScroll = false;

  @HostListener('window:keydown', ['$event'])
  handleKeyboardShortcut(event: KeyboardEvent): void {
    // Alt + C shortcut to toggle chat widget
    if (event.altKey && (event.key === 'c' || event.key === 'C')) {
      event.preventDefault();
      this.toggleOpen();
    }
  }

  toggleOpen(): void {
    const nextState = !this.isOpen();
    this.isOpen.set(nextState);
    if (nextState) {
      this.shouldScroll = true;
      setTimeout(() => {
        this.messageInput?.nativeElement?.focus();
      }, 100);
    }
  }

  close(): void {
    this.isOpen.set(false);
  }

  ngAfterViewChecked(): void {
    if (this.shouldScroll) {
      this.scrollToBottom();
      this.shouldScroll = false;
    }
  }

  private scrollToBottom(): void {
    try {
      if (this.scrollContainer) {
        this.scrollContainer.nativeElement.scrollTop =
          this.scrollContainer.nativeElement.scrollHeight;
      }
    } catch {
      // Ignore scroll failures
    }
  }

  sendMessage(textToSend?: string): void {
    const text = (textToSend || this.inputText()).trim();
    if (!text || this.loading()) return;

    // Add user message
    const userMsg: ChatMessage = {
      role: 'user',
      text,
      timestamp: new Date()
    };

    this.messages.update(list => [...list, userMsg]);
    this.inputText.set('');
    this.loading.set(true);
    this.shouldScroll = true;

    this.chatService.sendMessage(text).subscribe({
      next: (resp) => {
        const nonTickers = new Set([
          'EMA', 'RSI', 'SMA', 'MACD', 'VWAP', 'ATR', 'YOUR', 'YOU', 'BUY', 'SELL', 'HOLD',
          'TRADE', 'TRADES', 'ORDER', 'ORDERS', 'IN', 'ON', 'AT', 'TO', 'OF', 'BY', 'AN', 'AS',
          'TODAY', 'NOW', 'DAY', 'DAILY', 'WEEK', 'MONTH', 'YEAR', 'DATE', 'TIME',
          'SIGNAL', 'SIGNALS', 'ALERT', 'ALERTS', 'BOT', 'CHAT'
        ]);
        const validSignals = (resp.relatedSignals || []).filter(
          s => s && s.symbol && !nonTickers.has(s.symbol.toUpperCase()) && s.symbol.trim().length >= 2
        );

        const assistantMsg: ChatMessage = {
          role: 'assistant',
          text: resp.reply,
          signals: validSignals,
          timestamp: new Date()
        };
        this.messages.update(list => [...list, assistantMsg]);
        this.loading.set(false);
        this.shouldScroll = true;
      },
      error: (err) => {
        console.error('Chat error:', err);
        const errorMsg: ChatMessage = {
          role: 'assistant',
          text: 'I ran into an issue processing that question. Please make sure the backend LLM service is running.',
          timestamp: new Date()
        };
        this.messages.update(list => [...list, errorMsg]);
        this.loading.set(false);
        this.shouldScroll = true;
      }
    });
  }

  sendQuickPrompt(prompt: string): void {
    this.sendMessage(prompt);
  }

  navigateToTrade(symbol: string, action?: string): void {
    const side = action === 'SELL' ? 'SELL' : 'BUY';
    this.router.navigate(['/orders'], { queryParams: { symbol, side } });
  }

  clearChat(): void {
    this.messages.set([
      {
        role: 'assistant',
        text: 'Chat history cleared. How can I help you today?',
        timestamp: new Date()
      }
    ]);
  }
}
