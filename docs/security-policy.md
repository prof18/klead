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

Trusted `https://www.youtube.com/embed/...` and `https://www.youtube-nocookie.com/embed/...` iframes are the only iframe exception. They are normalized to `youtube-nocookie.com` cleaned-HTML embeds and Markdown video links; arbitrary iframe, object, and embed content is still stripped.

Safe image data URLs such as `data:image/png` are preserved. The parser does not execute JavaScript and does not use WebView, browser DOM, GraalJS, or Compose rendering.
