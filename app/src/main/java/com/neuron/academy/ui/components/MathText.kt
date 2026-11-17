package com.neuron.academy.ui.components

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Текст с формулами. Если в тексте есть LaTeX (\( \), \[ \], $ $) — рендерим через KaTeX
 * (файлы лежат в assets/katex и работают офлайн). Иначе — обычный Text с простым markdown.
 */
@Composable
fun MathText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = LocalContentColor.current,
) {
    val hasMath = remember(text) { text.contains("\\(") || text.contains("\\[") || text.contains('$') }
    if (!hasMath) {
        Text(markdownToAnnotated(text), modifier = modifier, style = style, color = color)
    } else {
        KatexView(text, modifier, style.fontSize.value.takeIf { !it.isNaN() } ?: 16f, color)
    }
}

fun markdownToAnnotated(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.trim().lines()
    lines.forEachIndexed { i, rawLine ->
        var line = rawLine.trimEnd()
        var header = false
        when {
            line.startsWith("### ") -> { header = true; line = line.removePrefix("### ") }
            line.startsWith("## ") -> { header = true; line = line.removePrefix("## ") }
            line.startsWith("# ") -> { header = true; line = line.removePrefix("# ") }
            line.startsWith("- ") || line.startsWith("* ") -> line = "• " + line.drop(2)
        }
        if (header) pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp))
        val parts = line.split("**")
        parts.forEachIndexed { idx, part ->
            if (idx % 2 == 1) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(part); pop()
            } else append(part)
        }
        if (header) pop()
        if (i < lines.lastIndex) append("\n")
    }
}

private fun escapeHtml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** Простейшее преобразование markdown → HTML, не ломающее формулы. */
fun markdownToHtml(src: String): String {
    // Склеиваем многострочные display-формулы, чтобы <br> не разорвал их
    var text = Regex("""\\\[(.*?)\\\]""", RegexOption.DOT_MATCHES_ALL).replace(src) { "\\[" + it.groupValues[1].replace('\n', ' ') + "\\]" }
    text = Regex("""\$\$(.*?)\$\$""", RegexOption.DOT_MATCHES_ALL).replace(text) { "$$" + it.groupValues[1].replace('\n', ' ') + "$$" }
    val sb = StringBuilder()
    text.trim().lines().forEach { raw ->
        var line = escapeHtml(raw.trimEnd())
        line = Regex("""\*\*(.+?)\*\*""").replace(line) { "<b>${it.groupValues[1]}</b>" }
        when {
            line.isBlank() -> sb.append("<div class='sp'></div>")
            line.startsWith("### ") || line.startsWith("## ") || line.startsWith("# ") ->
                sb.append("<h4>").append(line.trimStart('#', ' ')).append("</h4>")
            line.startsWith("- ") || line.startsWith("* ") -> sb.append("<div class='li'>• ").append(line.drop(2)).append("</div>")
            else -> sb.append("<div>").append(line).append("</div>")
        }
    }
    return sb.toString()
}

private fun cssColor(c: Color): String {
    val argb = c.toArgb()
    return "rgba(${(argb shr 16) and 0xFF},${(argb shr 8) and 0xFF},${argb and 0xFF},${((argb ushr 24) and 0xFF) / 255f})"
}

private fun buildHtml(text: String, color: Color, fontSize: Float): String {
    val d = "$"
    return """
<!DOCTYPE html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
<link rel="stylesheet" href="katex.min.css">
<script src="katex.min.js"></script>
<script src="auto-render.min.js"></script>
<style>
html,body{margin:0;padding:0;background:transparent;color:${cssColor(color)};font-family:sans-serif;font-size:${fontSize}px;line-height:1.5;word-wrap:break-word;}
#c{padding:2px 0;}
.katex-display{overflow-x:auto;overflow-y:hidden;margin:.4em 0;padding:2px 0;}
.katex{font-size:1.08em;}
h4{margin:.5em 0 .2em;font-size:1.12em;}
.li{padding-left:.3em;}
.sp{height:.5em;}
</style></head><body><div id="c">${markdownToHtml(text)}</div>
<script>
function report(){ try { Android.onHeight(Math.ceil(document.getElementById('c').getBoundingClientRect().height) + 6); } catch(e){} }
try {
  renderMathInElement(document.getElementById('c'), {
    delimiters: [
      {left: '$d$d', right: '$d$d', display: true},
      {left: '\\[', right: '\\]', display: true},
      {left: '\\(', right: '\\)', display: false},
      {left: '$d', right: '$d', display: false}
    ],
    throwOnError: false, strict: false
  });
} catch (e) {}
report();
if (document.fonts) { document.fonts.ready.then(report); }
setTimeout(report, 250); setTimeout(report, 800);
window.addEventListener('resize', report);
</script></body></html>
"""
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KatexView(text: String, modifier: Modifier, fontSize: Float, color: Color) {
    val height = remember { mutableIntStateOf(24) }
    val html = remember(text, color, fontSize) { buildHtml(text, color, fontSize) }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onHeight(h: Int) {
                        post { if (h > 0) height.intValue = h }
                    }
                }, "Android")
            }
        },
        update = { wv ->
            if (wv.tag != html) {
                wv.tag = html
                wv.loadDataWithBaseURL("file:///android_asset/katex/", html, "text/html", "utf-8", null)
            }
        },
        modifier = modifier.fillMaxWidth().height(height.intValue.dp),
    )
}
