package com.sentongoharuna.pulse

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.util.Locale

object DevelopUgandaV269StoryDeskStore {
    const val PREFS_NAME = "develop_uganda_v269_smart_story_field_desk"
    val SHOTS = listOf("OPENING", "WIDE", "MEDIUM", "CLOSE-UP", "INTERVIEW", "B-ROLL", "CLOSING")
    val TAGS = listOf("INTERVIEW", "B-ROLL", "ESTABLISHING", "DETAIL", "CROWD", "NIGHT")

    private fun prefs(context: Context) = context.duSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    fun storyDeskEnabled(context: Context) = prefs(context).getBoolean("story_desk_enabled", true)
    fun quickTakeEnabled(context: Context) = prefs(context).getBoolean("quick_take_enabled", true)
    fun shotListEnabled(context: Context) = prefs(context).getBoolean("shot_list_enabled", true)
    fun recentActivityEnabled(context: Context) = prefs(context).getBoolean("recent_activity_enabled", true)
    fun autoCompleteShot(context: Context) = prefs(context).getBoolean("auto_complete_shot", true)
    fun storyTitle(context: Context) = prefs(context).getString("story_title", "") ?: ""
    fun storyLocation(context: Context) = prefs(context).getString("story_location", "") ?: ""
    fun storyReporter(context: Context) = prefs(context).getString("story_reporter", "") ?: ""
    fun storyDeadline(context: Context) = prefs(context).getString("story_deadline", "") ?: ""
    fun clipCount(context: Context) = prefs(context).getInt("clip_count", 0)
    fun bestCount(context: Context) = prefs(context).getInt("best_count", 0)
    fun interviewCount(context: Context) = prefs(context).getInt("interview_count", 0)
    fun totalDurationMs(context: Context) = prefs(context).getLong("total_duration_ms", 0L)
    fun lastRating(context: Context) = prefs(context).getString("last_rating", "UNRATED") ?: "UNRATED"
    fun lastTag(context: Context) = prefs(context).getString("last_tag", "UNTAGGED") ?: "UNTAGGED"
    fun lastClip(context: Context) = prefs(context).getString("last_clip", "—") ?: "—"
    fun lastNote(context: Context) = prefs(context).getString("last_note", "") ?: ""
    fun socialPreset(context: Context) = prefs(context).getString("social_export_preset", "9:16") ?: "9:16"
    fun brandMode(context: Context) = prefs(context).getString("export_brand_mode", "CLEAN MASTER") ?: "CLEAN MASTER"

    fun saveStory(context: Context, title: String, location: String, reporter: String, deadline: String) {
        prefs(context).edit()
            .putString("story_title", title.trim())
            .putString("story_location", location.trim())
            .putString("story_reporter", reporter.trim())
            .putString("story_deadline", deadline.trim())
            .apply()
        addActivity(context, "STORY SAVED • ${title.trim().ifBlank { "UNTITLED" }}")
    }

    fun activeShot(context: Context): String {
        val p = prefs(context)
        val stored = p.getString("active_shot", null)
        if (stored in SHOTS) return stored!!
        return SHOTS.firstOrNull { !isShotDone(context, it) } ?: "OPENING"
    }

    fun selectShot(context: Context, shot: String) {
        if (shot !in SHOTS) return
        prefs(context).edit().putString("active_shot", shot).apply()
        addActivity(context, "NEXT SHOT • $shot")
    }

    fun isShotDone(context: Context, shot: String): Boolean = prefs(context).getBoolean("shot_${shot.replace('-', '_')}_done", false)

    fun setShotDone(context: Context, shot: String, done: Boolean) {
        if (shot !in SHOTS) return
        prefs(context).edit().putBoolean("shot_${shot.replace('-', '_')}_done", done).apply()
        addActivity(context, if (done) "SHOT COMPLETE • $shot" else "SHOT REOPENED • $shot")
        if (done && activeShot(context) == shot) advanceToNextMissing(context)
    }

    private fun advanceToNextMissing(context: Context) {
        val next = SHOTS.firstOrNull { !isShotDone(context, it) } ?: "OPENING"
        prefs(context).edit().putString("active_shot", next).apply()
    }

