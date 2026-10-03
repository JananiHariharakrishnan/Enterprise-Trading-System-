
````md
# AGENTS.md — Frontend Design System

## Purpose

This project uses ONE consistent visual design system across the entire
Angular application.

The visual language is:

- Minimalistic
- Modern
- Professional
- Subtle glassmorphism
- Green-based
- Fintech / trading-platform aesthetic
- High readability
- Responsive
- Accessible

The application should look like one professionally designed product.

Do NOT create a different visual style for individual pages or components.

Before creating new CSS, inspect the existing global styles and reusable
components and reuse them wherever possible.

---

# 1. Design Philosophy

The application should feel like a modern professional trading platform.

Priorities:

1. Readability
2. Consistency
3. Clear information hierarchy
4. Clean spacing
5. Simple layouts
6. Subtle glass effects
7. Green as the primary accent
8. Professional financial-app appearance
9. Responsive behavior
10. Accessibility

Glassmorphism is a visual enhancement.

It must NEVER reduce readability.

Avoid:

- Excessive gradients
- Excessive blur
- Neon colors
- Heavy shadows
- Excessive rounded corners
- Cartoon-like UI
- Excessive animations
- Random decorative elements
- Random colors
- Random spacing
- Random component widths
- Inconsistent visual patterns

---

# 2. IMPORTANT: Layout Before Decoration

Always implement the structural layout FIRST.

The order of implementation should be:

1. Page structure
2. Flexbox / Grid layout
3. Width constraints
4. Alignment
5. Spacing
6. Typography
7. Component styling
8. Glassmorphism
9. Shadows / gradients
10. Animations

Do NOT start by adding gradients, blur or shadows.

A visually attractive component with poor layout is considered incorrect.

---

# 3. Global Color System

Use CSS variables.

Never hard-code colors inside individual components unless there is a
specific semantic reason.

Use:

```css
:root {
  /* Background */
  --color-bg: #07110d;
  --color-bg-secondary: #0b1712;
  --color-surface: #102019;

  /* Glass */
  --glass-bg: rgba(255, 255, 255, 0.06);
  --glass-bg-hover: rgba(255, 255, 255, 0.09);
  --glass-border: rgba(255, 255, 255, 0.12);

  /* Primary Green */
  --color-primary: #22c55e;
  --color-primary-hover: #16a34a;
  --color-primary-soft: rgba(34, 197, 94, 0.14);

  /* Text */
  --color-text: #f1f5f3;
  --color-text-secondary: #a7b5ae;
  --color-text-muted: #718078;

  /* Semantic */
  --color-success: #22c55e;
  --color-warning: #f59e0b;
  --color-error: #ef4444;
  --color-info: #38bdf8;

  /* Borders */
  --color-border: rgba(255, 255, 255, 0.10);
  --color-border-strong: rgba(255, 255, 255, 0.16);

  /* Radius */
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;

  /* Shadows */
  --shadow-sm: 0 4px 16px rgba(0, 0, 0, 0.12);
  --shadow-md: 0 8px 30px rgba(0, 0, 0, 0.18);

  /* Layout */
  --page-max-width: 1440px;
  --content-max-width: 1200px;
  --form-max-width: 720px;
  --trading-max-width: 1100px;

  /* Spacing */
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 20px;
  --space-6: 24px;
  --space-7: 32px;
  --space-8: 40px;
  --space-9: 48px;
  --space-10: 64px;

  /* Responsive */
  --breakpoint-sm: 640px;
  --breakpoint-md: 768px;
  --breakpoint-lg: 1024px;
  --breakpoint-xl: 1280px;
}
````

---

# 4. Global Page Layout

Every page must use a consistent centered container.

Preferred:

```css
.page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.page-content {
  flex: 1;
  width: 100%;
  display: flex;
  flex-direction: column;
}

