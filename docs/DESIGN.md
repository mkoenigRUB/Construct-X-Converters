# Design system — professional data-centric web UI

**Product character:** calm, clear, trustworthy, structured. Prioritize productivity and legibility over visual effects.

## Tokens (starting point, customizable)
- Surfaces: white/light neutrals; text: dark neutral, sufficient contrast
- Primary action: one consistent accent color to be chosen after branding
- Spacing scale: 4, 8, 12, 16, 24, 32 px
- Border radius: modest and consistent (e.g. 6–10px)
- Typography: system/UI sans-serif; clear hierarchy; comfortable body size
- Interactive states: default, hover, focus-visible, active, disabled, loading, error, success

## Structural patterns
- Desktop: persistent navigation if justified; consistent page heading, breadcrumb when needed, actionable toolbar.
- Tables: sort/filter/search/pagination as needed; column alignment and clear empty/loading/error states.
- Forms: inline validation, labels, helper text, required indicators and safe defaults.
- Dialogs: keyboard operable, appropriate focus management; prefer inline flows for routine edits.
- Mobile: true reflow, not merely shrinking desktop tables.

## Accessibility
Target WCAG 2.2 AA where applicable. Verify semantics, text contrast, tab order, focus indicators, screen-reader naming, touch targets and reduced-motion preference. A skill or component library does not guarantee conformance.

## UI review checklist
- [ ] Each page has a clear primary goal
- [ ] Components and tokens reused consistently
- [ ] Responsive at practical viewport sizes
- [ ] Forms, errors and empty states designed
- [ ] Keyboard and screen reader smoke tests performed
- [ ] No decorative motion hiding information

## Screens and references
[Link local mockups or screenshots; add project-specific visual decisions]