    fun progress(context: Context): Int {
        if (!shotListEnabled(context)) return 0
        val done = SHOTS.count { isShotDone(context, it) }
        return ((done * 100f) / SHOTS.size).toInt().coerceIn(0, 100)
    }

    fun onClipFinalized(context: Context, clipLabel: String, uri: String, durationMs: Long) {
        if (!storyDeskEnabled(context)) return
        val p = prefs(context)
        val shot = activeShot(context)
        val nextClipCount = p.getInt("clip_count", 0) + 1
        val nextDuration = p.getLong("total_duration_ms", 0L) + durationMs.coerceAtLeast(0L)
        val nextInterviews = p.getInt("interview_count", 0) + if (shot == "INTERVIEW") 1 else 0
        p.edit()
            .putInt("clip_count", nextClipCount)
            .putLong("total_duration_ms", nextDuration)
            .putInt("interview_count", nextInterviews)
            .putString("last_clip", clipLabel)
            .putString("last_clip_uri", uri)
            .putString("last_rating", "UNRATED")
            .putString("last_tag", if (shot == "INTERVIEW") "INTERVIEW" else "UNTAGGED")
            .apply()
        addActivity(context, "CLIP SAFE • $clipLabel • $shot")
        if (shotListEnabled(context) && autoCompleteShot(context)) {
            p.edit().putBoolean("shot_${shot.replace('-', '_')}_done", true).apply()
            addActivity(context, "AUTO COMPLETE • $shot")
            advanceToNextMissing(context)
        }
    }

    fun setLastRating(context: Context, rating: String) {
        val next = rating.uppercase(Locale.US).let { if (it in setOf("BEST", "KEEP", "RETAKE", "REJECT")) it else "UNRATED" }
        val p = prefs(context)
        val previous = p.getString("last_rating", "UNRATED") ?: "UNRATED"
        var best = p.getInt("best_count", 0)
        if (previous == "BEST" && next != "BEST") best = (best - 1).coerceAtLeast(0)
        if (previous != "BEST" && next == "BEST") best += 1
        p.edit().putString("last_rating", next).putInt("best_count", best).apply()
        addActivity(context, "TAKE $next • ${lastClip(context)}")
    }

    fun setLastTag(context: Context, tag: String) {
        val next = tag.uppercase(Locale.US).let { if (it in TAGS) it else "UNTAGGED" }
        prefs(context).edit().putString("last_tag", next).apply()
        addActivity(context, "TAG $next • ${lastClip(context)}")
    }

    fun saveLastNote(context: Context, note: String) {
        prefs(context).edit().putString("last_note", note.trim()).apply()
        addActivity(context, "NOTE SAVED • ${lastClip(context)}")
    }

    fun resetStoryProgress(context: Context) {
        val e = prefs(context).edit()
        SHOTS.forEach { e.remove("shot_${it.replace('-', '_')}_done") }
        e.putString("active_shot", "OPENING")
            .putInt("clip_count", 0)
            .putInt("best_count", 0)
            .putInt("interview_count", 0)
            .putLong("total_duration_ms", 0L)
            .putString("last_clip", "—")
            .putString("last_rating", "UNRATED")
            .putString("last_tag", "UNTAGGED")
            .putString("last_note", "")
            .apply()
        addActivity(context, "STORY PROGRESS RESET")
    }

    fun missingShotLine(context: Context): String {
        if (!shotListEnabled(context)) return "SHOT LIST OFF"
        val missing = SHOTS.filter { !isShotDone(context, it) }
        return if (missing.isEmpty()) "SHOT LIST COMPLETE" else "MISSING • ${missing.take(3).joinToString(" • ")}${if (missing.size > 3) " +${missing.size - 3}" else ""}"
    }

    fun commandStorySummary(context: Context): String {
        val title = storyTitle(context).ifBlank { "UNTITLED STORY" }
        return "$title\nNEXT ${activeShot(context)} • ${progress(context)}%"
    }

    fun afterShootSummary(context: Context): String = "${clipCount(context)} CLIPS • ${bestCount(context)} BEST\n${lastRating(context)} • ${lastTag(context)}"

