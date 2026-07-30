package org.linphone.shiroikuma

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.linphone.BuildConfig
import org.linphone.R

/**
 * shiroikuma-rindenwa fork — the full-app export/import engine (Kōjiki-style).
 *
 * Format: a ZIP with a `manifest.json` plus one entry per category. The Linphone SDK keeps
 * everything that matters in two places, so that is what the categories map onto:
 *
 *  * **`.linphonerc`** — an INI file holding both the accounts (`proxy_*`, `auth_info_*`,
 *    `nat_policy_*` sections) and every SIP/media setting. We split it by section so accounts
 *    restore independently of settings.
 *  * **the SDK databases** in `filesDir` (`linphone.db` and friends) — call history and
 *    conversations, copied as raw entries with their `-wal`/`-shm` siblings.
 *
 * plus our own appearance preferences and imported font files.
 *
 * Import is partial by category: absent entries are skipped, preferences merge (never clear),
 * and restored config/database files need an app restart — which the panel offers.
 *
 * The export directory (SAF tree URI) lives in its own device-local prefs file, deliberately
 * outside the exported preference set.
 */
object SkEximport {
    private const val FORMAT = "rindenwa-export"
    private const val FORMAT_VERSION = 1
    private const val PREFS_NAME = "sk_eximport"
    private const val KEY_DIR_URI = "dir_uri"

    /**
     * The family backup-name convention (白い熊, 2026-07-25): every app writes
     * `<english-app-name>_<yyyy-MM-dd_HH-mm-ss>.zip` — no version, no infix, no suffix — so all
     * apps' backups sort and read uniformly in one directory.
     */
    const val EXPORT_PREFIX = "shiroikuma-rindenwa_"

    private const val ACCOUNTS_ENTRY = "accounts.rc"
    private const val SIP_SETTINGS_ENTRY = "sip_settings.rc"
    private const val DB_DIR_ENTRY = "db/"
    private const val FONTS_DIR_ENTRY = "fonts/"

    /** linphonerc sections that constitute "the accounts and their full configuration". */
    private val ACCOUNT_SECTION_PREFIXES = listOf("proxy_", "auth_info_", "nat_policy_")

    /** SDK database basenames, matched case-insensitively with their -wal/-shm siblings. */
    private val DB_EXTENSIONS = listOf(".db", ".sqlite3", ".sqlite")

    /** Databases holding call history vs. conversations — the `history` sub-options. */
    private val CHAT_DB_HINTS = listOf("chat", "message", "lime", "x3dh", "encryption")

    /**
     * Export categories. A category with selectable parts lists each part as a child; the
     * automation contract's `items` extra accepts every id here, children included.
     *
     * [default] is the fourth field of a `LIST_CATEGORIES` line — whether the item starts ticked in
     * a picker drawn from our reply. Everything here is `true`: the flag exists for things that are
     * large, derived *and* re-creatable (downloaded tiles, a regenerable thumbnail cache), and this
     * app exports none of those. Stating it anyway is the point — the app declares the default
     * rather than leaving the picker to assume one, and anything added later inherits the field.
     */
    enum class Cat(
        val id: String,
        val labelRes: Int,
        val parent: String? = null,
        val default: Boolean = true,
    ) {
        ACCOUNTS("accounts", R.string.sk_eim_cat_accounts),
        HISTORY("history", R.string.sk_eim_cat_history),
        HISTORY_CALLS("history.calls", R.string.sk_eim_cat_history_calls, parent = "history"),
        HISTORY_CHAT("history.chat", R.string.sk_eim_cat_history_chat, parent = "history"),
        SIP_SETTINGS("sip_settings", R.string.sk_eim_cat_sip_settings),
        APPEARANCE("appearance", R.string.sk_eim_cat_appearance),
        APPEARANCE_FONTS("appearance.fonts", R.string.sk_eim_cat_appearance_fonts, parent = "appearance"),
        APP_SETTINGS("app_settings", R.string.sk_eim_cat_settings),
        ;

        val isChild: Boolean get() = parent != null

        companion object {
            /** The ids accepted in the automation contract's `items` extra. */
            fun byId(id: String): Cat? = entries.firstOrNull { it.id == id }

            fun topLevel(): List<Cat> = entries.filter { !it.isChild }

            fun childrenOf(cat: Cat): List<Cat> = entries.filter { it.parent == cat.id }
        }
    }