.page-container {
  width: 100%;
  max-width: var(--page-max-width);
  margin-inline: auto;
  padding-inline: 32px;
}
```

On smaller screens:

```css
@media (max-width: 768px) {
  .page-container {
    padding-inline: 16px;
  }
}
```

Large screens must NOT have content stretching infinitely across the screen.

Content must remain visually contained.

---

# 5. Flexbox-First Rule

Flexbox is the DEFAULT layout mechanism.

Prefer:

```css
display: flex;
```

for:

- Page layouts
- Navigation
- Headers
- Toolbars
- Button groups
- Form layouts
- Alignment
- Centering
- Vertical stacking
- Horizontal groups
- Responsive layouts

Use CSS Grid when the layout is genuinely two-dimensional.

Good Grid use cases:

- Dashboard card grids
- Statistics layouts
- Complex trading layouts

Do NOT use absolute positioning for normal page structure.

Avoid:

```css
position: absolute;
top: 100px;
left: 200px;
```

when flexbox or grid can solve the problem.

---

# 6. Centering

When content needs to be centered, use flexbox.

Preferred:

```css
.center {
  display: flex;
  align-items: center;
  justify-content: center;
}
```

Vertical centering:

```css
.center-vertical {
  display: flex;
  flex-direction: column;
  justify-content: center;
}
```

Horizontal centering:

```css
.center-horizontal {
  display: flex;
  justify-content: center;
}
```

Do not use arbitrary margins to force elements into the center.

---

# 7. Standard Page Structure

Every major page should follow:

```text
Page
│
├── Page Header
│   ├── Title
│   ├── Description
│   └── Actions
│
├── Primary Content
│
├── Secondary Content
│
└── Supporting Content
```

Example:

```text
Orders
View and manage your trading activity.

                              [Place Order]

┌────────────────────────────────────────────┐
│                                            │
│                Order Table                 │
│                                            │
└────────────────────────────────────────────┘
```

---

# 8. Page Header

Use:

```css
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 32px;
}
```

On mobile:

```css
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
```

The page title should be visually dominant.

Actions should be aligned with the header rather than positioned manually.

---

# 9. Spacing System

Use ONLY these primary spacing values:

```text
4px
8px
12px
16px
20px
24px
32px
40px
48px
64px
```

Prefer:

```css
gap: 16px;
padding: 24px;
margin-bottom: 32px;
```

Avoid arbitrary values such as:

```css
gap: 13px;
margin-left: 37px;
padding-top: 19px;
```

Use `gap` for spacing between siblings whenever possible.

---

# 10. Gap-First Rule

Prefer:

```css
.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
```

instead of:

```css
.field {
  margin-bottom: 16px;
}
```

Prefer parent-level spacing.

This keeps the layout predictable and maintainable.

---

# 11. Width Rules

Avoid arbitrary widths.

Prefer:

```css
width: 100%;
max-width: 720px;
```

instead of:

```css
width: 537px;
```

Forms should normally use:

```css
width: 100%;
```

with the parent controlling the maximum width.

Recommended maximum widths:

```text
Application:     1440px
Main content:    1200px
Trading area:    1100px
Forms:            720px
```

---

# 12. Height Rules

Avoid fixed heights for content.

Prefer:

```css
min-height
```

instead of:

```css
height
```

unless the component genuinely requires a fixed height.

Never clip content because of a fixed-height container.

---

# 13. Glassmorphism

Glassmorphism should be subtle.

Use it primarily for:

- Cards
- Navigation
- Modals
- Account panels
- Order ticket
- Dashboard sections

Standard glass:

```css
.glass {
  background: var(--glass-bg);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1px solid var(--glass-border);
  box-shadow: var(--shadow-sm);
}
```

Hover:

```css
.glass:hover {
  background: var(--glass-bg-hover);
}
```

Do NOT apply heavy glass effects to every element.

Dense financial tables should prioritize readability.

---

# 14. Background

Use a dark green-black background.

Preferred:

```css
.app-background {
  min-height: 100vh;

  background:
    radial-gradient(
      circle at top right,
      rgba(34, 197, 94, 0.10),
      transparent 35%
    ),
    var(--color-bg);
}
```

Use subtle gradients only.

Do not create multiple competing gradients.

---

# 15. Cards

Use one standard card design.

```css
.card {
  background: var(--glass-bg);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: 24px;
}
```

Do not create:

```text
card-green
card-blue
card-special
card-dashboard
card-order
card-account
```

Reuse the same card component/style.

---

# 16. Card Grids

For multiple cards:

```css
.card-grid {
  display: grid;
  grid-template-columns: repeat(
    auto-fit,
    minmax(240px, 1fr)
  );
  gap: 16px;
}
```

Cards should align naturally.

Do not create dozens of tiny cards.

---

# 17. Typography

Use a clean modern sans-serif.

Preferred:

```css
font-family:
  Inter,
  ui-sans-serif,
  system-ui,
  -apple-system,
  BlinkMacSystemFont,
  "Segoe UI",
  sans-serif;
