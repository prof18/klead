# Security Policy

The core parser handles static HTML as untrusted input.

Final output sanitization strips:

- scripts except math-source preservation cases before final cleanup
- styles from final content
- event handler attributes
- `srcdoc`
- dangerous `href`, `src`, `action`, `formaction`, and `xlink:href` values
- `javascript:`
- `data:text/html`

Trusted YouTube, X/Twitter, Instagram, and Vimeo iframe URLs are narrow iframe exceptions. YouTube, X/Twitter, and Instagram sources are normalized to safe cleaned-HTML embeds and Markdown links; Vimeo retains a sanitized iframe. Known publisher placeholders are converted only when their URLs pass the same trusted-source validation. Arbitrary iframe, object, and embed content is still stripped.

Safe image data URLs such as `data:image/png` are preserved. The parser does not execute JavaScript and does not use WebView, browser DOM, GraalJS, or Compose rendering.