    // ------------------------------------------------------------- directory

    private fun eximPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun dirUri(context: Context): Uri? =
        eximPrefs(context).getString(KEY_DIR_URI, null)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun setDirUri(context: Context, uri: Uri) {
        eximPrefs(context).edit().putString(KEY_DIR_URI, uri.toString()).apply()
    }

    fun exportDir(context: Context): DocumentFile? =
        dirUri(context)
            ?.let { runCatching { DocumentFile.fromTreeUri(context, it) }.getOrNull() }
            ?.takeIf { it.isDirectory }

    private fun isExportFile(name: String?): Boolean =
        name != null && name.endsWith(".zip") && name.startsWith(EXPORT_PREFIX)

    fun latestExport(context: Context): DocumentFile? {
        val dir = exportDir(context) ?: return null
        return runCatching {
            dir.listFiles().filter { it.isFile && isExportFile(it.name) }
                .maxByOrNull { it.lastModified() }
        }.getOrNull()
    }

    /** (message, isWarning) for the "last export" status line — queried when the page opens. */
    fun lastExportStatus(context: Context): Pair<String, Boolean> {
        if (exportDir(context) == null) {
            return context.getString(R.string.sk_eim_warn_nodir) to true
        }
        val newest = latestExport(context)
            ?: return context.getString(R.string.sk_eim_warn_none) to true
        return context.getString(R.string.sk_eim_last, fmtTs(newest.lastModified())) to false
    }

    private fun fmtTs(t: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(t))

    fun exportFileName(): String =
        EXPORT_PREFIX + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

    // ------------------------------------------------------------- headless target

    /**
     * Where a headless export writes: the automation contract's absolute-directory override, or
     * the app's own configured SAF directory. Everything the caller needs to write, size and —
     * when the export fails halfway — drop the partial file again.
     */
    class Target(
        val displayPath: String,
        val open: () -> OutputStream,
        val size: () -> Long,
        val discard: () -> Unit,
    )

    /**
     * Directory precedence for a headless export: [pathOverride] (absolute, created if missing) →
     * the configured export directory → null, which the caller reports as `ERROR:no-directory`.
     */
    fun headlessTarget(context: Context, pathOverride: String): Target? {
        val name = exportFileName()
        if (pathOverride.isNotEmpty()) {
            // /sdcard is a symlink — normalize it so the reported path is the real one.
            val primary = Environment.getExternalStorageDirectory().absolutePath
            val dir = File(pathOverride.replaceFirst(Regex("^/sdcard"), primary))
            dir.mkdirs()
            if (!dir.isDirectory) throw IOException("not a directory: $pathOverride")
            val file = File(dir, name)
            return Target(
                displayPath = file.absolutePath,
                open = { FileOutputStream(file) },
                size = { file.length() },
                discard = { runCatching { file.delete() } },
            )
        }

        val dir = exportDir(context) ?: return null
        val doc = dir.createFile("application/zip", name)
            ?: throw IOException("cannot create $name in ${dir.name}")
        return Target(
            displayPath = displayPathOf(doc.uri),
            open = {
                context.contentResolver.openOutputStream(doc.uri)
                    ?: throw IOException("cannot open ${doc.uri}")
            },
            size = { doc.length() },
            discard = { runCatching { doc.delete() } },
        )
    }

    /**
     * Best-effort filesystem path for a SAF document (`primary:〇/x.zip` →
     * `/storage/emulated/0/〇/x.zip`), so an automation reply names a path 白い熊 can open.
     */
    private fun displayPathOf(uri: Uri): String {
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return uri.toString()
        val volume = docId.substringBefore(':', "")
        val relative = docId.substringAfter(':', "")
        if (volume.isEmpty() || relative.isEmpty()) return uri.toString()
        val root = if (volume == "primary") {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volume"
        }
        return "$root/$relative"
    }

    // ------------------------------------------------------------- linphonerc splitting

    private fun configFile(context: Context): File =
        File(context.filesDir, ".linphonerc")

    /**
     * Flush the SDK's in-memory config to disk before we read it, so a just-changed account is in
     * the backup. Safe when the Core isn't up yet — we simply export what is on disk.
     */
    private fun syncConfig() {
        runCatching {
            org.linphone.LinphoneApplication.corePreferences.config.sync()
        }
    }

