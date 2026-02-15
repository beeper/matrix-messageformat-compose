package com.beeper.android.messageformat.desktop

internal const val EXAMPLE_MESSAGE = """
<h1>Matrix HTML Formatting Test</h1>
<p>This message demonstrates <b>bold</b>, <strong>strong</strong>, <i>italic</i>, <em>emphasized</em>, <u>underlined</u>, <s>strikethrough</s> and <code>@room inline code</code> with some non-code text after.</p>

<details><summary>Expandable tag here</summary>
<h2>Very long header very long header very long header very long header very long header very long header very long header very long header very long header very long header very long header very long header very long header very long header very long header very long header very long header very long header very long header very long header very long header very long header very long header very long header very long header</h2>
<p>collapsed stuff with inline image <img src='mxc://local.beeper.com/spiritcroc_TnTnE9q1fOXD0gtA9UlcBrStvCVLWwAbAtKcAIOKqWJRtQeZwsniGSRVA8xV51tB' alt='Example image' width='200' height='100' data-mx-emoticon>.</p>
<p><font data-mx-bg-color='#ff00ff'>Long text with background long text with background long text with background long text with background long text with background long text with background long text with background long text with background long text with background long text with background long</font></p>
<p>Spoiler <span data-mx-spoiler>alert! Link that shouldn't be clickable unless spoiler is revealed: https://beeper.com</span></p>
</details>

<pre><code>Multiline code
with a newline
and another one
and some url that shouldn't render as mention: https://matrix.to/#/@spiritcroc:matrix.org
same as @room pings

Some numbers with tabs from TWIM:
synapse	11679	11894	215	1.84%
c10y	450	546	96	21.33%
conduit	507	525	18	3.55%
dendri	380	365	-15	-3.95%
</code></pre>
<pre><code>synapse	11679	11894	215	1.84%</code></pre>
<pre><code>c10y	450	546	96	21.33%</code></pre>

<p>
@room hey <a href="https://matrix.to/#/@spiritcroc:matrix.org">Mention</a>, check out this <a href='https://matrix.org'>link</a>
<br>
<a href="https://matrix.to/#/@spiritcroc:beeper.com">Very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention very long mention</a>
</p>

<h2>Lists</h2>
<p>Unordered list:</p>
<ul>
  <li>First item</li>
  <li>Second item</li>
  <li>Third item with <b>bold</b> text</li>
  <li>List in list<ul><li>Inner item 1</li><li>Inner item 2</li></ul></li>
  <li>Another item</li>
  <li>List in list<ul><li>Inner item 1</li><li>Inner item 2<ul><li>Inner inner item 1</li><li>Inner inner item 2</li></ul></li></ul></li>
</ul>

<p>Ordered list:</p>
<ol>
  <li>Step one</li>
  <li>Step two</li>
  <li>Step three</li>
</ol>

<p>Now an unconventional ordered list:</p>
<ol start="5">
<li>test</li>
<li>test</li>
<li value="42">test</li>
<li>Very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very long item very</li>
<li>List in list<ol><li>Inner item 1</li><li>Inner item 2</li></ol></li>
</ol>

<h2>Blockquote</h2>
<blockquote>
  <p>“This is a quoted message.” — Someone on Matrix</p>
</blockquote>

<p>Text between quotes</p>

<blockquote>
    <blockquote>
      <p>Nested quote</p>
    </blockquote>
    <p>Not nested quote</p>
    <blockquote>
      <p>Nested quote</p>
        <blockquote>
          <p>Nested nested quote</p>
        </blockquote>
    </blockquote>
</blockquote>
<p>Not quote</p>

<blockquote>
    <blockquote>
      <p>Nested quote</p>
      <ul>
      <li>With</li>
      <li>a</li>
      <li>list</li>
      </ul>
    </blockquote>
    <p>Not nested quote</p>
    <blockquote>
      <p>Nested quote</p>
      <ol>
      <li>With</li>
      <li>ordered</li>
      <li>list</li>
      </ol>
      <h5>Header in the quote</h5>
      <p>more quoted stuff</p>
    </blockquote>
</blockquote>
<p>Not quote</p>

<h2>Color and Style</h2>
<p><font color='#ff0000'>Red text</font>, <font color='#00aa00'>green text</font>, and <span data-mx-color='#ffffff' data-mx-bg-color='#0000ff'>text on blue</span>.</p>
<p><font data-mx-bg-color='#ff00ff'>Long text with background long text with background long text with background long text with background long text with background long text with background long text with background long text with background long text with background long text with background long</font></p>
<p>Spoiler <span data-mx-spoiler>alert!</span></p>

<h2>Nested Elements</h2>
<p>This text <b><i><u>combines multiple styles</u></i></b> for testing nested formatting.</p>

<h2>Horizontal Rule</h2>
<hr>

<h2>Images</h2>
<p>Here an inline image <img src='mxc://local.beeper.com/spiritcroc_TnTnE9q1fOXD0gtA9UlcBrStvCVLWwAbAtKcAIOKqWJRtQeZwsniGSRVA8xV51tB' alt='Example image' width='200' height='100' data-mx-emoticon> using a Matrix content URI.</p>
<p>Have some GIFs too <img src='mxc://local.beeper-staging.com/spirit2_MMMenUsTgZdM9lbg2O6MW6SYB4h0MOw0uqMfRCrX0Xk0jKtRrbrvRqqccyOuf3Yd' alt='GIF1' width='200' height='100' data-mx-emoticon><img src='mxc://local.beeper-staging.com/spirit2_18PUFOHdg7rNExXLXgXv4Ws27fqRrSMSdLRVNQuIP0rUh7CM8qQqkUj9yFzvFDcA' alt='GIF2' width='100' height='100'> and a lottie <img src="mxc://local.beeper.com/spiritcroc_ROUkxDcXkLYjTTRJuI6OirQagO1VrIejupwDVgaKS2K7fykiz2BbRVaZq6sqwbmB" alt="LOTTIE"></p>

<h2>RTL text</h2>
<blockquote>
<p><span data-mx-bg-color='#00ffff'>
برخی از متن که به زبان فارسی است، اما من فارسی صحبت نمی کنم، بنابراین من برخی از انگلیسی به فارسی با استفاده از گوگل ترجمه، ترجمه شده است. من کاملا مطمئنم که ترجمه واقعا افتضاح است
</span>
</p>
</blockquote>

<h2>Paragraphs</h2>
<p>One paragraph</p>
<p>Second paragraph<br>with newline in between</p>
"""