    fun commandSummary(context: Context): String = "${storyTitle(context).ifBlank { "STORY DESK" }} • ${progress(context)}% • NEXT ${activeShot(context)} • ${clipCount(context)} CLIPS"

    fun statusSummary(context: Context): String = commandStorySummary(context) + "\n" + afterShootSummary(context) + "\n" + missingShotLine(context) + "\nEXPORT ${socialPreset(context)} • ${brandMode(context)}"

    private fun addActivity(context: Context, item: String) {
        val p = prefs(context)
        val old = try { JSONArray(p.getString("recent_json", "[]")) } catch (_: Exception) { JSONArray() }
        val next = JSONArray().put(item)
        for (i in 0 until minOf(old.length(), 7)) next.put(old.optString(i))
        p.edit().putString("recent_json", next.toString()).apply()
    }

    fun latestActivity(context: Context): String {
        if (!recentActivityEnabled(context)) return "RECENT ACTIVITY OFF"
        val a = try { JSONArray(prefs(context).getString("recent_json", "[]")) } catch (_: Exception) { JSONArray() }
        return a.optString(0, "NO STORY ACTIONS YET")
    }

    fun recentActivity(context: Context): List<String> {
        if (!recentActivityEnabled(context)) return emptyList()
        val a = try { JSONArray(prefs(context).getString("recent_json", "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until minOf(a.length(), 8)).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
    }

    fun formattedDuration(context: Context): String {
        val sec = totalDurationMs(context) / 1000L
        return String.format(Locale.US, "%02d:%02d:%02d", sec / 3600L, (sec / 60L) % 60L, sec % 60L)
    }
}

object DevelopUgandaV269QuickTakeDialog {
    private val ink = DevelopUgandaFivemods8Theme.surface
    private val card = DevelopUgandaFivemods8Theme.surfaceRaised
    private val white = DevelopUgandaFivemods8Theme.content
    private val muted = DevelopUgandaFivemods8Theme.contentDim
    private val gold = DevelopUgandaFivemods8Theme.accent
    private val cyan = DevelopUgandaFivemods8Theme.content
    private val green = DevelopUgandaFivemods8Theme.accent
    private val red = DevelopUgandaFivemods8Theme.record

    fun show(activity: Activity, clipLabel: String) {
        if (!DevelopUgandaV269StoryDeskStore.quickTakeEnabled(activity)) return
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 10)); background = rounded(ink, gold, 18) }
        box.addView(label(activity, "V272 • QUICK TAKE", 13f, white, true))
        box.addView(label(activity, "$clipLabel • NEXT ${DevelopUgandaV269StoryDeskStore.activeShot(activity)}", 8.2f, muted, false).apply { setPadding(0, dp(activity, 4), 0, dp(activity, 8)) })
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("BEST" to gold, "KEEP" to green, "RETAKE" to cyan, "REJECT" to red).forEachIndexed { i, pair ->
            row.addView(button(activity, pair.first, pair.second) { DevelopUgandaV269StoryDeskStore.setLastRating(activity, pair.first); Toast.makeText(activity, "TAKE ${pair.first}", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(0, dp(activity, 42), 1f).apply { if (i>0) leftMargin=dp(activity,2); if (i<3) rightMargin=dp(activity,2) })
        }
        box.addView(row)
        val note = EditText(activity).apply { hint = "Quick note for this take"; setHintTextColor(muted); setTextColor(white); textSize = 10f; setSingleLine(true); background = rounded(card, cyan, 12); setPadding(dp(activity,10),0,dp(activity,10),0) }
        box.addView(note, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity,44)).apply { topMargin=dp(activity,8) })
        val actions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button(activity, "SAVE NOTE", cyan) { DevelopUgandaV269StoryDeskStore.saveLastNote(activity, note.text.toString()); Toast.makeText(activity,"NOTE SAVED",Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(0, dp(activity,40),1f).apply { rightMargin=dp(activity,3) })
        actions.addView(button(activity, "OPEN STORY DESK", gold) { DevelopUgandaV270Guidance.safeOpen(activity, DevelopUgandaV269SmartStoryDeskActivity::class.java) }, LinearLayout.LayoutParams(0, dp(activity,40),1f).apply { leftMargin=dp(activity,3) })
        box.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin=dp(activity,6) })
        AlertDialog.Builder(activity).setView(box).setNegativeButton("DONE", null).show()
    }

    private fun button(c: Context, text: String, accent: Int, action: () -> Unit) = Button(c).apply { this.text=text; textSize=8f; setTextColor(white); typeface=Typeface.DEFAULT_BOLD; isAllCaps=false; background=rounded(card,accent,12); setOnClickListener { view -> performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); DevelopUgandaV28012SafeActions.run(view.context, "SMART STORY BUTTON") { action() } } }
    private fun label(c: Context, text: String, size: Float, color: Int, bold: Boolean)=TextView(c).apply{this.text=text;textSize=size;setTextColor(color);if(bold)typeface=Typeface.DEFAULT_BOLD}
    private fun rounded(fill:Int, stroke:Int, radius:Int)=GradientDrawable().apply{setColor(fill);cornerRadius=radius.toFloat();setStroke(1,stroke)}
    private fun dp(c: Context, v:Int)=(v*c.resources.displayMetrics.density).toInt()
}