```

Hierarchy:

```text
Page Title
Section Title
Card Title
Body
Secondary / Metadata
```

Avoid excessive font-size variations.

---

# 18. Buttons

Primary:

```css
.btn-primary {
  background: var(--color-primary);
  color: #041009;
  border-radius: var(--radius-md);
  font-weight: 600;
}
```

Secondary:

```css
.btn-secondary {
  background: var(--glass-bg);
  color: var(--color-text);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
}
```

Danger:

```css
.btn-danger {
  background: rgba(239, 68, 68, 0.12);
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.25);
}
```

All buttons must have:

- Consistent height
- Consistent padding
- Consistent radius
- Visible hover state
- Visible focus state
- Disabled state

Do not create unique button designs per page.

---

# 19. Forms

All forms must use the same styling.

```css
.form-input {
  width: 100%;
  background: rgba(255, 255, 255, 0.04);
  color: var(--color-text);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 10px 12px;
  outline: none;
}

.form-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}
```

Forms must have:

- Clear labels
- Consistent spacing
- Focus states
- Error states
- Disabled states
- Accessible labels

---

# 20. Trading UI

Trading pages must prioritize clarity.

Preferred layout:

```text
Trade
Place a new order.

┌──────────────────────┐  ┌──────────────────────┐
│ Order Ticket         │  │ Account Summary      │
│                      │  │                      │
│ Instrument           │  │ Account              │
│ Buy / Sell           │  │ Cash Balance         │
│ Quantity             │  │ Buying Power         │
│ Price                │  │                      │
│                      │  │ Read Only            │
│ [Place Order]        │  │                      │
└──────────────────────┘  └──────────────────────┘