    /** Split an INI file into (sectionName -> raw section text, header included). */
    private fun readSections(file: File): LinkedHashMap<String, String> {
        val sections = LinkedHashMap<String, String>()
        if (!file.isFile) return sections
        var current: String? = null
        val buffer = StringBuilder()
        file.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                current?.let { sections[it] = buffer.toString() }
                current = trimmed.removeSurrounding("[", "]")
                buffer.setLength(0)
                buffer.append(line).append('\n')
            } else if (current != null) {
                buffer.append(line).append('\n')
            }
        }
        current?.let { sections[it] = buffer.toString() }
        return sections
    }

    private fun isAccountSection(name: String): Boolean =
        ACCOUNT_SECTION_PREFIXES.any { name.startsWith(it) }

    private fun writeSections(target: File, incoming: Map<String, String>) {
        // Merge into whatever is already on disk: replace matching sections, keep the rest.
        val existing = readSections(target)
        for ((name, body) in incoming) {
            existing[name] = body
        }
        target.writeText(existing.values.joinToString(""))
    }

    // ------------------------------------------------------------- databases

    private fun dbFiles(context: Context, chat: Boolean?): List<File> {
        val all = context.filesDir.listFiles()?.filter { file ->
            file.isFile && DB_EXTENSIONS.any { ext ->
                file.name.lowercase().substringBefore("-wal").substringBefore("-shm").endsWith(ext)
            }
        }.orEmpty()
        if (chat == null) return all
        return all.filter { file ->
            val isChat = CHAT_DB_HINTS.any { file.name.lowercase().contains(it) }
            isChat == chat
        }
    }

    /** Files backing the selected [cats], de-duplicated (a parent implies both children). */
    private fun selectedDbFiles(context: Context, cats: Set<Cat>): List<File> {
        val wantCalls = Cat.HISTORY_CALLS in cats || (Cat.HISTORY in cats && !anyChildSelected(cats))
        val wantChat = Cat.HISTORY_CHAT in cats || (Cat.HISTORY in cats && !anyChildSelected(cats))
        val files = LinkedHashSet<File>()
        if (wantCalls) files += dbFiles(context, chat = false)
        if (wantChat) files += dbFiles(context, chat = true)
        return files.toList()
    }

    private fun anyChildSelected(cats: Set<Cat>): Boolean =
        Cat.HISTORY_CALLS in cats || Cat.HISTORY_CHAT in cats

    // ------------------------------------------------------------- export

    /** Live progress: [done] of [total] items, currently working on [stage]. */
    fun interface ProgressListener {
        fun onProgress(done: Int, total: Int, stage: String)
    }

    /** Cancellation = worker-thread interrupt (the panel's Cancel button). */
    private fun checkCancelled() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("cancelled")
        }
    }

    /** Write a ZIP of the selected categories to [out]. Returns a multi-line human summary. */
    fun export(
        context: Context,
        cats: Set<Cat>,
        out: OutputStream,
        listener: ProgressListener = ProgressListener { _, _, _ -> },
    ): String {
        syncConfig()
        val sections = readSections(configFile(context))
        val accountSections = sections.filterKeys { isAccountSection(it) }
        val settingSections = sections.filterKeys { !isAccountSection(it) }
        val dbs = selectedDbFiles(context, cats)
        val fontFiles =
            if (wantsFonts(cats)) {
                SkFonts.fontsDir(context).listFiles()?.filter { it.isFile }.orEmpty()
            } else {
                emptyList()
            }

        val accountCount = if (Cat.ACCOUNTS in cats) countAccounts(accountSections) else 0
        val settingCount = if (Cat.SIP_SETTINGS in cats) settingSections.size else 0
        val appearanceKeys =
            if (Cat.APPEARANCE in cats) countPrefs(defaultPrefs(context)) { it.startsWith("sk_") } else 0
        val settingsKeys =
            if (Cat.APP_SETTINGS in cats) countPrefs(defaultPrefs(context)) { !it.startsWith("sk_") } else 0
        val total = accountCount + dbs.size + settingCount + appearanceKeys + fontFiles.size + settingsKeys
        var done = 0

        val parts = mutableListOf<String>()
        ZipOutputStream(out).use { zip ->
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("version", FORMAT_VERSION)
                .put("app", context.packageName)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("createdTs", System.currentTimeMillis())
                .put("categories", JSONArray(cats.map { it.id }))
            writeEntry(zip, "manifest.json", manifest.toString(2).toByteArray())

            if (Cat.ACCOUNTS in cats) {
                checkCancelled()
                val label = context.getString(Cat.ACCOUNTS.labelRes)
                listener.onProgress(done, total, label)
                writeEntry(zip, ACCOUNTS_ENTRY, accountSections.values.joinToString("").toByteArray())
                done += accountCount
                listener.onProgress(done, total, label)
                parts += "$label: " + context.getString(R.string.sk_eim_accounts_count, accountCount)
            }

            if (dbs.isNotEmpty()) {
                val label = context.getString(Cat.HISTORY.labelRes)
                for (db in dbs) {
                    checkCancelled()
                    listener.onProgress(done, total, label)
                    writeEntry(zip, DB_DIR_ENTRY + db.name, db.readBytes())
                    done++
                }
                listener.onProgress(done, total, label)
                parts += "$label: " + context.getString(R.string.sk_eim_files_count, dbs.size)
            }

            if (Cat.SIP_SETTINGS in cats) {
                checkCancelled()
                val label = context.getString(Cat.SIP_SETTINGS.labelRes)
                listener.onProgress(done, total, label)
                writeEntry(zip, SIP_SETTINGS_ENTRY, settingSections.values.joinToString("").toByteArray())
                done += settingCount
                listener.onProgress(done, total, label)
                parts += "$label: " + context.getString(R.string.sk_eim_sections_count, settingCount)
            }

            if (Cat.APPEARANCE in cats) {
                checkCancelled()
                val label = context.getString(Cat.APPEARANCE.labelRes)
                listener.onProgress(done, total, label)
                val json = exportPrefs(defaultPrefs(context)) { it.startsWith("sk_") }
                writeEntry(zip, "appearance.json", json.first.toByteArray())
                done += appearanceKeys
                listener.onProgress(done, total, label)
                parts += "$label: " + context.getString(R.string.sk_eim_prefs_count, json.second)
            }

            if (fontFiles.isNotEmpty()) {
                val label = context.getString(Cat.APPEARANCE_FONTS.labelRes)
                for (font in fontFiles) {
                    checkCancelled()
                    listener.onProgress(done, total, label)
                    writeEntry(zip, FONTS_DIR_ENTRY + font.name, font.readBytes())
                    done++
                }
                listener.onProgress(done, total, label)
                parts += "$label: " + context.getString(R.string.sk_eim_fonts_count, fontFiles.size)
            }

            if (Cat.APP_SETTINGS in cats) {
                checkCancelled()
                val label = context.getString(Cat.APP_SETTINGS.labelRes)
                listener.onProgress(done, total, label)
                val json = exportPrefs(defaultPrefs(context)) { !it.startsWith("sk_") }
                writeEntry(zip, "app_settings.json", json.first.toByteArray())
                done += settingsKeys
                listener.onProgress(done, total, label)
                parts += "$label: " + context.getString(R.string.sk_eim_prefs_count, json.second)
            }
        }
        return parts.joinToString("\n")
    }

    /**
     * Per the automation contract, a parent id without its children means "that category's own
     * data only" — so `appearance` alone is the preference set, and the font files ride on
     * `appearance.fonts`.
     */
    private fun wantsFonts(cats: Set<Cat>): Boolean = Cat.APPEARANCE_FONTS in cats

    /** How many accounts the config describes — one per `proxy_*` section. */
    private fun countAccounts(accountSections: Map<String, String>): Int =
        accountSections.keys.count { it.startsWith("proxy_") }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    // ------------------------------------------------------------- import

    /** The known category ids present in [zipBytes]; empty = not one of our exports. */
    fun categoriesIn(zipBytes: ByteArray): Set<Cat> {
        val files = runCatching { readZip(zipBytes) }.getOrNull() ?: return emptySet()
        val manifest = files["manifest.json"]
            ?.let { runCatching { JSONObject(it.decodeToString()) }.getOrNull() }
            ?: return emptySet()
        if (manifest.optString("format") != FORMAT) return emptySet()
        val present = mutableSetOf<Cat>()
        if (files.containsKey(ACCOUNTS_ENTRY)) present += Cat.ACCOUNTS
        if (files.keys.any { it.startsWith(DB_DIR_ENTRY) }) present += Cat.HISTORY
        if (files.containsKey(SIP_SETTINGS_ENTRY)) present += Cat.SIP_SETTINGS
        if (files.containsKey("appearance.json")) present += Cat.APPEARANCE
        if (files.keys.any { it.startsWith(FONTS_DIR_ENTRY) }) present += Cat.APPEARANCE_FONTS
        if (files.containsKey("app_settings.json")) present += Cat.APP_SETTINGS
        return present
    }

    /**
     * Apply the selected categories from a ZIP. Missing entries are skipped. Returns a multi-line
     * human summary; throws if nothing at all could be applied.
     *
     * Config and database files are restored in place — the Core reads them at startup, so the
     * panel's "Restart now" is what actually brings them live.
     */
    fun import(
        context: Context,
        zipBytes: ByteArray,
        cats: Set<Cat>,
        listener: ProgressListener = ProgressListener { _, _, _ -> },
    ): String {
        val files = readZip(zipBytes)

        val dbEntries = if (wantsHistory(cats)) {
            files.keys.filter { it.startsWith(DB_DIR_ENTRY) }.filter { name ->
                val base = name.removePrefix(DB_DIR_ENTRY).lowercase()
                val isChat = CHAT_DB_HINTS.any { base.contains(it) }
                when {
                    Cat.HISTORY in cats && !anyChildSelected(cats) -> true
                    isChat -> Cat.HISTORY_CHAT in cats
                    else -> Cat.HISTORY_CALLS in cats
                }
            }
        } else {
            emptyList()
        }
        val fontEntries =
            if (wantsFonts(cats)) files.keys.filter { it.startsWith(FONTS_DIR_ENTRY) } else emptyList()
        val appearanceKeys = if (Cat.APPEARANCE in cats) {
            files["appearance.json"]?.let { countJsonKeys(it) { k -> k.startsWith("sk_") } } ?: 0
        } else {
            0
        }
        val settingsKeys = if (Cat.APP_SETTINGS in cats) {
            files["app_settings.json"]?.let { countJsonKeys(it) { k -> !k.startsWith("sk_") } } ?: 0
        } else {
            0
        }
        val total = (if (Cat.ACCOUNTS in cats) 1 else 0) + dbEntries.size +
            (if (Cat.SIP_SETTINGS in cats) 1 else 0) + appearanceKeys + fontEntries.size + settingsKeys
        var done = 0

        val parts = mutableListOf<String>()
        syncConfig()

        if (Cat.ACCOUNTS in cats) {
            files[ACCOUNTS_ENTRY]?.let { bytes ->
                checkCancelled()
                val label = context.getString(Cat.ACCOUNTS.labelRes)
                listener.onProgress(done, total, label)
                val incoming = parseSections(bytes.decodeToString())
                writeSections(configFile(context), incoming)
                done++
                listener.onProgress(done, total, label)
                parts += "$label: " +
                    context.getString(R.string.sk_eim_accounts_count, countAccounts(incoming))
            }
        }

        if (dbEntries.isNotEmpty()) {
            val label = context.getString(Cat.HISTORY.labelRes)
            for (name in dbEntries) {
                checkCancelled()
                listener.onProgress(done, total, label)
                val base = File(name).name // basename only — no path traversal
                if (base.isNotEmpty()) {
                    files[name]?.let { File(context.filesDir, base).writeBytes(it) }
                }
                done++
            }
            listener.onProgress(done, total, label)
            parts += "$label: " + context.getString(R.string.sk_eim_files_count, dbEntries.size)
        }

        if (Cat.SIP_SETTINGS in cats) {
            files[SIP_SETTINGS_ENTRY]?.let { bytes ->
                checkCancelled()
                val label = context.getString(Cat.SIP_SETTINGS.labelRes)
                listener.onProgress(done, total, label)
                val incoming = parseSections(bytes.decodeToString())
                writeSections(configFile(context), incoming)
                done++
                listener.onProgress(done, total, label)
                parts += "$label: " +
                    context.getString(R.string.sk_eim_sections_count, incoming.size)
            }
        }

        if (Cat.APPEARANCE in cats) {
            files["appearance.json"]?.let { bytes ->
                checkCancelled()
                val label = context.getString(Cat.APPEARANCE.labelRes)
                listener.onProgress(done, total, label)
                val applied = importPrefs(defaultPrefs(context), bytes.decodeToString()) {
                    it.startsWith("sk_")
                }
                done += appearanceKeys
                listener.onProgress(done, total, label)
                parts += "$label: " + context.getString(R.string.sk_eim_prefs_count, applied)
            }
        }

        if (fontEntries.isNotEmpty()) {
            val label = context.getString(Cat.APPEARANCE_FONTS.labelRes)
            for (name in fontEntries) {
                checkCancelled()
                listener.onProgress(done, total, label)
                val base = File(name).name
                if (base.isNotEmpty()) {
                    files[name]?.let { File(SkFonts.fontsDir(context), base).writeBytes(it) }
                }
                done++
            }
            SkFonts.invalidateCache()
            listener.onProgress(done, total, label)
            parts += "$label: " + context.getString(R.string.sk_eim_fonts_count, fontEntries.size)
        }

        if (Cat.APP_SETTINGS in cats) {
            files["app_settings.json"]?.let { bytes ->
                checkCancelled()
                val label = context.getString(Cat.APP_SETTINGS.labelRes)
                listener.onProgress(done, total, label)
                val applied = importPrefs(defaultPrefs(context), bytes.decodeToString()) {
                    !it.startsWith("sk_")
                }
                done += settingsKeys
                listener.onProgress(done, total, label)
                parts += "$label: " + context.getString(R.string.sk_eim_prefs_count, applied)
            }
        }

        if (parts.isEmpty()) {
            throw IOException("no category could be applied")
        }
        return parts.joinToString("\n")
    }

    private fun wantsHistory(cats: Set<Cat>): Boolean =
        Cat.HISTORY in cats || anyChildSelected(cats)

    private fun parseSections(text: String): LinkedHashMap<String, String> {
        val sections = LinkedHashMap<String, String>()
        var current: String? = null
        val buffer = StringBuilder()
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                current?.let { sections[it] = buffer.toString() }
                current = trimmed.removeSurrounding("[", "]")
                buffer.setLength(0)
                buffer.append(line).append('\n')
            } else if (current != null) {
                buffer.append(line).append('\n')
            }
        }
        current?.let { sections[it] = buffer.toString() }
        return sections
    }

    private fun countJsonKeys(bytes: ByteArray, filter: (String) -> Boolean): Int =
        runCatching {
            val root = JSONObject(bytes.decodeToString())
            root.keys().asSequence().count(filter)
        }.getOrDefault(0)

    private fun readZip(zipBytes: ByteArray): Map<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    files[entry.name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        return files
    }

    // ------------------------------------------------------------- prefs (type-tagged JSON)

    private fun defaultPrefs(context: Context): SharedPreferences = SkTheme.prefs(context)

    private fun countPrefs(sp: SharedPreferences, filter: (String) -> Boolean): Int =
        sp.all.entries.count { filter(it.key) && it.value != null }

    /** Serialize matching keys as {"t":"b|i|l|f|s|ss","v":…}; returns (json, keyCount). */
    private fun exportPrefs(sp: SharedPreferences, filter: (String) -> Boolean): Pair<String, Int> {
        val root = JSONObject()
        var count = 0
        for ((key, value) in sp.all) {
            if (!filter(key) || value == null) continue
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("t", "b").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is String -> entry.put("t", "s").put("v", value)
                is Set<*> -> entry.put("t", "ss").put("v", JSONArray(value.map { it.toString() }))
                else -> continue
            }
            root.put(key, entry)
            count++
        }
        return root.toString(2) to count
    }

    /** Merge matching typed keys into [sp] (no clear); returns the number applied. */
    private fun importPrefs(sp: SharedPreferences, json: String, filter: (String) -> Boolean): Int {
        val root = JSONObject(json)
        val editor = sp.edit()
        var count = 0
        for (key in root.keys()) {
            if (!filter(key)) continue
            val entry = root.optJSONObject(key) ?: continue
            when (entry.optString("t")) {
                "b" -> editor.putBoolean(key, entry.getBoolean("v"))
                "i" -> editor.putInt(key, entry.getInt("v"))
                "l" -> editor.putLong(key, entry.getLong("v"))
                "f" -> editor.putFloat(key, entry.getDouble("v").toFloat())
                "s" -> editor.putString(key, entry.getString("v"))
                "ss" -> {
                    val array = entry.getJSONArray("v")
                    editor.putStringSet(key, (0 until array.length()).map { array.getString(it) }.toSet())
                }
                else -> continue
            }
            count++
        }
        editor.apply()
        return count
    }
}