class DevelopUgandaV269SmartStoryDeskActivity : AppCompatActivity() {
    private val ink = DevelopUgandaFivemods8Theme.surface; private val panel=DevelopUgandaFivemods8Theme.surface; private val card=DevelopUgandaFivemods8Theme.surfaceRaised; private val line=DevelopUgandaFivemods8Theme.outline; private val gold=DevelopUgandaFivemods8Theme.accent; private val cyan=DevelopUgandaFivemods8Theme.content; private val green=DevelopUgandaFivemods8Theme.accent; private val violet=DevelopUgandaFivemods8Theme.contentDim; private val white=DevelopUgandaFivemods8Theme.content; private val muted=DevelopUgandaFivemods8Theme.contentDim; private val red=DevelopUgandaFivemods8Theme.record
    private lateinit var host: LinearLayout
    private lateinit var titleInput: EditText; private lateinit var locationInput: EditText; private lateinit var reporterInput: EditText; private lateinit var deadlineInput: EditText; private lateinit var noteInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); buildUi() }
    override fun onResume() { super.onResume(); refresh() }

    private fun buildUi() {
        val scroll=ScrollView(this).apply{setBackgroundColor(ink);isFillViewport=true}
        host=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(14),dp(12),dp(28));setBackgroundColor(ink)}
        scroll.addView(host); setContentView(scroll); refresh()
    }

    private fun refresh() {
        host.removeAllViews()

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(text("develop.uganda • V273 PRO CAM",20f,white,true), LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        top.addView(action("ⓘ GUIDE",gold) {
            DevelopUgandaV270Guidance.showFeature(
                this,
                "Smart Story / Field Desk",
                "Organizes the story from planning through shooting, take review and delivery without changing the clean master.",
                "Use this page in order from section 1 to section 5. Return to it after each important take."
            )
        }, LinearLayout.LayoutParams(dp(82),dp(40)))
        host.addView(top)
        host.addView(text("SMART STORY + FIELD DESK • GUIDED ORDER",11f,gold,true).apply{setPadding(0,dp(2),0,dp(6))})
        host.addView(text("Follow the numbered sections. Small instructions tell you what each step achieves. Nothing on this page burns into the recorded video.",8.5f,muted,false))

        section("1 • DEFINE THE STORY")
        val story=panelBlock()
        story.addView(text("WHAT TO DO • Enter the story identity before the first clip, then SAVE STORY. This keeps later clips, notes and delivery decisions understandable.",7.8f,green,false).apply{setPadding(0,0,0,dp(6))})
        titleInput=input("Story title",DevelopUgandaV269StoryDeskStore.storyTitle(this))
        locationInput=input("Location",DevelopUgandaV269StoryDeskStore.storyLocation(this))
        reporterInput=input("Reporter",DevelopUgandaV269StoryDeskStore.storyReporter(this))
        deadlineInput=input("Deadline / delivery note",DevelopUgandaV269StoryDeskStore.storyDeadline(this))
        story.addView(titleInput)
        story.addView(locationInput, fieldLp())
        story.addView(reporterInput, fieldLp())
        story.addView(deadlineInput, fieldLp())
        story.addView(action("SAVE STORY",gold){
            DevelopUgandaV269StoryDeskStore.saveStory(this,titleInput.text.toString(),locationInput.text.toString(),reporterInput.text.toString(),deadlineInput.text.toString())
            toast("STORY SAVED")
            refresh()
        }, fullButtonLp())
        host.addView(story)

        section("2 • PLAN + SHOOT THE REQUIRED SHOTS")
        val progress=panelBlock()
        progress.addView(text("HOW TO USE • Tap SET to make a shot NEXT. Record it in Field Camera. ✓ manually marks a shot complete; automatic completion can also be enabled in Pro Settings.",7.8f,green,false))
        progress.addView(text(DevelopUgandaV269StoryDeskStore.statusSummary(this),10f,white,true).apply{setPadding(0,dp(7),0,0)})
        progress.addView(text(DevelopUgandaV269StoryDeskStore.missingShotLine(this),8.3f,gold,true).apply{setPadding(0,dp(7),0,0)})
        progress.addView(text("RECORDED ${DevelopUgandaV269StoryDeskStore.formattedDuration(this)} • ${DevelopUgandaV269StoryDeskStore.interviewCount(this)} INTERVIEW CLIPS",7.8f,muted,true).apply{setPadding(0,dp(5),0,0)})
        progress.addView(action("OPEN FIELD CAMERA • SHOOT ${DevelopUgandaV269StoryDeskStore.activeShot(this)}",cyan){
            DevelopUgandaV270Guidance.safeOpen(this,DevelopUgandaAllProCameraActivity::class.java)
        }, fullButtonLp())
        host.addView(progress)

        if (DevelopUgandaV269StoryDeskStore.shotListEnabled(this)) {
            section("SHOT CHECKLIST • SET NEXT / ✓ COMPLETE")
            DevelopUgandaV269StoryDeskStore.SHOTS.forEach { shot -> host.addView(shotRow(shot)) }
        }

        section("3 • REVIEW + RATE THE LAST TAKE")
        val last=panelBlock()
        val hasTake = DevelopUgandaV269StoryDeskStore.clipCount(this) > 0 && DevelopUgandaV269StoryDeskStore.lastClip(this) != "—"
        last.addView(text(
            if (hasTake) "HOW TO USE • Rate the latest safe clip, add a useful tag, then save a short note before moving to the next shot."
            else "NO TAKE YET • Record a clip first. Rating/tag/note controls stay disabled so they cannot claim to rate a clip that does not exist.",
            7.8f,
            if (hasTake) green else gold,
            false
        ))
        last.addView(text("${DevelopUgandaV269StoryDeskStore.lastClip(this)}\n${DevelopUgandaV269StoryDeskStore.lastRating(this)} • ${DevelopUgandaV269StoryDeskStore.lastTag(this)}",9.5f,white,true).apply{setPadding(0,dp(7),0,0)})
        val rate=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        listOf("BEST" to gold,"KEEP" to green,"RETAKE" to cyan,"REJECT" to red).forEachIndexed{i,pair->
            val b=action(pair.first,pair.second){DevelopUgandaV269StoryDeskStore.setLastRating(this,pair.first);refresh()}
            setActionEnabled(b,hasTake)
            rate.addView(b,LinearLayout.LayoutParams(0,dp(42),1f).apply{if(i>0)leftMargin=dp(2);if(i<3)rightMargin=dp(2)})
        }
        last.addView(rate, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{topMargin=dp(8)})
        last.addView(text("CLIP TAG",7.5f,muted,true).apply{setPadding(0,dp(9),0,dp(4))})
        val tags=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        DevelopUgandaV269StoryDeskStore.TAGS.take(3).forEachIndexed{i,tag->
            val b=action(tag,if(tag==DevelopUgandaV269StoryDeskStore.lastTag(this))gold else cyan){DevelopUgandaV269StoryDeskStore.setLastTag(this,tag);refresh()}
            setActionEnabled(b,hasTake)
            tags.addView(b,LinearLayout.LayoutParams(0,dp(40),1f).apply{if(i>0)leftMargin=dp(2);if(i<2)rightMargin=dp(2)})
        }
        last.addView(tags)
        val tags2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        DevelopUgandaV269StoryDeskStore.TAGS.drop(3).forEachIndexed{i,tag->
            val b=action(tag,if(tag==DevelopUgandaV269StoryDeskStore.lastTag(this))gold else violet){DevelopUgandaV269StoryDeskStore.setLastTag(this,tag);refresh()}
            setActionEnabled(b,hasTake)
            tags2.addView(b,LinearLayout.LayoutParams(0,dp(40),1f).apply{if(i>0)leftMargin=dp(2);if(i<2)rightMargin=dp(2)})
        }
        last.addView(tags2,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{topMargin=dp(4)})
        noteInput=input("Take note",DevelopUgandaV269StoryDeskStore.lastNote(this))
        noteInput.isEnabled=hasTake
        noteInput.alpha=if(hasTake)1f else 0.45f
        last.addView(noteInput,fieldLp())
        val saveNote=action("SAVE TAKE NOTE",cyan){DevelopUgandaV269StoryDeskStore.saveLastNote(this,noteInput.text.toString());toast("NOTE SAVED");refresh()}
        setActionEnabled(saveNote,hasTake)
        last.addView(saveNote,fullButtonLp())
        host.addView(last)

        section("4 • REVIEW THE PACKAGE")
        val review=panelBlock()
        review.addView(text("WHAT YOU ACHIEVE • Check clip health, BEST/KEEP decisions, proxy/multi-camera material and packaged metadata before final editing or sharing.",7.8f,green,false))
        val rr=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        rr.addView(action("STORY PACKAGES",green){DevelopUgandaV270Guidance.safeOpen(this,DevelopUgandaStoryPackagesActivity::class.java)},LinearLayout.LayoutParams(0,dp(46),1f).apply{rightMargin=dp(3)})
        rr.addView(action("PROXY / SYNC REVIEW",cyan){DevelopUgandaV270Guidance.safeOpen(this,DevelopUgandaV265ProxySyncReviewActivity::class.java)},LinearLayout.LayoutParams(0,dp(46),1f).apply{leftMargin=dp(3)})
        review.addView(rr,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{topMargin=dp(7)})
        host.addView(review)

        section("5 • DELIVERY INTENT")
        val delivery=panelBlock()
        delivery.addView(text("SOCIAL ${DevelopUgandaV269StoryDeskStore.socialPreset(this)} • ${DevelopUgandaV269StoryDeskStore.brandMode(this)}",9f,white,true))
        delivery.addView(text("These choices describe the intended delivery. They do not crop, watermark or alter the clean camera master by themselves.",7.8f,muted,false).apply{setPadding(0,dp(4),0,0)})
        val dr=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        dr.addView(action("COLOR STUDIO",violet){DevelopUgandaV270Guidance.safeOpen(this,DevelopUgandaColorStudioActivity::class.java)},LinearLayout.LayoutParams(0,dp(44),1f).apply{rightMargin=dp(3)})
        dr.addView(action("EDIT VIDEO",cyan){DevelopUgandaV270Guidance.safeOpen(this,DevelopUgandaEditorActivity::class.java)},LinearLayout.LayoutParams(0,dp(44),1f).apply{leftMargin=dp(3)})
        delivery.addView(dr,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{topMargin=dp(7)})
        host.addView(delivery)

        section("RECENT ACTIVITY")
        val recent=panelBlock()
        val items=DevelopUgandaV269StoryDeskStore.recentActivity(this)
        recent.addView(text(if(items.isEmpty())"NO STORY ACTIONS YET" else items.joinToString("\n") { "• $it" },8.2f,if(items.isEmpty())muted else white,false))
        host.addView(recent)

        section("FINAL ACTIONS")
        val a2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        a2.addView(action("SHARE STORY SUMMARY",gold){shareSummary()},LinearLayout.LayoutParams(0,dp(46),1f).apply{rightMargin=dp(3)})
        a2.addView(action("RESET PROGRESS",red){confirmReset()},LinearLayout.LayoutParams(0,dp(46),1f).apply{leftMargin=dp(3)})
        host.addView(a2)
    }

    private fun shotRow(shot:String):View {
        val active=DevelopUgandaV269StoryDeskStore.activeShot(this)==shot; val done=DevelopUgandaV269StoryDeskStore.isShotDone(this,shot)
        return LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),dp(8),dp(8),dp(8));background=rounded(card,if(active)gold else if(done)green else line,14,1)
            addView(text(shot,9f,if(active)gold else white,true),LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
            addView(text(if(done)"DONE" else if(active)"NEXT" else "WAIT",7.7f,if(done)green else if(active)gold else muted,true).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(54),dp(36)))
            addView(action(if(done)"REOPEN" else "SET",if(done)cyan else gold){if(done)DevelopUgandaV269StoryDeskStore.setShotDone(this@DevelopUgandaV269SmartStoryDeskActivity,shot,false) else DevelopUgandaV269StoryDeskStore.selectShot(this@DevelopUgandaV269SmartStoryDeskActivity,shot);refresh()},LinearLayout.LayoutParams(dp(68),dp(36)).apply{leftMargin=dp(4)})
            if(!done)addView(action("✓",green){DevelopUgandaV269StoryDeskStore.setShotDone(this@DevelopUgandaV269SmartStoryDeskActivity,shot,true);refresh()},LinearLayout.LayoutParams(dp(42),dp(36)).apply{leftMargin=dp(4)})
        }.also { it.layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{topMargin=dp(5)} }
    }

    private fun setActionEnabled(button: Button, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.38f
    }

    private fun shareSummary(){ val text="${DevelopUgandaV269StoryDeskStore.storyTitle(this).ifBlank{"develop.uganda story"}}\n${DevelopUgandaV269StoryDeskStore.statusSummary(this)}\n${DevelopUgandaV269StoryDeskStore.storyLocation(this)}"; startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,text)},"Share story summary")) }
    private fun confirmReset(){AlertDialog.Builder(this).setTitle("Reset story progress?").setMessage("Story title stays, but shot completion, clip counters, rating and notes reset.").setPositiveButton("RESET"){_,_->DevelopUgandaV269StoryDeskStore.resetStoryProgress(this);refresh()}.setNegativeButton("CANCEL",null).show()}
    private fun section(title:String){host.addView(text(title,9f,gold,true).apply{setPadding(dp(2),dp(14),0,dp(6))})}
    private fun panelBlock()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(11),dp(10),dp(11),dp(10));background=rounded(panel,line,18,1)}
    private fun text(value:String,size:Float,color:Int,bold:Boolean)=TextView(this).apply{text=value;textSize=size;setTextColor(color);if(bold)typeface=Typeface.DEFAULT_BOLD}
    private fun input(hintText:String,value:String)=EditText(this).apply{hint=hintText;setHintTextColor(muted);setTextColor(white);textSize=9.5f;setText(value);setSingleLine(true);background=rounded(card,line,12,1);setPadding(dp(10),0,dp(10),0)}
    private fun action(label:String,accent:Int,fn:()->Unit)=Button(this).apply{text=label;textSize=8f;setTextColor(white);typeface=Typeface.DEFAULT_BOLD;isAllCaps=false;gravity=Gravity.CENTER;background=rounded(card,accent,12,1);setOnClickListener{view->performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);DevelopUgandaV28012SafeActions.run(view.context,"STORY DESK ACTION"){fn()}}}
    private fun rounded(fill:Int,stroke:Int,radius:Int,width:Int)=GradientDrawable().apply{setColor(fill);cornerRadius=dp(radius).toFloat();setStroke(dp(width).coerceAtLeast(1),stroke)}
    private fun fieldLp()=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(44)).apply{topMargin=dp(6)}
    private fun fullButtonLp()=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(44)).apply{topMargin=dp(7)}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun toast(v:String)=Toast.makeText(this,v,Toast.LENGTH_SHORT).show()
}