┌─────────────────────────────────────────────────┐
│ Order Result                                    │
└─────────────────────────────────────────────────┘
```

Use:

```css
.trading-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(280px, 0.8fr);
  gap: 24px;
}
```

On mobile:

```css
@media (max-width: 900px) {
  .trading-layout {
    grid-template-columns: 1fr;
  }
}
```

---

# 21. Tables / Order Blotter

Tables should be clean and readable.

```css
.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th {
  color: var(--color-text-secondary);
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.data-table td,
.data-table th {
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
}

.data-table tbody tr:hover {
  background: rgba(255, 255, 255, 0.03);
}
```

Numbers should normally be right aligned.

Status must contain text as well as color.

Example:

```text
NEW
FILLED
REJECTED
CANCELLED
```

Never rely on color alone.

---

# 22. Status Colors

Use semantic colors consistently.

```text
SUCCESS / FILLED   → Green
NEW                → Blue / Neutral
REJECTED           → Red
CANCELLED          → Amber
WARNING            → Amber
ERROR              → Red
```

Do not introduce new colors for statuses.

---

# 23. Navigation

Navigation should be minimal.

Preferred:

```text
Logo

Dashboard
Trade
Orders
Account

User
Logout
```

Use subtle glass styling.

The active route should use the green accent.

Do not create oversized navigation elements.

---

# 24. Responsive Design

The application must work on:

- Desktop
- Tablet
- Mobile

Preferred breakpoints:

```css
640px
768px
1024px
1280px
```

Desktop layouts can use horizontal structures.

Mobile layouts should naturally stack.

Use CSS media queries.

Do NOT create separate mobile and desktop components unless absolutely necessary.

---

# 25. Alignment

Default alignment rules:

Text:
Left aligned

Numbers:
Right aligned

Buttons:
Grouped together

Page actions:
Aligned with page header

Status badges:
Vertically centered

Use centered content only when there is a deliberate reason.

---

# 26. Animation

Animations must be subtle.

Allowed:

- 150–250ms transitions
- Button hover
- Focus transitions
- Modal entrance
- Small state changes

Preferred:

```css
transition:
  background 180ms ease,
  border-color 180ms ease,
  box-shadow 180ms ease,
  transform 180ms ease;
```

Avoid:

- Constant animations
- Floating elements
- Excessive glowing
- Large parallax
- Distracting movement

---

# 27. Accessibility

Never sacrifice accessibility for visual design.

Maintain:

- Sufficient contrast
- Visible keyboard focus
- Accessible labels
- Semantic HTML
- Status text in addition to color
- Readable font sizes
- Clear error messages

Glassmorphism must never make text difficult to read.

---

# 28. CSS Architecture

Prefer:

```text
src/
├── styles.css
├── styles/
│   ├── _variables.css
│   ├── _layout.css
│   ├── _buttons.css
│   ├── _forms.css
│   ├── _cards.css
│   ├── _tables.css
│   └── _utilities.css
│
└── app/
    ├── core/
    ├── shared/
    └── features/
```

Global design tokens belong in global styles.

Component-specific styles belong in the component.

Do not put global design-system rules inside random components.

---

# 29. Reuse Before Creating

Before creating CSS:

1. Search for an existing class.
2. Search for an existing component.
3. Reuse an existing design token.
4. Reuse an existing spacing value.
5. Reuse an existing component pattern.
6. Only create new CSS when necessary.

If the same styling appears in two or more components,
move it into the shared design system.

---

# 30. Anti-Patterns

NEVER use:

- Random absolute positioning
- Random margins to force alignment
- Arbitrary widths
- Arbitrary heights
- Different button designs
- Different input designs
- Excessive gradients
- Excessive blur
- Excessive shadows
- Huge empty spaces
- Random colors
- Random border radii
- Random spacing
- Competing accent colors
- Decorative elements that compete with trading information

---

# 31. Visual Quality Check

Before considering a UI implementation complete, verify:

### Layout

- Is the content centered?
- Is the maximum width controlled?
- Is flexbox used where appropriate?
- Is grid used only where appropriate?
- Is spacing consistent?
- Is there unnecessary empty space?
- Is anything positioned absolutely without a good reason?

### Visual consistency

- Are colors coming from CSS variables?
- Are cards consistent?
- Are buttons consistent?
- Are inputs consistent?
- Are border radii consistent?
- Are shadows consistent?
- Is the green accent used consistently?

### Responsive

- Does the page work on desktop?
- Does it work on tablet?
- Does it work on mobile?
- Do horizontal layouts stack appropriately?

### Professionalism

- Is the primary action obvious?
- Is the information hierarchy clear?
- Is the interface visually calm?
- Is the glass effect subtle?
- Does the page look like it belongs to the same product?

---

# 32. Theme System (Dark & Light Mode)

The application supports both dark and light themes with seamless switching.

## How It Works

1. **Theme Service** (`src/app/services/theme.service.ts`)
   - Manages theme state using Angular signals
   - Detects system preference on first load
   - Persists user choice to localStorage
   - Applies theme via `data-theme` attribute on HTML element

2. **CSS Variables**
   - `:root` defines dark mode (DEFAULT)
   - `[data-theme="light"]` overrides with light mode colors
   - All colors, spacing, shadows use variables
   - Smooth 300ms transitions between themes

3. **Theme Toggle Button**
   - Located in app header and home page hero
   - Shows ☀️ in dark mode (click to switch to light)
   - Shows 🌙 in light mode (click to switch to dark)
   - Uses data-testid for e2e testing

## Implementation for New Pages

When creating new pages/components:

1. **Don't use hardcoded colors**
   ```css
   /* ❌ WRONG */
   .card { background: #ffffff; }
   
   /* ✅ CORRECT */
   .card { background: var(--glass-bg); }
   ```

2. **Use CSS Variables for all colors**
   ```css
   /* ✅ Text */
   color: var(--color-text);
   color: var(--color-text-secondary);
   color: var(--color-text-muted);
   
   /* ✅ Colors */
   background: var(--color-bg);
   border-color: var(--color-border);
   
   /* ✅ Accents */
   background: var(--color-primary);
   color: var(--color-success | --color-error | --color-warning);
   ```

3. **Glass Effect**
   ```css
   /* ✅ Use glass variables */
   background: var(--glass-bg);
   backdrop-filter: blur(14px);
   -webkit-backdrop-filter: blur(14px);
   border: 1px solid var(--glass-border);
   
   /* ✅ Hover state */
   &:hover {
     background: var(--glass-bg-hover);
   }
   ```

4. **Shadows**
   ```css
   /* ✅ Use predefined shadows */
   box-shadow: var(--shadow-sm);  /* Light shadow */
   box-shadow: var(--shadow-md);  /* Medium shadow */
   ```

5. **Testing Themes**
   ```typescript
   /* ✅ In components */
   readonly themeService = inject(ThemeService);
   
   /* ✅ In templates */
   *ngIf="themeService.currentTheme() === 'dark'"
   
   /* ✅ Toggle in code */
   this.themeService.toggleTheme();
   this.themeService.setTheme('light');
   ```

## Color Palette Reference

### Dark Mode (Default)
```css
--color-bg: #07110d;              /* Deep green-black */
--color-text: #f1f5f3;            /* Off-white */
--color-primary: #22c55e;         /* Bright green */
--glass-bg: rgba(255,255,255,0.06);
```

### Light Mode
```css
--color-bg: #f8fafc;              /* Very light blue-gray */
--color-text: #1f2937;            /* Dark gray */
--color-primary: #059669;         /* Emerald green */
--glass-bg: rgba(255,255,255,0.5); /* Stronger glass effect */
--color-border: rgba(5,150,105,0.2); /* Green-tinted borders */
```

## What NOT to Do

❌ Don't create mode-specific stylesheets
❌ Don't hardcode hex colors in components
❌ Don't use `prefers-color-scheme` media queries in component CSS
❌ Don't make assumptions about background/text colors
❌ Don't forget WebKit prefix for backdrop-filter

## When Adding New Components

1. Use CSS variables exclusively
2. Test in both dark AND light modes
3. Verify contrast ratios (WCAG AA: 4.5:1 minimum)
4. Check that the component "just works" when theme changes
5. Avoid hardcoded whitespace/darkness assumptions

## Performance Note

Theme changes trigger smooth 300ms transitions on all elements. This is intentional for a polished UX. If performance is an issue on very large pages, consider adding:

```css
/* Disable transitions during theme switch for lower-end devices */
.no-theme-transition * {
  transition: none !important;
}
```

---

# 34. Project File Structure & Component Usage

## Directory Structure

```text
trade-ui/
├── src/
│   ├── app/
│   │   ├── app.ts                     # Root component
│   │   ├── app.routes.ts              # Routing configuration
│   │   ├── app.html                   # Root template (RouterOutlet)
│   │   ├── app.css                    # Root styles
│   │   │
│   │   ├── components/
│   │   │   ├── app-header/            # Sticky header (shown on all pages)
│   │   │   │   ├── app-header.component.ts
│   │   │   │   ├── app-header.component.html
│   │   │   │   └── app-header.component.css
│   │   │   └── ...
│   │   │
│   │   ├── pages/
│   │   │   ├── home/                  # Entry point (root path '/')
│   │   │   │   ├── home.component.ts
│   │   │   │   ├── home.component.html
│   │   │   │   ├── home.component.css
│   │   │   │   └── home.component.spec.ts
│   │   │   │
│   │   │   ├── login/                 # Path '/login' (public)
│   │   │   │   └── ...
│   │   │   │
│   │   │   ├── register/              # Path '/register' (public)
│   │   │   │   └── ...
│   │   │   │
│   │   │   ├── dashboard/             # Path '/dashboard' (protected)
│   │   │   │   └── ...
│   │   │   │
│   │   │   └── profile/               # Path '/profile' (protected)
│   │   │       └── ...
│   │   │
│   │   ├── services/
│   │   │   ├── theme.service.ts       # Theme switching (dark/light)
│   │   │   ├── auth-session.service.ts # Authentication state
│   │   │   └── ...
│   │   │
│   │   └── guards/
│   │       ├── auth.guard.ts          # Protects authenticated routes
│   │       └── login-redirect.guard.ts # Prevents logged-in users from accessing login
│   │
│   ├── styles.css                     # Global design tokens & background
│   └── ...
│
├── AGENTS.md                          # This file - design system docs
└── ...
```

## Component Usage

### **HomeComponent** (`src/app/pages/home/home.component.html`)

**Location:** Root path (`/`)

**Purpose:** Landing/intro page visible to all users (authenticated and unauthenticated)

**Used by:**
- `app.routes.ts` - Configured as the root route `{ path: '', component: HomeComponent }`
- `RouterOutlet` in `app.html` - Rendered inside the main router outlet

**Key Elements:**
- Hero section with title and description
- "Sign In" / "Create Account" buttons (shown when NOT logged in)
- "Go to Dashboard" button (shown when logged in)
- Features section (4-column grid on desktop)
- Theme toggle button (☀️/🌙 emoji)
- Investment-themed glassmorphic background with green gradients

**Light Mode Styling:**
- Soft blue-green background (#f5f9f7)
- Visible green gradient circles (opacity 0.3-0.45)
- Enhanced glass effect for cards (rgba(240, 253, 250, 0.8))

### **AppHeaderComponent** (`src/app/components/app-header/app-header.component.html`)

**Location:** Shown on ALL pages (included in `app.ts`)

**Purpose:** Navigation header with theme toggle and sign-out

**Used by:**
- `app.ts` - Imported in root component and always displayed above RouterOutlet

**Key Elements:**
- Brand logo
- Navigation links
- Sign-out button
- Sticky positioning (stays at top when scrolling)
- Glass effect background
- Theme toggle in header actions section

### **LoginComponent** (`src/app/pages/login/login.component.html`)

**Location:** Path `/login`

**Purpose:** User sign-in form

**Used by:**
- `app.routes.ts` - Configured at `{ path: 'login', component: LoginComponent, ... }`
- Protected by `loginRedirectGuard` - redirects to dashboard if already logged in

**Light Mode Styling:**
- Form card with enhanced glass effect: `rgba(240, 253, 250, 0.75)`
- Input fields with stronger visibility: `rgba(255, 255, 255, 0.85)`
- Borders upgraded to `1.5px` solid with green tint
- Shadows use green-based color: `rgba(5, 150, 105, 0.12)`

### **RegisterComponent** (`src/app/pages/register/register.component.html`)

**Location:** Path `/register`

**Purpose:** New user registration form

**Used by:**
- `app.routes.ts` - Configured at `{ path: 'register', component: RegisterComponent, ... }`
- Protected by `loginRedirectGuard` - redirects to dashboard if already logged in

### **DashboardComponent** (`src/app/pages/dashboard/dashboard.component.html`)

**Location:** Path `/dashboard`

**Purpose:** Main trading dashboard (portfolio, summary, orders)

**Used by:**
- `app.routes.ts` - Configured at `{ path: 'dashboard', component: DashboardComponent, ... }`
- Protected by `authGuard` - requires authentication

**Light Mode Styling:**
- Dashboard cards: `rgba(240, 253, 250, 0.8)` background with `1.5px` borders
- Summary items: `rgba(240, 253, 250, 0.6)` with enhanced hover state
- Table headers: `rgba(5, 150, 105, 0.08)` background for visual separation
- All borders upgraded to green-tinted: `var(--glass-border)`

### **ProfileComponent** (`src/app/pages/profile/profile.component.html`)

**Location:** Path `/profile`

**Purpose:** User profile editing

**Used by:**
- `app.routes.ts` - Configured at `{ path: 'profile', component: ProfileComponent, ... }`
- Protected by `authGuard` - requires authentication

## Routing Flow

```text
Browser Request
        ↓
    app.ts (root component)
        ├── [Always Visible]
        │   └── AppHeaderComponent
        │
        └── [RouterOutlet - shows different page based on route]
            ├── path '' → HomeComponent
            ├── path 'login' → LoginComponent (with loginRedirectGuard)
            ├── path 'register' → RegisterComponent (with loginRedirectGuard)
            ├── path 'dashboard' → DashboardComponent (with authGuard)
            ├── path 'profile' → ProfileComponent (with authGuard)
            └── path '**' → redirects to ''
```

## Navigation Rules

**Unauthenticated Users:**
- Can access: Home, Login, Register
- Cannot access: Dashboard, Profile
- Attempting to access protected routes → Redirected to Home

**Authenticated Users:**
- Can access: All routes
- Attempting to access Login/Register → Redirected to Dashboard
- Can sign out via AppHeaderComponent

## Light Mode Implementation

All components now have enhanced light mode styling:

1. **Stronger Glass Effect** - Opacity increased for visibility
2. **Green-Tinted Elements** - Borders and shadows use green accents
3. **Better Contrast** - Input fields and cards have stronger backgrounds
4. **Investment-Themed Background** - Global background uses green gradients (ellipses at 15%, 85%)
5. **Consistent Hover States** - All interactive elements have visible hover effects

When adding new pages:
- Use CSS variable overrides in light mode (see Section #32)
- Test both dark and light modes
- Ensure sufficient contrast for readability
- Use green-tinted borders/shadows for cohesion

---

# 33. Final Agent Rule

The goal is NOT to make every component visually impressive.

The goal is to make the ENTIRE APPLICATION feel like ONE professionally designed
product.

Consistency > novelty.

Readability > decoration.

Layout > effects.

Professionalism > visual complexity.

When in doubt, simplify.
