# Security Policy

The core parser handles static HTML as untrusted input.

Final output sanitization strips:

- scripts except math-source preservation cases before final cleanup
- `<style>` elements and inline publisher color, background, font family, font size, and line-height declarations from cleaned HTML so the consuming reader can apply its theme
- legacy HTML presentation attributes (`bgcolor`, `color`, `face`, and `<font size>`) outside SVG and MathML
- event handler attributes
- `srcdoc`
- dangerous `href`, `src`, `action`, `formaction`, and `xlink:href` values
- `javascript:`
- `data:text/html`

Trusted YouTube, X/Twitter, Instagram, and Vimeo iframe URLs are narrow iframe exceptions. YouTube, X/Twitter, and Instagram sources are normalized to safe cleaned-HTML embeds and Markdown links; Vimeo retains a sanitized iframe. Known publisher placeholders are converted only when their URLs pass the same trusted-source validation. Arbitrary iframe, object, and embed content is still stripped.

Safe image data URLs such as `data:image/png` are preserved. The parser does not execute JavaScript and does not use WebView, browser DOM, GraalJS, or Compose rendering.

Inline layout declarations, image dimensions, code whitespace, semantic emphasis, and SVG/MathML visual styling are preserved in cleaned HTML.

For unrecognized Kill the Newsletter templates, a generic cleanup removes only
layout declarations (margin, padding, border, `box-shadow`, and non-media width and
height), legacy layout attributes (`align`, `valign`, `border`, `cellpadding`,
`cellspacing`) and non-media `width`/`height` attributes. Unknown and semantic
declarations are kept, media dimensions are kept, and code, SVG/MathML and data
tables are not modified. This is presentation cleanup, not content deletion.
