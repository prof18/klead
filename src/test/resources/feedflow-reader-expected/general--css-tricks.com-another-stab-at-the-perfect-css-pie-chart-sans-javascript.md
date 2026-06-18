```json
{
  "title": "Another Stab at the Perfect CSS Pie Chart... Sans JavaScript!",
  "author": "Antoine Villepreux",
  "site": "CSS-Tricks",
  "published": "2026-06-04T07:14:49-06:00"
}
```

Recently, [Juan Diego Rodríguez published an excellent article](https://css-tricks.com/trying-to-make-the-perfect-pie-chart-in-css/) exploring how far CSS can be pushed to build a semantic and customizable pie chart while keeping JavaScript to a minimum.

Citing Juan himself:

> In this article, we’ll try making the perfect pie chart in CSS. That means avoiding as much JavaScript as possible while addressing major headaches that comes with handwriting pie charts.

And it stated some goals that I want to go through again in order of priority:

> - This must be semantic! Meaning a screen reader should be able to understand the data shown in the pie chart.

To my understanding, the original article’s solution reached that goal. Its semantic approach (labels in plain HTML + values as attributes reinjected into the DOM via pseudo-elements) is clean, expressive, and hopefully accessible.

> - This should be HTML-customizable! Once the CSS is done, we only have to change the markup to customize the pie chart.

The original article reached that goal as well.

> - This should keep JavaScript to a minimum! No problem with JavaScript in general, it’s just more fun this way.

The original article aimed to use as little JavaScript as possible, mainly for fun. I tend to disagree slightly. For me, it should not be just for fun, since…

- JavaScript is there to deal with states and logic, and
- CSS is there to style the markup.

The initial “no JavaScript” constraint was meaningful to me. CSS should be powerful enough to let us style a pie chart. JavaScript should not be required. So, I decided to see whether there was a way to 100% get rid of it and, for fun, forked [the article’s CodePen](https://codepen.io/monknow/pen/pvbrmGL) during a lunch break.

I kept the original code as unchanged as possible, preserving its semantic approach and HTML-side customizability. If It Ain’t Broke, Don’t Fix It™.

Coincidentally, this article came right after a [recent short pen of mine toying with bar charts](https://codepen.io/villepreux/pen/NPrBEgE). So I was already in the mood for charts. But bar charts are far easier: each bar’s position or size does not depend on the others. A pie chart is a different beast: each slice’s position depends on the previous one. Luckily, this made it more of a fun challenge.

But before diving into my take on pie charts, let’s see how these have been approached by other web developers.

### Prior Art

I read many blogs, articles, and code examples from professional front-end developers, but I am not one myself, so I am not entirely certain of my ability to identify the most relevant and up-to-date prior art… Let’s try anyway.

It is easy to find many JavaScript libraries dealing with charts. I have used them a lot in my work. However, due to our no-JavaScript constraint, we shall exclude them.

I started looking for CSS-only pie charts, and one of the first libraries that pops up is [Chart-CSS](https://chartscss.org/charts). It advertises semantic structure, HTML tags to display data, accessibility, and raw data inside the markup. It seems to be a very good library and does not use any JavaScript.

Instead, it uses HTML tables, which, in my opinion and experience, makes total sense (most of the time, source data comes in a table). However, it does not solve the specific challenge of letting the user set only the values while having the start and end angles of each slice automatically computed. In this case, users still have to manually define them.

There are also very good articles discussing charts or data visualization in general. To name a few:

- Vitaly Friedman’s [“2022 Guide to Accessible Front-End Components”](https://www.smashingmagazine.com/2021/03/complete-guide-accessible-front-end-components/#accessible-data-visualizations) is still relevant.
- Sarah L. Fossheim [wrote extensively about data visualization accessibility](https://fossheim.io/writing/tag/dataviz/) between 2020 and 2024

They just have one small (but very important to us) drawback. While these resources are valuable in explaining how chart accessibility should work, they do not really address easy HTML “interface” nor pure CSS implementations.

### How I Tackled the Problem

If you are still reading, I assume you are at least somewhat interested in my approach. Understandably, If you just want to see the code, here it is!

Initially, the reason JavaScript was required was that each slice needed to know the value of the previous one. However, due to how CSS property inheritance works, a child cannot know the state of another child. Despite knowing this, I first tried to determine whether there were niche or “voodoo” techniques that would allow me to keep the original HTML markup and attribute-based approach while removing JavaScript.

I know that people like [Roman Komarov](https://kizu.dev/) can do incredible things with CSS, so I even considered exploring techniques involving property animations. But I clearly did not have the time to investigate that direction.

I returned to the core issue: because of how CSS inheritance works, children cannot know the state of their siblings. I obviously needed a “surrounding entity” to handle this.

In Juan’s post, that “entity” was JavaScript, which could loop through all children and compute the appropriate slice accumulations.

```javascript
const pieChartItems = document.querySelectorAll(".pie-chart li");

let accum = 0;

pieChartItems.forEach((item) => {
  item.style.setProperty("--accum", accum);
  accum += parseFloat(item.getAttribute("data-percentage"));
});
```

The JavaScript code sets an `--accum` value for each slice, which holds the percentage values of all charts prior to it. Without it, we wouldn’t know where to position each slice and its corresponding label.

In HTML/CSS, that entity exists too: the classic parent element. Therefore, my solution was to move the percentage values to the parent.

First, let’s remember what the original markup for the pie chart looked like this:

```markup
<ul class="pie-chart">
  <li data-percentage-1="10">Apple</li>
  <li data-percentage-2="30">Banana</li>
  <li data-percentage-3="20">Orange</li>
  <li data-percentage-4="40">Strawberry</li>
</ul>
```

While the version we’ll be using looks like this:

```markup
<ul class="pie-chart" data-percentage-1="10" data-percentage-2="30" data-percentage-3="20" data-percentage-4="40">
  <li>Apple</li>
  <li>Banana</li>
  <li>Orange</li>
  <li>Strawberry</li>
</ul>
```

We’ve moved all values to the parent `<ul>` and given each item a dedicated name — effectively indexing them.

I had previously experimented with this kind of “indexing” CSS workaround, for example, to compensate for the lack of [sibling-index()](https://css-tricks.com/almanac/functions/s/sibling-index/) and [sibling-count()](https://css-tricks.com/almanac/functions/s/sibling-count/) functions to [generate random numbers](https://codepen.io/villepreux/pen/azdgZRG). I knew this was the right direction and that the rest would follow logically on the CSS side.

**Spoiler:** `sibling-index()` and `sibling-count()` are becoming Baseline soon!

It may look like duplication since we didn’t add anything but rather moved the attributes. However, this slight change allows us to manage all labels and values from the parent in CSS. What’s best, we still keep all attributes close together. And while you may say that this won’t scale as well, if we have data with tons of entries, then a pie chart is rarely the best choice to show it.

Optionally, we could add `data-label` attributes to the labels just to pair labels and values visually.

```markup
<ul class="pie-chart" data-percentage-1="10" data-percentage-2="30" data-percentage-3="20" data-percentage-4="40">
  <!-- Optional data-label attributes: just visual hints-->
  <li data-label-1>Apple</li>
  <li data-label-2>Banana</li>
  <li data-label-3>Orange</li>
  <li data-label-4>Strawberry</li>
</ul>
```

Now let’s examine the CSS. The implementation requires two sets of some repetitive but straightforward CSS rules.

Firstly, we’ll need to pass down each percentage to its corresponding slice. To do so, we use Juan’s and get the `data-percentage` attributes into CSS through the upgraded [attr()](https://developer.chrome.com/blog/advanced-attr) function. In parallel, we’ll assign them to the corresponding slice using the [nth-child()](https://css-tricks.com/almanac/pseudo-selectors/n/nth-child/) selector.

```css
.pie-chart {
   /* We write one for each slice we think we'll need */
  --p-100-1: attr(data-percentage-1 type(<number>)); :nth-child(1) { --p-100: var(--p-100-1) }
  --p-100-2: attr(data-percentage-2 type(<number>)); :nth-child(2) { --p-100: var(--p-100-2) }
  --p-100-3: attr(data-percentage-3 type(<number>)); :nth-child(3) { --p-100: var(--p-100-3) }
  --p-100-4: attr(data-percentage-4 type(<number>)); :nth-child(4) { --p-100: var(--p-100-4) }
   /*...*/
}
```

For that kind of repetitive/incremental code, I keep it as a one-liner without carriage return. IMHO it’s a very acceptable exception to common formatting rules as it prevents typos by easing scan-ability of and also eases further iterations (e.g., adding support for more slices). But your mileage may vary.

Let’s look a little closer at what’s going on here. At the level of the whole pie, we access the percentages for each slice through their index and store them in a corresponding CSS variable, so the fourth element gets `--p-100-4`, the fifth element gets `--p-100-5`, and so on:

```css
--p-100-4: attr(data-percentage-4 type(<number>));
```

Next, we pass each a `--p-100` variable that’s local to each slice.

```css
:nth-child(4) {
  --p-100: var(--p-100-4);
}
```

We now have all these slice values accessible at two levels:

- On the pie, via indexed variables: `--p-100-1`, `--p-100-2`, `--p-100-3`
- On each slice, via the `--p-100` variable

Now, we’ll need to calculate the corresponding `--accum` value, which is the sum of the values of all previous slices. To do so, we’ll have to progressively sum each percentage after each slice, then assign the value to the slice using `nth-child()` again.

```css
.pie-chart {
  /* ... */
  --accum-1: 0;                                     :nth-child(1) { --accum: var(--accum-1) }
  --accum-2: calc(var(--accum-1) + var(--p-100-1)); :nth-child(2) { --accum: var(--accum-2) }
  --accum-3: calc(var(--accum-2) + var(--p-100-2)); :nth-child(3) { --accum: var(--accum-3) }
  --accum-4: calc(var(--accum-3) + var(--p-100-3)); :nth-child(4) { --accum: var(--accum-4) }
  /*...*/
}
```

Again, we first work at the pie level, where we compute one dedicated variable per slice. The first slice is a special case: there is no previous slice, so the accumulation is `0`. While in the rest, the accumulation for the slice `n` is the accumulation of the slices before `n−1` plus the value of the slice `n−1`.

```css
--accum-4: calc(var(--accum-3) + var(--p-100-3));
```

The fourth element gets `--accum-4`, the fifth element gets `--accum-5`, and so on. Just as the percentages, at the level of each slice, we assign them to the local variable `--accum`.

```css
:nth-child(4) {
  --accum: var(--accum-4);
}
```

Once again, we have all these slice accumulation values accessible at two levels:

- On the pie, via indexed variables: `--accum-1`, `--accum-2`, `--accum-3`
- On each slice, via the `--accum` variable

I hope future native CSS features (perhaps `@function`?) will prevent us from having to resort to such repetitive code. In the meantime, this can be simplified with a CSS preprocessor (Sass, Less).

While forking the original Pen, some questions popped out in my mind — that I did not actually explore — to keep the original code as unchanged as possible:

- What about using `<label>` and `<meter>` for labels and values?
- What about using a `<table>` (since charts are often extracted from tables with rows like `[label, value]`)?

### Note About Accessibility

In my fork, I handled accessibility the same way Juan did, but with one slight modification: I used [counter-reset](https://css-tricks.com/almanac/properties/c/counter-reset/) / [counter()](https://css-tricks.com/almanac/functions/c/counter/) instead of `attr()` to assign the percentages to the [content](https://css-tricks.com/almanac/properties/c/content/) property. This should work just as good as `attr()`, but let’s make sure it is still screenreader-friendly:

Another thing I thought of changing was the label elements inside each `<li>`. In the original article, Juan uses a `<strong>` element, while I opted for `<span>` instead. However, I think it may be totally acceptable to use the `<label>` itself. We normally think of them as being bounded inside `<form>` elements, but [the spec says](https://html.spec.whatwg.org/multipage/forms.html#the-label-element) that we could expect to use them in contexts “where phrasing content is expected.” So I could not find any obligation to use them only in the context of forms.

### Default Colors

Juan’s article also called upon [some improvements](https://css-tricks.com/trying-to-make-the-perfect-pie-chart-in-css/#aa-thats-about-it-for-now), which I tried to address in my fork:

- `data-color` can be omitted, and colors are then generated.
- Colors can be defined either on the parent or on the children (user’s choice; both are supported).

This translates to the next snippet for each slice:

```css
.pie-chart li {
  --color: attr(data-color type(<color>));
  --bg-color: var(--color, hsl(calc(360deg * sibling-index() / sibling-count()) 90% 40%));
}
```

I refrained from using `sibling-index()` and `sibling-count()` in the main part, since they aren’t Baseline (yet, but soon!), but I couldn’t hold myself back since calculating the color hue is so much fancier with them. These functions really allow some [magic](https://codepen.io/villepreux/pen/pvEjVdK)!

Still, here is my “CSS-only polyfill”; repetitive (yet simple) code:

```css
.pie-chart {
  :has(:nth-child(1)) { --sibling-count: 1 } :nth-child(1) { --sibling-index: 1; }
  :has(:nth-child(2)) { --sibling-count: 2 } :nth-child(2) { --sibling-index: 2; }
  :has(:nth-child(3)) { --sibling-count: 3 } :nth-child(3) { --sibling-index: 3; }
  :has(:nth-child(4)) { --sibling-count: 4 } :nth-child(4) { --sibling-index: 4; }
  /* ... */
}
```

### More Chart Types?

We now have a common foundation for other chart types. As a proof of concept, I implemented a bar chart mode in [my fork](https://codepen.io/villepreux/pen/KwMrGGa?editors=0100).

### A Web Component, Perhaps?

In a way, we already have a web component here — one without JavaScript, using [light DOM](https://meyerweb.com/eric/thoughts/2023/11/01/blinded-by-the-light-dom/).

And to me `<pie-char attributes...>` is not fundamentally different that either `<div class="pie chart" attributes...>` or `<div pie chart attributes...>`. I can see value in this approach when considering progressive enhancement, though.

For example, a chart that refreshes automatically and fetches live data. But that would require JavaScript — which we are deliberately avoiding today.
