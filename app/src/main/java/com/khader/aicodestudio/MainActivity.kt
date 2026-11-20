package com.khader.aicodestudio

import android.app.Activity
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private val OPEN = 1
    private val SAVE = 2
    private val DEV = "khader abo salha"
    private val ORANGE = Color.parseColor("#FF6D00")
    private val AMBER = Color.parseColor("#FFB300")
    private val CARD = Color.parseColor("#1B1B2B")
    private val MUTED = Color.parseColor("#9A9AB0")

    private val common = " Reply with the COMPLETE updated code ONLY, no markdown fences. Put a short Arabic changelog as a comment at the very top."
    private val agents = listOf(
        Triple("🛠️", "خبير الأكواد", "You are an expert software engineer. Fix all bugs, compile errors, null-safety and performance issues and clean the code. Keep all features."),
        Triple("🎨", "خبير الرسوميات والتصميم", "You are an expert in graphics, UI/UX and visual design. Improve layout, colors, shaders, textures and UI polish. Do not break logic."),
        Triple("🎬", "خبير الكاميرا والمؤثرات", "You are an expert in camera movement, VFX and audio. Add or improve smooth camera behavior, particles, screen effects and sound hooks. Do not break logic."),
        Triple("🧠", "خبير المنطق", "You are an expert in game/app logic and architecture. Improve state, game loop, collisions, scoring, input, lifecycle and edge cases. Keep features."),
        Triple("⚖️", "الحَكَم · المراجعة النهائية", "You are the final judge and senior reviewer. Remove conflicts and duplicates between the specialists' edits, verify imports and permissions are consistent, and output the best final version.")
    )

    private lateinit var key: EditText
    private lateinit var model: EditText
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var go: Button
    private lateinit var bar: ProgressBar
    private lateinit var testMsg: TextView
    private lateinit var testBtn: Button
    private val chips = ArrayList<TextView>()
    private var result = ""
    private var loaded = false
    private var thinkCfg = true
    private lateinit var connCard: LinearLayout
    private var summary = ""
    private var zipOut = false
    private val files = LinkedHashMap<String, String>()
    private var outFiles = LinkedHashMap<String, String>()
    private lateinit var errView: TextView
    private val exts = setOf("kt","java","xml","gradle","kts","json","txt","md","properties","html","js","css","glsl","vert","frag","yml","yaml","toml","pro","cfg","ts","py","c","cpp","h","cs")
    private val singleMode = " Reply with the COMPLETE updated code ONLY, no markdown fences. Put a short Arabic changelog as a comment at the very top."
    private val projMode = " The input is a multi-file project; each file starts with a line '=== FILE: path ==='. Reply ONLY with files you modified or added, each preceded by the same marker line followed by the FULL new content of that file. Omit unchanged files. No markdown fences, no explanations."

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bg(color: Int, r: Int = 20, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat(); if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = bg(CARD, 22, Color.parseColor("#2A2A40"))
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(7), 0, dp(7)) }
    }

    private fun label(t: String) = TextView(this).apply { text = t; setTextColor(AMBER); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(8)) }

    private fun field(h: String, pass: Boolean = false) = EditText(this).apply {
        hint = h; setTextColor(Color.WHITE); setHintTextColor(MUTED); textSize = 13f
        background = bg(Color.parseColor("#12121D"), 14, Color.parseColor("#33334D")); setPadding(dp(12), dp(12), dp(12), dp(12))
        if (pass) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(8)) }
    }

    private fun btn(t: String, primary: Boolean = true, f: () -> Unit) = Button(this).apply {
        text = t; setTextColor(Color.WHITE); isAllCaps = false; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
        background = if (primary) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(ORANGE, AMBER)).apply { cornerRadius = dp(16).toFloat() }
        else bg(Color.parseColor("#26263A"), 16, Color.parseColor("#3A3A55"))
        setOnClickListener { f() }
        layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
    }

    private fun row(vararg v: android.view.View) = LinearLayout(this).apply { v.forEach { addView(it) } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.parseColor("#0B0B14")
        val sp = getSharedPreferences("s", MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(40), dp(16), dp(24)) }
        val sv = ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#0B0B14"), Color.parseColor("#1B1033")))
            addView(root)
        }

        root.addView(TextView(this).apply {
            text = "⚙️ الإعدادات"; setTextColor(Color.WHITE); textSize = 13f
            background = bg(Color.parseColor("#26263A"), 16, Color.parseColor("#3A3A55")); setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { connCard.visibility = if (connCard.visibility == android.view.View.GONE) android.view.View.VISIBLE else android.view.View.GONE }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END; setMargins(0, 0, 0, dp(8)) }
        })
        // الترويسة
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ORANGE, Color.parseColor("#C2185B"))).apply { cornerRadius = dp(28).toFloat() }
            setPadding(dp(16), dp(24), dp(16), dp(24))
            addView(TextView(this@MainActivity).apply { text = "⚡ AI Code Studio"; setTextColor(Color.WHITE); textSize = 26f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER })
            addView(TextView(this@MainActivity).apply { text = "٤ خبراء + حَكَم لإخراج أفضل كود"; setTextColor(Color.parseColor("#FFE0B2")); textSize = 13f; gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(10)) })
            addView(TextView(this@MainActivity).apply {
                text = "👨‍💻 المطور: $DEV"; setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER
                background = bg(Color.parseColor("#33000000"), 20); setPadding(dp(14), dp(6), dp(14), dp(6))
            })
        })

        // الإعدادات
        key = field("مفتاح Gemini API (AQ... أو AIza...)", true).apply { setText(sp.getString("k", "")) }
        model = field("النموذج").apply { setText(sp.getString("m", "gemini-flash-latest")) }
        testMsg = TextView(this).apply { text = "لم يتم الاختبار بعد"; setTextColor(MUTED); textSize = 12f; setPadding(dp(4), dp(6), dp(4), dp(2)) }
        testBtn = btn("🔍 فحص المفتاح") { testConnection(sp) }.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(2), 0, dp(2)) } }
        connCard = card().apply { addView(label("🔑 إعدادات المفتاح")); addView(key); addView(model); addView(testBtn); addView(testMsg) }
        connCard.visibility = if (key.text.isBlank()) android.view.View.VISIBLE else android.view.View.GONE
        root.addView(connCard)

        // الكود
        input = field("الصق الكود هنا أو ارفع ملفاً…").apply { minLines = 7; maxLines = 12; gravity = Gravity.TOP; typeface = Typeface.MONOSPACE }
        root.addView(card().apply {
            addView(label("📄 الكود")); addView(input)
            addView(row(
                btn("📂 رفع ملف", false) { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, OPEN) },
                btn("📋 لصق", false) {
                    val c = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                    if (c != null && c.itemCount > 0) input.setText(c.getItemAt(0).coerceToText(this@MainActivity))
                }))
        })

        go = btn("🚀 ابدأ المعالجة") { start(sp) }.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(0, dp(6), 0, dp(6)) } }
        root.addView(go)
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = agents.size; progressTintList = android.content.res.ColorStateList.valueOf(ORANGE) }
        root.addView(bar)

        // الخبراء
        root.addView(card().apply {
            errView = TextView(this@MainActivity).apply { setTextColor(Color.parseColor("#F44336")); textSize = 12f; visibility = android.view.View.GONE; setPadding(0, 0, 0, dp(8)) }
            addView(label("👥 فريق الخبراء")); addView(errView)
            agents.forEach { a ->
                val chip = TextView(this@MainActivity).apply { text = "في الانتظار"; textSize = 11f; setTextColor(MUTED); background = bg(Color.parseColor("#26263A"), 12); setPadding(dp(10), dp(4), dp(10), dp(4)) }
                chips.add(chip)
                addView(LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(7), 0, dp(7))
                    addView(TextView(this@MainActivity).apply { text = "${a.first}  ${a.second}"; setTextColor(Color.WHITE); textSize = 13.5f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
                    addView(chip)
                })
            }
        })

        // النتيجة
        output = TextView(this).apply { setTextColor(Color.parseColor("#E0E0F0")); setTextIsSelectable(true); typeface = Typeface.MONOSPACE; textSize = 11f; hint = "ستظهر النسخة النهائية هنا"; setHintTextColor(MUTED) }
        root.addView(card().apply {
            addView(label("✅ الكود النهائي")); addView(output)
            addView(row(
                btn("نسخ") { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("code", result)); toast("تم النسخ") },
                btn("💾 تحميل") {
                    if (result.isBlank()) toast("لا يوجد كود بعد") else startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = if (zipOut) "application/zip" else "text/plain"; putExtra(Intent.EXTRA_TITLE, if (zipOut) "final_project.zip" else "final_code.txt") }, SAVE)
                },
                btn("مشاركة", false) { if (result.isNotBlank()) startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, result) }, "مشاركة")) }))
        })

        root.addView(TextView(this).apply { text = "صُنع بـ ❤️ بواسطة $DEV"; setTextColor(MUTED); textSize = 12f; gravity = Gravity.CENTER; setPadding(0, dp(16), 0, 0) })
        setContentView(sv)
    }

    override fun onActivityResult(req: Int, res: Int, d: Intent?) {
        super.onActivityResult(req, res, d)
        val u: Uri = d?.data ?: return
        if (res != RESULT_OK) return
        try {
            if (req == OPEN) {
                val bytes = contentResolver.openInputStream(u)!!.use { it.readBytes() }
                if (bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) loadZip(bytes)
                else {
                    val t = String(bytes)
                    if (t.contains('\u0000') || t.contains('\uFFFD')) { toast("الملف ليس نصياً. ارفعه كملف كود أو zip"); return }
                    loaded = false; input.setText(t)
                }
            }
            if (req == SAVE) {
                contentResolver.openOutputStream(u)!!.use { o ->
                    if (zipOut) ZipOutputStream(o).use { z -> outFiles.forEach { (p, c) -> z.putNextEntry(ZipEntry(p)); z.write(c.toByteArray()); z.closeEntry() } }
                    else o.write(result.toByteArray())
                }
                toast("تم الحفظ")
            }
        } catch (e: Exception) { toast("خطأ: ${e.message}") }
    }

    private fun loadZip(bytes: ByteArray) {
        files.clear(); var total = 0; var skipped = 0
        val skip = Regex("(^|/)(build|\\.git|\\.gradle|\\.idea|node_modules)/")
        ZipInputStream(bytes.inputStream()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val n = e.name
                if (!e.isDirectory && !skip.containsMatchIn(n) && n.substringAfterLast('.', "").lowercase() in exts) {
                    val b = z.readBytes(); val t = String(b)
                    if (b.size <= 300000 && !t.contains('\u0000') && total + t.length <= 400000) { files[n] = t; total += t.length } else skipped++
                }
                e = z.nextEntry
            }
        }
        if (files.isEmpty()) { toast("لا توجد ملفات كود نصية داخل الـ zip"); return }
        summary = "📦 مشروع zip: ${files.size} ملف (${total / 1000} ألف حرف)" +
            (if (total > 150000) "\n⏱️ المشروع كبير وقد يستغرق وقتاً أطول" else "") +
            (if (skipped > 0) "\n⚠️ تم تجاوز $skipped ملف كبير أو ثنائي" else "") + "\n\n" + files.keys.joinToString("\n")
        loaded = true; input.setText(summary)
    }

    private fun pack(m: Map<String, String>) = m.entries.joinToString("\n") { "=== FILE: ${it.key} ===\n${it.value}" }

    private fun applyReply(reply: String, m: MutableMap<String, String>) {
        val ms = Regex("(?m)^=== FILE: (.+?) ===[ \\t]*$").findAll(reply).toList()
        if (ms.isEmpty()) {
            if (m.size == 1) m[m.keys.first()] = strip(reply) else throw Exception("رد غير مفهوم من الخبير")
            return
        }
        ms.forEachIndexed { i, x ->
            val end = if (i + 1 < ms.size) ms[i + 1].range.first else reply.length
            m[x.groupValues[1].trim()] = strip(reply.substring(x.range.last + 1, end))
        }
    }

    private fun cleanKey() = key.text.toString().filter { !it.isWhitespace() && it != '"' && it != '\'' }

    private fun testConnection(sp: SharedPreferences) {
        val k = cleanKey()
        val m = model.text.toString().trim().removePrefix("models/").ifBlank { "gemini-flash-latest" }
        if (k.length < 20) { testMsg.text = "❌ أدخل المفتاح أولاً"; testMsg.setTextColor(Color.parseColor("#F44336")); return }
        testBtn.isEnabled = false; testMsg.text = "⏳ جارٍ الاختبار…"; testMsg.setTextColor(AMBER)
        Thread {
            var ok = false
            val msg = try {
                val r = callRetry(k, m, "Reply with the single word OK.", "ping")
                ok = true
                sp.edit().putString("k", k).putString("m", m).putBoolean("v", true).apply()
                "✅ المفتاح صالح ومفعّل (النموذج: $m)"
            } catch (e: java.net.UnknownHostException) { "❌ لا يوجد اتصال بالإنترنت"
            } catch (e: Exception) {
                val t = e.message ?: ""
                when {
                    t.contains("HTTP 400") && t.contains("API key", true) -> "❌ المفتاح غير صالح"
                    t.contains("HTTP 400") -> "❌ طلب خاطئ: $t"
                    t.contains("HTTP 401") || t.contains("HTTP 403") -> "❌ المفتاح مرفوض أو غير مفعّل: $t"
                    t.contains("HTTP 404") -> "❌ النموذج غير موجود، غيّر اسمه"
                    t.contains("HTTP 429") -> "⚠️ المفتاح صحيح لكن تجاوزت الحصة، حاول لاحقاً"
                    else -> "❌ $t"
                }
            }
            runOnUiThread {
                testMsg.text = msg
                testMsg.setTextColor(if (ok || msg.startsWith("⚠️")) Color.parseColor("#4CAF50") else Color.parseColor("#F44336"))
                testBtn.isEnabled = true; if (ok) testMsg.postDelayed({ connCard.visibility = android.view.View.GONE }, 1500)
            }
        }.start()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun chip(i: Int, t: String, c: Int) = runOnUiThread { chips[i].text = t; chips[i].setTextColor(c) }

    private val judgeMode = " Input = ORIGINAL followed by PROPOSALS from specialists. Merge the best of ALL proposals into one final version, resolving conflicts and duplicates, keeping everything compilable."

    private fun start(sp: SharedPreferences) {
        val k = cleanKey()
        if (k.length < 20) { toast("أدخل مفتاح Gemini من الإعدادات ⚙️"); connCard.visibility = android.view.View.VISIBLE; return }
        val orig = LinkedHashMap<String, String>()
        val proj = loaded && input.text.toString() == summary
        if (proj) orig.putAll(files) else {
            val t = input.text.toString()
            if (t.isBlank()) { toast("أدخل الكود أولاً"); return }
            orig["code"] = t
        }
        val m = model.text.toString().trim().removePrefix("models/").ifBlank { "gemini-flash-latest" }
        sp.edit().putString("k", k).putString("m", m).apply()
        zipOut = proj
        go.isEnabled = false; output.text = ""; result = ""; bar.progress = 0; errView.visibility = android.view.View.GONE
        chips.forEachIndexed { i, _ -> chip(i, "في الانتظار", MUTED) }
        val green = Color.parseColor("#4CAF50"); val red = Color.parseColor("#F44336")
        Thread {
            val specs = agents.dropLast(1); val judge = agents.last(); val ji = specs.size
            val payload = if (proj) pack(orig) else orig["code"]!!
            val props = arrayOfNulls<Map<String, String>>(specs.size)
            val errs = arrayOfNulls<String>(specs.size)
            val done = java.util.concurrent.atomic.AtomicInteger(0)
            // الخبراء الأربعة يعملون بالتوازي لتسريع التحليل
            specs.mapIndexed { i, a ->
                Thread {
                    chip(i, "جارٍ العمل…", AMBER)
                    try {
                        val w = LinkedHashMap(orig)
                        applyReply(callRetry(k, m, a.third + (if (proj) projMode else singleMode), payload) { n -> chip(i, "جارٍ العمل… $n", AMBER) }, w)
                        props[i] = w; chip(i, "تم ✓", green)
                    } catch (e: Exception) { errs[i] = e.message; chip(i, "فشل ✗", red) }
                    runOnUiThread { bar.progress = done.incrementAndGet() }
                }.also { it.start() }
            }.forEach { it.join() }
            val ok = props.indices.filter { props[it] != null }
            try {
                if (ok.isEmpty()) throw Exception(errs.firstOrNull { it != null } ?: "فشل جميع الخبراء")
                val sb = StringBuilder("=== ORIGINAL ===\n$payload")
                ok.forEach { i ->
                    val w = props[i]!!
                    sb.append("\n\n##### PROPOSAL: ${specs[i].second} #####\n").append(if (proj) pack(w.filter { orig[it.key] != it.value }) else w["code"])
                }
                val fin = LinkedHashMap(orig)
                chip(ji, "جارٍ المراجعة…", AMBER)
                try {
                    applyReply(callRetry(k, m, judge.third + judgeMode + (if (proj) projMode else singleMode), sb.toString()) { n -> chip(ji, "جارٍ المراجعة… $n", AMBER) }, fin)
                    chip(ji, "تم ✓", green)
                } catch (e: Exception) {
                    ok.forEach { i -> props[i]!!.forEach { (f, v) -> if (orig[f] != v) fin[f] = v } }
                    chip(ji, "دمج تلقائي", AMBER)
                }
                runOnUiThread { bar.progress = agents.size }
                outFiles = fin
                result = if (proj) pack(fin) else fin["code"]!!
                val shown = if (result.length > 15000) result.take(15000) + "\n\n…(مقتطع للعرض، استخدم زر التحميل للنسخة الكاملة)" else result
                val failed = specs.indices.filter { props[it] == null }.joinToString("، ") { specs[it].second }
                runOnUiThread {
                    output.text = shown; toast("اكتملت المعالجة ✅")
                    if (failed.isNotEmpty()) { errView.text = "⚠️ تعذّر على: $failed (${errs.firstOrNull { it != null }})"; errView.visibility = android.view.View.VISIBLE }
                }
            } catch (e: Exception) {
                chip(ji, "فشل ✗", red)
                runOnUiThread { errView.text = "⚠️ فشل: ${e.message}"; errView.visibility = android.view.View.VISIBLE }
            }
            runOnUiThread { go.isEnabled = true }
        }.start()
    }

    private fun callRetry(k: String, m: String, sys: String, user: String, prog: ((Int) -> Unit)? = null): String {
        var n = 0
        while (true) {
            try { return call(k, m, sys, user, prog) }
            catch (e: Exception) {
                val t = e.message ?: ""
                if (t.contains("HTTP 400") && t.contains("think", true) && thinkCfg) { thinkCfg = false; continue }
                n++
                val retry = e is java.net.SocketTimeoutException || t.contains("HTTP 429") || t.contains("HTTP 500") || t.contains("HTTP 503")
                if (n < 3 && retry) Thread.sleep(4000L * n)
                else if (e is java.net.SocketTimeoutException) throw Exception("انتهت المهلة: تعذّر الاتصال بخوادم Google. تحقق من الإنترنت أو أوقف/بدّل الـ VPN ثم اضغط «اختبار الاتصال»")
                else if (e is java.net.UnknownHostException) throw Exception("لا يوجد اتصال بالإنترنت")
                else throw e
            }
        }
    }

    private fun strip(s: String): String {
        val t = s.trim()
        return if (t.startsWith("```")) t.substringAfter("\n").removeSuffix("```").trim() else t
    }

    // بث مباشر (SSE): يدعم المفاتيح الحديثة AQ. والقديمة AIza عبر ترويسة x-goog-api-key
    private fun call(k: String, m: String, sys: String, user: String, prog: ((Int) -> Unit)? = null): String {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 65536).apply { if (thinkCfg) put("thinkingConfig", JSONObject().put("thinkingBudget", 1024)) })
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$m:streamGenerateContent?alt=sse").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20000; c.readTimeout = 180000
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("x-goog-api-key", k)
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        if (c.responseCode !in 200..299) {
            val txt = c.errorStream?.bufferedReader()?.readText() ?: ""
            val msg = try { JSONObject(txt).getJSONObject("error").getString("message") } catch (e: Exception) { "" }
            throw Exception("HTTP ${c.responseCode} ${msg.take(160)}")
        }
        val sb = StringBuilder()
        c.inputStream.bufferedReader().use { r ->
            while (true) {
                val line = r.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val j = line.removePrefix("data:").trim()
                if (j.isEmpty()) continue
                val parts = JSONObject(j).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: continue
                for (i in 0 until parts.length()) { val p = parts.getJSONObject(i); if (!p.optBoolean("thought")) sb.append(p.optString("text")) }
                prog?.invoke(sb.length)
            }
        }
        if (sb.isEmpty()) throw Exception("رد فارغ من النموذج")
        return sb.toString()
    }
}
