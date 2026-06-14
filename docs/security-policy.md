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

Safe image data URLs such as `data:image/png` are preserved. The parser does not execute JavaScript and does not use WebView, browser DOM, GraalJS, or Compose rendering.
